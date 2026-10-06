package vn.sysclean.core.performance

import org.junit.Assert.assertEquals
import org.junit.Test

class AppClassifierTest {
    private val inUse = setOf("com.sec.android.app.launcher", "com.samsung.android.honeyboard", "vn.sysclean", "moe.shizuku.privileged.api")

    @Test
    fun launcherKeyboardSelfAndShizukuAreLocked() {
        inUse.forEach { assertEquals(it, AppKind.LOCKED, AppClassifier.kindOf(it, isUserApp = true, inActiveUse = inUse)) }
    }

    @Test
    fun coreSystemIsLockedEvenIfReportedAsUserApp() {
        listOf("com.google.android.gms", "com.android.systemui", "com.android.phone").forEach {
            assertEquals(it, AppKind.LOCKED, AppClassifier.kindOf(it, isUserApp = true, inActiveUse = inUse))
        }
    }

    @Test
    fun knownOptionalServicesAndUserApps() {
        assertEquals(AppKind.OPTIONAL_SERVICE, AppClassifier.kindOf("com.samsung.android.rubin.app", false, inUse))
        assertEquals(AppKind.OPTIONAL_SERVICE, AppClassifier.kindOf("com.google.android.googlequicksearchbox", false, inUse))
        assertEquals(AppKind.USER_APP, AppClassifier.kindOf("org.telegram.messenger", true, inUse))
        assertEquals(AppKind.SYSTEM_APP, AppClassifier.kindOf("com.samsung.android.app.contacts", false, inUse))
    }

    @Test
    fun anOptionalServiceInUseAsLauncherStaysLocked() {
        val googleHome = inUse + "com.google.android.googlequicksearchbox"
        assertEquals(AppKind.LOCKED, AppClassifier.kindOf("com.google.android.googlequicksearchbox", false, googleHome))
    }

    @Test
    fun unusedPreloadsOfAnyBrandAreSleepableButNeverOverrideSafety() {
        val unused = setOf("com.vivo.weather", "com.oppo.music", "com.huawei.himovie.overseas", "com.android.chrome", "com.sec.android.app.launcher")
        listOf("com.vivo.weather", "com.oppo.music", "com.huawei.himovie.overseas").forEach {
            assertEquals(it, AppKind.IDLE_PRELOAD, AppClassifier.kindOf(it, false, inUse, unused))
        }
        // Protected prefixes and the launcher win over "unused".
        assertEquals(AppKind.LOCKED, AppClassifier.kindOf("com.android.chrome", false, inUse, unused))
        assertEquals(AppKind.LOCKED, AppClassifier.kindOf("com.sec.android.app.launcher", false, inUse, unused))
        // A user app stays a user app.
        assertEquals(AppKind.USER_APP, AppClassifier.kindOf("com.oppo.music", true, inUse, unused))
    }

    @Test
    fun mostActiveGroupWins() {
        assertEquals(MemoryGroup.ACTIVE, AppClassifier.mostActive(listOf(MemoryGroup.CACHED, MemoryGroup.ACTIVE)))
        assertEquals(MemoryGroup.BACKGROUND, AppClassifier.mostActive(listOf(MemoryGroup.CACHED, MemoryGroup.BACKGROUND)))
        assertEquals(MemoryGroup.CACHED, AppClassifier.mostActive(listOf(MemoryGroup.CACHED)))
    }
}
