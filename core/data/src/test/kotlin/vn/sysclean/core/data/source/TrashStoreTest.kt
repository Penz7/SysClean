package vn.sysclean.core.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.sysclean.core.data.repository.covers
import java.io.File

class TrashStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val store by lazy { TrashStore(File(tmp.root, "trash")) }

    private fun file(path: String, text: String = "data") =
        File(tmp.root, path).apply { parentFile!!.mkdirs(); writeText(text) }

    @Test
    fun moveAndRestoreRoundTripsContentAndHidesFromGallery() {
        val photo = file("DCIM/Camera/a.jpg", "pixels")
        val trashed = store.moveIn(photo, "id-1")!!

        assertFalse(photo.exists())
        assertTrue(File(store.root, ".nomedia").exists())

        val restored = store.restore(trashed, photo.path)!!
        assertEquals(photo.path, restored.path)
        assertEquals("pixels", restored.readText())
    }

    @Test
    fun restoreNeverOverwritesAFileThatTookThePlace() {
        val original = file("Download/report.pdf", "old")
        val trashed = store.moveIn(original, "id-2")!!
        file("Download/report.pdf", "new")

        val restored = store.restore(trashed, original.path)!!
        assertEquals("report (1).pdf", restored.name)
        assertEquals("new", File(tmp.root, "Download/report.pdf").readText())
        assertEquals("old", restored.readText())
    }

    @Test
    fun movesWholeFoldersAndRecreatesMissingParents() {
        file("Android/media/com.gone/a/b.bin", "x")
        val folder = File(tmp.root, "Android/media/com.gone")
        val trashed = store.moveIn(folder, "id-3")!!
        File(tmp.root, "Android/media").deleteRecursively()

        val restored = store.restore(trashed, folder.path)!!
        assertEquals("x", File(restored, "a/b.bin").readText())
    }

    @Test
    fun missingSourceIsNotMoved() {
        assertNull(store.moveIn(File(tmp.root, "nope.txt"), "id-4"))
    }

    @Test
    fun orphansIgnoreKnownIdsAndTheNomediaMarker() {
        store.moveIn(file("a.txt"), "known")
        store.moveIn(file("b.txt"), "orphan")
        val orphans = store.orphans(knownIds = setOf("known"), olderThan = Long.MAX_VALUE).map { it.name }
        assertEquals(listOf("orphan"), orphans)
    }

    @Test
    fun whitelistCoversNestedPathsButNotSiblingsWithSamePrefix() {
        val ignored = setOf("/sdcard/Download/Keep")
        assertTrue(ignored.covers("/sdcard/Download/Keep"))
        assertTrue(ignored.covers("/sdcard/Download/Keep/x.apk"))
        assertFalse(ignored.covers("/sdcard/Download/Keeper/x.apk"))
    }
}
