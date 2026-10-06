package vn.sysclean.feature.cleaner

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.JunkItem

class SelectionTest {
    private fun item(path: String, group: String? = null, original: Boolean = false) =
        JunkItem(path, path, 1, 0, groupId = group, isOriginal = original)

    @Test
    fun safeCategoriesStartFullySelected() {
        val items = listOf(item("a.tmp"), item("b.log"))
        assertEquals(setOf("a.tmp", "b.log"), defaultSelection(JunkCategory.TEMP_FILES, items))
    }

    @Test
    fun reviewCategoriesStartEmpty() {
        val items = listOf(item("movie.mkv"), item("shot.png"))
        assertEquals(emptySet<String>(), defaultSelection(JunkCategory.LARGE_FILES, items))
        assertEquals(emptySet<String>(), defaultSelection(JunkCategory.BLURRY_PHOTOS, items))
        assertEquals(emptySet<String>(), defaultSelection(JunkCategory.SCREENSHOTS, items))
    }

    @Test
    fun duplicatesPreselectEverythingButTheKeeperWhileSimilarPhotosStartEmpty() {
        val items = listOf(
            item("a1", "g1", original = true), item("a2", "g1"), item("a3", "g1"),
            item("b1", "g2", original = true), item("b2", "g2"),
        )
        assertEquals(setOf("a2", "a3", "b2"), defaultSelection(JunkCategory.DUPLICATE_FILES, items))
        // Similar is a judgement call, not a byte-for-byte match.
        assertEquals(emptySet<String>(), defaultSelection(JunkCategory.SIMILAR_PHOTOS, items))
        assertEquals(setOf("a2", "a3", "b2"), extrasOf(items))
    }
}
