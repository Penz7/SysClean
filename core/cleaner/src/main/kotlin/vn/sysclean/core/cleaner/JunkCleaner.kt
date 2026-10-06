package vn.sysclean.core.cleaner

import vn.sysclean.core.model.CleanResult
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.JunkItem
import vn.sysclean.core.model.JunkReport

interface JunkCleaner {
    /**
     * Cleans [selected] items of [category]. [all] is the whole category so grouped
     * categories can verify against, and always keep, one member of each set.
     * App cache is cleaned only with Shizuku; in normal mode it goes through a system dialog.
     * Items inside Android/data or obb (Shizuku only) are deleted for good, not binned.
     */
    suspend fun clean(
        category: JunkCategory,
        selected: List<JunkItem>,
        all: List<JunkItem> = selected,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): CleanResult

    /** Cleans every safe file category of [report] in one go. */
    suspend fun cleanSafe(report: JunkReport, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): CleanResult

    /**
     * Shizuku only: lets Android free every app's cache, internal and external.
     * Returns the bytes freed, measured from storage stats before and after.
     */
    suspend fun clearAllAppCaches(): Long

    /** Puts items back from the recycle bin; returns how many were restored. */
    suspend fun undo(result: CleanResult): Int
}
