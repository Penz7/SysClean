package vn.sysclean.core.data.source

import java.io.File

/**
 * File-system half of the recycle bin. Moving is a rename inside the same volume, so even
 * gigabyte-sized videos go to the bin instantly and come back byte-for-byte identical.
 */
internal class TrashStore(val root: File) {

    fun prepare() {
        root.mkdirs()
        // Keeps trashed photos and videos out of the gallery.
        File(root, ".nomedia").takeUnless { it.exists() }?.createNewFile()
    }

    fun locationFor(id: String): File = File(root, id)

    fun moveIn(source: File, id: String): File? {
        prepare()
        val target = locationFor(id)
        return target.takeIf { source.exists() && source.renameTo(it) }
    }

    /**
     * Puts [trashed] back at [originalPath]. If something new now occupies that path, the
     * item is restored next to it as "name (1).ext" rather than overwriting the user's file.
     */
    fun restore(trashed: File, originalPath: String): File? {
        if (!trashed.exists()) return null
        val target = freePath(File(originalPath))
        target.parentFile?.mkdirs()
        return target.takeIf { trashed.renameTo(it) }
    }

    fun delete(trashed: File): Boolean = !trashed.exists() || trashed.deleteRecursively()

    /** Entries on disk that the database does not know about, e.g. after "Clear data". */
    fun orphans(knownIds: Set<String>, olderThan: Long): List<File> =
        root.listFiles().orEmpty().filter {
            it.name != ".nomedia" && it.name !in knownIds && it.lastModified() < olderThan
        }

    private fun freePath(wanted: File): File {
        if (!wanted.exists()) return wanted
        val parent = wanted.parentFile
        val base = wanted.nameWithoutExtension
        val extension = wanted.extension.takeIf { it.isNotEmpty() && wanted.isFile }?.let { ".$it" } ?: ""
        return generateSequence(1) { it + 1 }
            .map { File(parent, "$base ($it)$extension") }
            .first { !it.exists() }
    }
}

internal fun File.sizeRecursive(): Long =
    if (isFile) length() else walkTopDown().filter { it.isFile }.sumOf { it.length() }
