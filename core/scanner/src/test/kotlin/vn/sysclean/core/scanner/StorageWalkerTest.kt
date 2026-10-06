package vn.sysclean.core.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.JunkNote
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

class StorageWalkerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = TimeUnit.DAYS.toMillis(1000)

    private fun file(path: String, bytes: Int = 10, fill: Byte = 1, modified: Long = now): File =
        File(tmp.root, path).apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(bytes) { fill })
            setLastModified(modified)
        }

    private fun walk(
        installed: Map<String, Long> = emptyMap(),
        apks: Map<String, ApkMeta> = emptyMap(),
    ) = StorageWalker(
        root = tmp.root,
        installedPackages = installed,
        inspectApk = { apks[it.name] },
        now = now,
    ).walk()

    @Test
    fun classifiesApksAgainstInstalledVersions() {
        file("Download/old.apk")
        file("Download/new.apk")
        file("Download/other.apk")
        file("Download/broken.apk")
        val result = walk(
            installed = mapOf("a.installed" to 10),
            apks = mapOf(
                "old.apk" to ApkMeta("a.installed", 9),
                "new.apk" to ApkMeta("a.installed", 11),
                "other.apk" to ApkMeta("b.missing", 1),
            ),
        )
        val notes = result.categories.getValue(JunkCategory.APK_FILES).items().associate { it.title to it.note }
        assertEquals(JunkNote.APK_INSTALLED, notes["old.apk"])
        assertEquals(JunkNote.APK_NEWER_THAN_INSTALLED, notes["new.apk"])
        assertEquals(JunkNote.APK_NOT_INSTALLED, notes["other.apk"])
        assertEquals(JunkNote.APK_INVALID, notes["broken.apk"])
    }

    @Test
    fun detectsTempScreenshotsAndOldDownloads() {
        file("Documents/report.tmp", bytes = 5)
        file("Documents/~\$draft.docx", bytes = 5)
        file("Pictures/Screenshots/s1.png", bytes = 7)
        file("Download/ancient.pdf", bytes = 3, modified = now - TimeUnit.DAYS.toMillis(200))
        file("Download/recent.pdf", bytes = 3, modified = now - TimeUnit.DAYS.toMillis(10))

        val result = walk()
        assertEquals(2, result.categories.getValue(JunkCategory.TEMP_FILES).count)
        assertEquals(7, result.categories.getValue(JunkCategory.SCREENSHOTS).totalBytes)
        val old = result.categories.getValue(JunkCategory.OLD_DOWNLOADS).items().map { it.title }
        assertEquals(listOf("ancient.pdf"), old)
    }

    @Test
    fun reportsOnlyOutermostEmptyFolderAndNeverStandardOnes() {
        File(tmp.root, "Music").mkdirs()
        File(tmp.root, "junk/a/b").mkdirs()
        File(tmp.root, "keep/inner").mkdirs()
        file("keep/file.txt")

        val empty = walk().categories.getValue(JunkCategory.EMPTY_FOLDERS).items().map { it.path }
        assertEquals(
            listOf(File(tmp.root, "junk").path, File(tmp.root, "keep/inner").path).sorted(),
            empty.sorted(),
        )
    }

    @Test
    fun sumsThumbnailFoldersAndSkipsPrivateAppDirs() {
        file("DCIM/.thumbnails/1.jpg", bytes = 100)
        file("DCIM/.thumbnails/2.jpg", bytes = 50)
        file("Android/data/com.app/cache.tmp", bytes = 999)

        val result = walk()
        assertEquals(150, result.categories.getValue(JunkCategory.THUMBNAILS).totalBytes)
        assertNull(result.categories[JunkCategory.TEMP_FILES])
    }

    @Test
    fun flagsLeftoverMediaFoldersOfUninstalledApps() {
        file("Android/media/com.gone.app/x.bin", bytes = 40)
        file("Android/media/com.still.here/y.bin", bytes = 40)

        val leftovers = walk(installed = mapOf("com.still.here" to 1))
            .categories.getValue(JunkCategory.LEFTOVER_FOLDERS)
        assertEquals(1, leftovers.count)
        assertEquals(40, leftovers.totalBytes)
        assertTrue(leftovers.items().single().path.endsWith("com.gone.app"))
    }

    @Test
    fun countsOnlyRedundantDuplicateCopies() {
        val size = StorageWalker.DUPLICATE_MIN_BYTES.toInt()
        file("Movies/a.mp4", bytes = size, fill = 7, modified = now - 2000)
        file("Download/a (1).mp4", bytes = size, fill = 7, modified = now - 1000)
        file("Download/a (2).mp4", bytes = size, fill = 7)
        file("Download/same-size-different.mp4", bytes = size, fill = 9)

        val duplicates = walk().categories.getValue(JunkCategory.DUPLICATE_FILES)
        assertEquals(2, duplicates.count)
        assertEquals(2L * size, duplicates.totalBytes)
        assertFalse(duplicates.items().any { it.title == "same-size-different.mp4" })
    }

    @Test
    fun largeFilesExcludeFilesAlreadyInAnotherCategory() {
        sparse("Movies/film.mkv", StorageWalker.LARGE_FILE_BYTES)
        sparse("Download/game.apk", StorageWalker.LARGE_FILE_BYTES)

        val result = walk()
        assertEquals(listOf("film.mkv"), result.categories.getValue(JunkCategory.LARGE_FILES).items().map { it.title })
        assertEquals(1, result.categories.getValue(JunkCategory.APK_FILES).count)
        assertEquals(2, result.filesScanned)
    }

    /** Sparse files let us test the 100 MB threshold without writing 100 MB. */
    private fun sparse(path: String, length: Long) {
        val target = File(tmp.root, path).apply { parentFile!!.mkdirs() }
        RandomAccessFile(target, "rw").use { it.setLength(length) }
        target.setLastModified(now)
    }
}
