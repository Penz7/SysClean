package vn.sysclean.core.privilege

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.sysclean.core.privilege.shell.PrivilegedPaths

class PrivilegedPathsTest {
    @Test
    fun packageFoldersAndTheirContentsAreDeletable() {
        assertTrue(PrivilegedPaths.isDeletable("/storage/emulated/0/Android/data/com.gone.app"))
        assertTrue(PrivilegedPaths.isDeletable("/storage/emulated/0/Android/data/org.telegram.messenger/cache"))
        assertTrue(PrivilegedPaths.isDeletable("/storage/emulated/0/Android/obb/com.game"))
    }

    @Test
    fun rootsAndEverythingElseAreRefused() {
        assertFalse(PrivilegedPaths.isDeletable("/storage/emulated/0/Android/data"))
        assertFalse(PrivilegedPaths.isDeletable("/storage/emulated/0/Android/data/"))
        assertFalse(PrivilegedPaths.isDeletable("/storage/emulated/0/DCIM"))
        assertFalse(PrivilegedPaths.isDeletable("/data/data/com.app"))
        assertFalse(PrivilegedPaths.isDeletable("/storage/emulated/0/Android/datax/com.app"))
    }

    @Test
    fun traversalTricksAreRefused() {
        assertFalse(PrivilegedPaths.isDeletable("/storage/emulated/0/Android/data/../../DCIM"))
        assertFalse(PrivilegedPaths.isDeletable("/storage/emulated/0/Android/data/./com.app"))
        assertFalse(PrivilegedPaths.isDeletable("/storage/emulated/0/Android/data//com.app"))
    }

    @Test
    fun onlyPackageShapedNamesCountAsAppFolders() {
        assertTrue(PrivilegedPaths.looksLikePackage("org.telegram.messenger"))
        assertTrue(PrivilegedPaths.looksLikePackage("com.mtk.android.networkstack_overlay"))
        assertFalse(PrivilegedPaths.looksLikePackage(".nomedia"))
        assertFalse(PrivilegedPaths.looksLikePackage("cache"))
        assertFalse(PrivilegedPaths.looksLikePackage("com..broken"))
    }

    @Test
    fun extraCommandsAreAllowedOnlyInTheirExactShape() {
        assertTrue(PrivilegedPaths.isCommandAllowed(listOf("dumpsys", "meminfo")))
        assertTrue(PrivilegedPaths.isCommandAllowed(listOf("dumpsys", "gfxinfo", "com.sec.android.app.launcher", "reset")))
        assertTrue(PrivilegedPaths.isCommandAllowed(listOf("sm", "fstrim")))
        assertTrue(PrivilegedPaths.isCommandAllowed(listOf("cat", "/proc/pressure/memory")))
        assertTrue(PrivilegedPaths.isCommandAllowed(listOf("svc", "power", "reboot")))

        assertFalse(PrivilegedPaths.isCommandAllowed(listOf("dumpsys", "batterystats", "--reset")))
        assertFalse(PrivilegedPaths.isCommandAllowed(listOf("sm", "format", "private")))
        assertFalse(PrivilegedPaths.isCommandAllowed(listOf("cat", "/data/system/packages.xml")))
        assertFalse(PrivilegedPaths.isCommandAllowed(listOf("svc", "power", "shutdown")))
        assertFalse(PrivilegedPaths.isCommandAllowed(listOf("svc", "wifi", "disable")))
        assertFalse(PrivilegedPaths.isCommandAllowed(listOf("reboot")))
        assertFalse(PrivilegedPaths.isCommandAllowed(emptyList()))
    }
}
