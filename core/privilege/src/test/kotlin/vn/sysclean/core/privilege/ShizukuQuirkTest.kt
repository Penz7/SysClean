package vn.sysclean.core.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShizukuQuirkTest {
    @Test
    fun brandsWithAnExtraSwitch() {
        assertEquals(ShizukuQuirk.XIAOMI, ShizukuQuirk.of("Xiaomi", "Redmi"))
        assertEquals(ShizukuQuirk.XIAOMI, ShizukuQuirk.of("Xiaomi", "POCO"))
        assertEquals(ShizukuQuirk.COLOROS, ShizukuQuirk.of("OPPO", "OPPO"))
        assertEquals(ShizukuQuirk.COLOROS, ShizukuQuirk.of("realme", "realme"))
        assertEquals(ShizukuQuirk.COLOROS, ShizukuQuirk.of("OnePlus", "OnePlus"))
        assertEquals(ShizukuQuirk.FLYME, ShizukuQuirk.of("Meizu", "meizu"))
    }

    @Test
    fun everyoneElseNeedsNothingExtra() {
        listOf("samsung" to "samsung", "Google" to "google", "vivo" to "vivo", "TECNO" to "TECNO", "motorola" to "motorola")
            .forEach { (m, b) -> assertNull("$m/$b", ShizukuQuirk.of(m, b)) }
    }
}
