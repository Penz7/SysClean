package vn.sysclean.core.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.sysclean.core.privilege.shell.BloatwarePolicy

class BloatwarePolicyTest {
    @Test
    fun coreSystemPackagesAreAlwaysProtected() {
        listOf(
            "android", "com.android.systemui", "com.android.settings", "com.android.phone",
            "com.google.android.gms", "com.android.vending", "com.sec.android.app.launcher", "com.miui.home",
            // Other brands' launchers and security centres.
            "com.oppo.launcher", "com.oplus.battery", "com.coloros.safecenter", "com.bbk.launcher2",
            "com.huawei.android.launcher", "com.transsion.hilauncher", "com.google.android.apps.nexuslauncher",
        ).forEach { assertTrue(it, BloatwarePolicy.isProtected(it)) }
    }

    @Test
    fun runtimeProtectionWinsOverTheRecommendation() {
        // If the user picked Game Launcher as their home screen, it must not be offered for removal.
        val launcher = setOf("com.samsung.android.game.gamehome")
        assertTrue(BloatwarePolicy.isRecommended("com.samsung.android.game.gamehome"))
        assertFalse(BloatwarePolicy.isRecommended("com.samsung.android.game.gamehome", launcher))
    }

    @Test
    fun noRecommendedPackageIsProtectedByDefault() {
        val clashes = BloatwarePolicy.recommended.keys.filter { BloatwarePolicy.isProtected(it) }
        assertEquals(emptyList<String>(), clashes)
    }

    @Test
    fun unknownPackagesAreNeitherRecommendedNorProtected() {
        assertFalse(BloatwarePolicy.isRecommended("com.example.someapp"))
        assertFalse(BloatwarePolicy.isProtected("com.example.someapp"))
    }
}
