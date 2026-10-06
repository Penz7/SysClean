package vn.sysclean.core.scanner

import vn.sysclean.core.model.JunkItem
import java.util.PriorityQueue

/** Running total for one category that only remembers its [capacity] largest items. */
internal class TopItems(private val capacity: Int) {
    var count = 0
        private set
    var totalBytes = 0L
        private set
    private val heap = PriorityQueue<JunkItem>(compareBy { it.sizeBytes })

    fun add(item: JunkItem, countsAs: Int = 1, bytes: Long = item.sizeBytes) {
        count += countsAs
        totalBytes += bytes
        heap.add(item)
        if (heap.size > capacity) heap.poll()
    }

    fun items(): List<JunkItem> = heap.sortedByDescending { it.sizeBytes }
}
