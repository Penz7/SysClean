package vn.sysclean.core.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SysFsTest {
    @Test
    fun parseCpuRange_handlesRangesAndLists() {
        assertEquals((0..7).toList(), SysFs.parseCpuRange("0-7"))
        assertEquals(listOf(0, 1, 2, 3, 6), SysFs.parseCpuRange("0-3,6"))
        assertEquals(listOf(0), SysFs.parseCpuRange("0"))
    }

    @Test
    fun parseMeminfo_convertsKilobytesToBytes() {
        val meminfo = """
            MemTotal:        7810136 kB
            SwapTotal:       4194300 kB
            SwapFree:        2097152 kB
        """.trimIndent()
        val parsed = SysFs.parseMeminfo(meminfo)
        assertEquals(4194300L * 1024, parsed["SwapTotal"])
        assertEquals(2097152L * 1024, parsed["SwapFree"])
    }

    @Test
    fun cpuinfoField_findsHardwareCaseInsensitively() {
        val cpuinfo = "processor\t: 0\nHardware\t: Qualcomm Technologies, Inc SM8250\n"
        assertEquals("Qualcomm Technologies, Inc SM8250", SysFs.cpuinfoField(cpuinfo, "hardware"))
        assertNull(SysFs.cpuinfoField(cpuinfo, "Revision"))
    }
}
