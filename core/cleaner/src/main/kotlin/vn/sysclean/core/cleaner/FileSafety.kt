package vn.sysclean.core.cleaner

import vn.sysclean.core.model.JunkItem
import java.io.File
import java.security.MessageDigest

/** Pure java.io checks that run right before anything is removed. */
internal object FileSafety {

    /** Deletes [dir] only if it still contains nothing but empty folders. */
    fun deleteIfEmpty(dir: File): Boolean {
        if (!dir.isDirectory) return false
        if (dir.walkTopDown().any { it.isFile }) return false
        return dir.walkBottomUp().all { it.delete() }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    data class DuplicatePlan(val removable: List<JunkItem>, val unverified: List<JunkItem>)

    /**
     * The scan only fingerprints head + tail. Before removing a copy we hash every byte and
     * compare with a copy that stays, and we always leave at least one copy of each set.
     */
    fun planDuplicates(selected: List<JunkItem>, all: List<JunkItem>): DuplicatePlan {
        val selectedPaths = selected.map { it.path }.toSet()
        val removable = mutableListOf<JunkItem>()
        val unverified = mutableListOf<JunkItem>()
        for (members in all.filter { it.groupId != null }.groupBy { it.groupId }.values) {
            val chosen = members.filter { it.path in selectedPaths }
            if (chosen.isEmpty()) continue
            var kept = members.filter { it.path !in selectedPaths }
            var toRemove = chosen
            if (kept.isEmpty()) {
                // Everything was selected: keep the original anyway.
                val keeper = chosen.firstOrNull { it.isOriginal } ?: chosen.first()
                kept = listOf(keeper)
                toRemove = chosen - keeper
            }
            val reference = runCatching { sha256(File(kept.first().path)) }.getOrNull()
            toRemove.forEach { item ->
                val matches = reference != null && runCatching { sha256(File(item.path)) }.getOrNull() == reference
                if (matches) removable += item else unverified += item
            }
        }
        return DuplicatePlan(removable, unverified)
    }
}
