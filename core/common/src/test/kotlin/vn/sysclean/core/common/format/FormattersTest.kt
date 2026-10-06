package vn.sysclean.core.common.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattersTest {
    @Test
    fun formatBytes_usesDecimalUnits() {
        assertEquals("999 B", formatBytes(999, Locale.US))
        assertEquals("1.0 KB", formatBytes(1000, Locale.US))
        assertEquals("1.5 GB", formatBytes(1_500_000_000, Locale.US))
        assertEquals("128 GB", formatBytes(128_000_000_000, Locale.US))
    }

    @Test
    fun formatBytes_respectsLocaleDecimalSeparator() {
        assertEquals("1,5 GB", formatBytes(1_500_000_000, Locale.forLanguageTag("vi")))
    }

    @Test
    fun formatFrequency_switchesToGhz() {
        assertEquals("800 MHz", formatFrequency(800_000, Locale.US))
        assertEquals("2.84 GHz", formatFrequency(2_841_600, Locale.US))
    }

    @Test
    fun formatDuration_dropsLeadingZeroUnits() {
        assertEquals("5m", formatDuration(5 * 60_000L))
        assertEquals("2h 0m", formatDuration(2 * 3_600_000L))
        assertEquals("1d 3h 7m", formatDuration(((24 + 3) * 60 + 7) * 60_000L))
    }
}
