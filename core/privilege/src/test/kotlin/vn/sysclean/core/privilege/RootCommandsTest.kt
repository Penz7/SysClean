package vn.sysclean.core.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.sysclean.core.privilege.shell.PrivilegedPaths
import vn.sysclean.core.privilege.shell.RootCommands

class RootCommandsTest {
    @Test
    fun quotingNeutralisesShellMetacharacters() {
        assertEquals("'a b'", RootCommands.quote("a b"))
        assertEquals("'x'\\''; rm -rf / #'", RootCommands.quote("x'; rm -rf / #"))
        assertEquals("'\$(reboot)'", RootCommands.quote("\$(reboot)"))
    }

    @Test
    fun onlyAllowedBinariesRun() {
        assertEquals("'pm' 'uninstall' 'com.app'", RootCommands.command(listOf("pm", "uninstall", "com.app")))
        assertThrows(IllegalArgumentException::class.java) { RootCommands.command(listOf("rm", "-rf", "/")) }
        assertThrows(IllegalArgumentException::class.java) { RootCommands.command(listOf("sh", "-c", "reboot")) }
        assertThrows(IllegalArgumentException::class.java) { RootCommands.command(emptyList()) }
    }

    @Test
    fun deleteIsLimitedToAppFolders() {
        val ok = "/storage/emulated/0/Android/data/com.gone"
        assertEquals("rm -rf '$ok'", RootCommands.delete(ok))
        listOf("/", "/data", "/system", "/storage/emulated/0/DCIM", "/data/data/com.app", "/storage/emulated/0/Android/data")
            .forEach { path -> assertThrows(path, IllegalArgumentException::class.java) { RootCommands.delete(path) } }
    }

    @Test
    fun privateCachesCanBeEmptiedButNotDeleted() {
        val cache = "/data/data/com.app/cache"
        assertTrue(RootCommands.deleteContents(cache).contains("find '$cache' -mindepth 1 -maxdepth 1"))
        assertThrows(IllegalArgumentException::class.java) { RootCommands.delete(cache) }
        assertThrows(IllegalArgumentException::class.java) { RootCommands.deleteContents("/data/data/com.app/databases") }
        assertThrows(IllegalArgumentException::class.java) { RootCommands.deleteContents("/data/data/com.app") }
    }

    @Test
    fun internalCachePatternRejectsTraversalAndOddNames() {
        assertTrue(PrivilegedPaths.isInternalCacheDir("/data/user/10/com.app/code_cache"))
        assertTrue(PrivilegedPaths.isInternalCacheDir("/data/user_de/0/com.app/cache"))
        assertFalse(PrivilegedPaths.isInternalCacheDir("/data/data/../system/cache"))
        assertFalse(PrivilegedPaths.isInternalCacheDir("/data/data/com.app/cache/../../x"))
        assertFalse(PrivilegedPaths.isInternalCacheDir("/data/data/.hidden/cache"))
    }

    @Test
    fun parsesDuKibOutput() {
        assertEquals(2048L, RootCommands.parseKib("2\n"))
        assertEquals(0L, RootCommands.parseKib(""))
    }
}
