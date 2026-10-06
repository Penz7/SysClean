package vn.sysclean.core.scanner

import kotlinx.coroutines.flow.StateFlow
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.JunkItem
import vn.sysclean.core.model.ScanState

/**
 * Runs in the application scope so a scan keeps going while the user moves between
 * screens, and the last report stays available until the next scan.
 */
interface JunkScanner {
    val state: StateFlow<ScanState>
    fun start()
    fun cancel()

    /** Drops cleaned items from the last report so totals update without a rescan. */
    fun removeItems(category: JunkCategory, paths: Set<String>)

    /** Puts items back into the last report, e.g. after the user undoes a clean. */
    fun restoreItems(category: JunkCategory, items: List<JunkItem>)
}
