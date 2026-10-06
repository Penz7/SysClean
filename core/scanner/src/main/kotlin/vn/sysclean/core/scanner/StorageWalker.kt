package vn.sysclean.core.scanner

import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.JunkItem
import vn.sysclean.core.model.JunkNote
import vn.sysclean.core.model.ScanProgress
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class ApkMeta(val packageName: String, val versionCode: Long)

internal class WalkResult(
    val categories: MutableMap<JunkCategory, TopItems>,
    val filesScanned: Long,
    val bytesScanned: Long,
)

/**
 * Walks shared storage once and sorts every file into junk categories. Pure java.io so it
 * can be unit-tested on a temp directory; Android specifics are injected.
 *
 * Detection only: nothing here deletes anything.
 */
internal class StorageWalker(
    private val root: File,
    /** packageName -> installed versionCode */
    private val installedPackages: Map<String, Long>,
    private val inspectApk: (File) -> ApkMeta?,
    private val now: Long = System.currentTimeMillis(),
    private val isActive: () -> Boolean = { true },
    /** Whitelisted paths and our own recycle bin. */
    private val isIgnored: (String) -> Boolean = { false },
    private val onProgress: (ScanProgress) -> Unit = {},
) {
    private val results = mutableMapOf<JunkCategory, TopItems>()
    private val emptyDirs = mutableListOf<File>()
    private val duplicateCandidates = mutableMapOf<Long, MutableList<File>>()
    private var filesScanned = 0L
    private var bytesScanned = 0L
    private var lastProgressAt = 0L

    private val rootPath = root.absolutePath
    private val skippedDirs = setOf("$rootPath/Android/data", "$rootPath/Android/obb")
    private val downloadDir = "$rootPath/Download"
    private val protectedDirs: Set<String> =
        (STANDARD_DIRS.map { "$rootPath/$it" } + rootPath + "$rootPath/Android" + "$rootPath/Android/media").toSet()

    fun walk(): WalkResult {
        visitDirectory(root)
        reportEmptyDirectories()
        reportLeftovers()
        reportDuplicates()
        return WalkResult(results, filesScanned, bytesScanned)
    }

    /** Returns true when [dir] holds nothing but (recursively) empty directories. */
    private fun visitDirectory(dir: File): Boolean {
        checkActive()
        val children = dir.listFiles() ?: return false
        var empty = true
        for (child in children) {
            if (Files.isSymbolicLink(child.toPath()) || isIgnored(child.absolutePath)) {
                empty = false
                continue
            }
            if (child.isDirectory) {
                when {
                    child.absolutePath in skippedDirs -> empty = false
                    child.name == ".thumbnails" -> {
                        empty = false
                        val size = measureDirectory(child)
                        if (size > 0) add(JunkCategory.THUMBNAILS, child, size)
                    }
                    !visitDirectory(child) -> empty = false
                }
            } else {
                empty = false
                visitFile(child)
            }
        }
        if (empty && dir.absolutePath !in protectedDirs) emptyDirs += dir
        return empty
    }

    private fun visitFile(file: File) {
        val size = file.length()
        countScanned(file, size)
        val name = file.name
        val extension = name.substringAfterLast('.', "").lowercase()
        val lastModified = file.lastModified()

        when {
            extension in APK_EXTENSIONS -> add(JunkCategory.APK_FILES, file, size, note = apkNote(file, extension))
            extension in TEMP_EXTENSIONS || name.lowercase() in TEMP_NAMES || name.startsWith("~$") ->
                add(JunkCategory.TEMP_FILES, file, size)
            file.parentFile?.name.equals("Screenshots", ignoreCase = true) ->
                add(JunkCategory.SCREENSHOTS, file, size)
            file.absolutePath.startsWith("$downloadDir/") && now - lastModified > OLD_DOWNLOAD_AGE ->
                add(JunkCategory.OLD_DOWNLOADS, file, size)
            size >= LARGE_FILE_BYTES -> add(JunkCategory.LARGE_FILES, file, size)
        }
        if (size >= DUPLICATE_MIN_BYTES) duplicateCandidates.getOrPut(size) { mutableListOf() } += file
    }

    private fun apkNote(file: File, extension: String): JunkNote {
        // Split bundles (.apks/.xapk) are zips of APKs; the package manager cannot parse them directly.
        if (extension != "apk") return JunkNote.APK_NOT_INSTALLED
        val meta = inspectApk(file) ?: return JunkNote.APK_INVALID
        val installed = installedPackages[meta.packageName] ?: return JunkNote.APK_NOT_INSTALLED
        return if (meta.versionCode <= installed) JunkNote.APK_INSTALLED else JunkNote.APK_NEWER_THAN_INSTALLED
    }

    /** Only the outermost of nested empty directories is reported; deleting it removes the rest. */
    private fun reportEmptyDirectories() {
        val sorted = emptyDirs.map { it.absolutePath }.sorted()
        var lastTop: String? = null
        for (path in sorted) {
            if (lastTop != null && path.startsWith("$lastTop/")) continue
            lastTop = path
            add(JunkCategory.EMPTY_FOLDERS, File(path), 0)
        }
    }

    /** Folders in Android/media named after packages that are no longer installed. */
    private fun reportLeftovers() {
        val media = File(root, "Android/media").listFiles() ?: return
        media.filter { it.isDirectory && '.' in it.name && it.name !in installedPackages && !isIgnored(it.absolutePath) }
            .forEach { add(JunkCategory.LEFTOVER_FOLDERS, it, sizeOf(it)) }
    }

    /**
     * Files with equal size are fingerprinted by head + tail. This is an estimate that never
     * misses a duplicate; deleting will re-verify with a full hash.
     */
    private fun reportDuplicates() {
        var groupIndex = 0
        for ((size, files) in duplicateCandidates) {
            if (files.size < 2) continue
            checkActive()
            files.groupBy { runCatching { fingerprint(it, size) }.getOrNull() }
                .filterKeys { it != null }
                .values
                .filter { it.size > 1 }
                .forEach { group ->
                    val id = "dup-${groupIndex++}"
                    val sorted = group.sortedBy { it.lastModified() }
                    // The oldest copy is treated as the original; every other copy is reclaimable.
                    sorted.forEachIndexed { index, file ->
                        val reclaimable = index > 0
                        results.getOrPut(JunkCategory.DUPLICATE_FILES) { TopItems(MAX_ITEMS) }.add(
                            item = file.toItem(size, groupId = id).copy(isOriginal = !reclaimable),
                            countsAs = if (reclaimable) 1 else 0,
                            bytes = if (reclaimable) size else 0,
                        )
                    }
                }
        }
    }

    private fun fingerprint(file: File, size: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(file, "r").use { raf ->
            val buffer = ByteArray(FINGERPRINT_CHUNK)
            val head = raf.read(buffer)
            if (head > 0) digest.update(buffer, 0, head)
            if (size > FINGERPRINT_CHUNK * 2) {
                raf.seek(size - FINGERPRINT_CHUNK)
                val tail = raf.read(buffer)
                if (tail > 0) digest.update(buffer, 0, tail)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun measureDirectory(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { file ->
            checkActive()
            file.length().also { countScanned(file, it) }
        }

    private fun sizeOf(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun add(category: JunkCategory, file: File, size: Long, note: JunkNote? = null) {
        results.getOrPut(category) { TopItems(MAX_ITEMS) }.add(file.toItem(size, note = note))
    }

    private fun File.toItem(size: Long, note: JunkNote? = null, groupId: String? = null) = JunkItem(
        path = absolutePath,
        title = name,
        sizeBytes = size,
        lastModified = lastModified(),
        note = note,
        groupId = groupId,
    )

    private fun countScanned(file: File, size: Long) {
        filesScanned++
        bytesScanned += size
        val time = System.nanoTime()
        if (time - lastProgressAt > PROGRESS_INTERVAL_NANOS) {
            lastProgressAt = time
            onProgress(ScanProgress(file.absolutePath.removePrefix(rootPath), filesScanned, bytesScanned))
        }
    }

    private fun checkActive() {
        if (!isActive()) throw WalkCancelledException()
    }

    companion object {
        // High enough that "clean all" really means all; JunkItem is tiny.
        const val MAX_ITEMS = 5000
        const val LARGE_FILE_BYTES = 100L * 1000 * 1000
        const val DUPLICATE_MIN_BYTES = 1000L * 1000
        val OLD_DOWNLOAD_AGE = TimeUnit.DAYS.toMillis(90)
        private const val FINGERPRINT_CHUNK = 64 * 1024
        private val PROGRESS_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(150)

        private val APK_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm")
        private val TEMP_EXTENSIONS = setOf("tmp", "temp", "log", "bak", "old", "dmp", "crdownload", "part")
        private val TEMP_NAMES = setOf("thumbs.db", ".ds_store", "desktop.ini")
        private val STANDARD_DIRS = listOf(
            "Alarms", "Audiobooks", "DCIM", "Documents", "Download", "Movies", "Music",
            "Notifications", "Pictures", "Podcasts", "Recordings", "Ringtones",
        )
    }
}

internal class WalkCancelledException : RuntimeException()
