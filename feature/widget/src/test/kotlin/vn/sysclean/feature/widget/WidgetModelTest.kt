package vn.sysclean.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import vn.sysclean.core.data.repository.HealthSnapshot
import vn.sysclean.core.model.MemoryInfo
import vn.sysclean.core.model.StorageInfo
import vn.sysclean.core.model.StorageVolumeInfo

class WidgetModelTest {
    private fun snapshot(free: Long, availableRam: Long) = HealthSnapshot(
        storage = StorageInfo(listOf(StorageVolumeInfo("Internal", true, false, 128_000_000_000, free)), null),
        memory = MemoryInfo(4_000_000_000, availableRam, 0, false, null, null),
    )

    @Test
    fun smallChangesDoNotChangeTheModel() {
        // A few MB written and RAM moving within the same whole percent must not redraw the widget.
        val a = WidgetModel.from(snapshot(free = 64_012_000_000, availableRam = 1_400_000_000))
        val b = WidgetModel.from(snapshot(free = 64_049_000_000, availableRam = 1_390_000_000))
        assertEquals(a, b)
    }

    @Test
    fun visibleChangesDo() {
        val a = WidgetModel.from(snapshot(free = 64_000_000_000, availableRam = 1_400_000_000))
        val b = WidgetModel.from(snapshot(free = 64_000_000_000, availableRam = 1_000_000_000))
        assertEquals(65, a.ramUsedPercent)
        assertEquals(75, b.ramUsedPercent)
    }

    @Test
    fun missingSourcesStayNull() {
        val model = WidgetModel.from(HealthSnapshot())
        assertNull(model.score)
        assertNull(model.storageUsedPercent)
        assertFalse(model.scanned)
    }
}
