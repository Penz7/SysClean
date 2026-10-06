package vn.sysclean.core.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are real dumps from a Galaxy A05 (One UI 7, Android 15). */
class ParsersTest {
    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun meminfoTotalsAndGroups() {
        val map = Parsers.meminfo(resource("meminfo_a05.txt"))!!
        assertEquals(3_748_996L, map.totalKb)
        assertEquals(446_272L, map.zramPhysicalKb)
        assertEquals(1_497_156L, map.swapUsedKb)

        fun group(name: String) = map.processes.first { it.name == name }.group
        assertEquals(MemoryGroup.SYSTEM, group("system"))
        assertEquals(MemoryGroup.ACTIVE, group("com.sec.android.app.launcher"))
        assertEquals(MemoryGroup.BACKGROUND, group("com.samsung.android.rubin.app"))
        assertEquals(MemoryGroup.CACHED, group("com.android.vending"))
        assertEquals(186_439L, map.processes.first { it.name == "com.android.systemui" }.pssKb)
    }

    @Test
    fun processNamesMapToPackages() {
        assertEquals("com.google.android.apps.messaging", ProcessMemory("com.google.android.apps.messaging:rcs", 1, 1, MemoryGroup.ACTIVE).packageName)
        assertEquals(null, ProcessMemory("surfaceflinger", 1, 1, MemoryGroup.SYSTEM).packageName)
        assertEquals(null, ProcessMemory("/system/bin/vold", 1, 1, MemoryGroup.SYSTEM).packageName)
    }

    @Test
    fun onlyTheMainApkDecidesWhetherAnAppIsOptimized() {
        val unoptimized = Parsers.unoptimizedPackages(resource("dexopt_a05.txt"))
        // A system app whose main APK is only verified.
        assertTrue("com.android.backupconfirm" in unoptimized)
        // Overlay without code has status=unknown and must not be reported.
        assertFalse("android.auto_generated_rro_product__" in unoptimized)
    }

    @Test
    fun splitApksInVerifyDoNotMarkACompiledAppAsUnoptimized() {
        val dump = """
            Dexopt state:
              [com.zing.zalo]
                path: /data/app/x/com.zing.zalo-y/base.apk
                  arm64: [status=speed-profile] [reason=bg-dexopt] [primary-abi]
                path: /data/app/x/com.zing.zalo-y/split_tensorflowLite.apk
                  arm64: [status=verify] [reason=vdex-dm] [primary-abi]
              [com.slow.app]
                path: /data/app/x/com.slow.app-y/base.apk
                  arm64: [status=verify] [reason=install] [primary-abi]
        """.trimIndent().prependIndent("")
        assertEquals(setOf("com.slow.app"), Parsers.unoptimizedPackages(dump))
    }

    @Test
    fun gfxinfoFrameStats() {
        val stats = Parsers.gfxinfo(resource("gfxinfo_a05.txt"))
        assertNotNull(stats)
        assertTrue(stats!!.totalFrames > 0)
        assertTrue(stats.jankyPercent in 0.0..100.0)
        assertNotNull(stats.p90Ms)
    }

    @Test
    fun pressureAverages() {
        val p = Parsers.pressure("some avg10=0.18 avg60=0.25 avg300=0.33 total=1\nfull avg10=0.18 avg60=0.12 avg300=0.31 total=1\n")
        assertEquals(0.25, p!!.someAvg60, 1e-9)
        assertEquals(0.12, p.fullAvg60, 1e-9)
    }
}
