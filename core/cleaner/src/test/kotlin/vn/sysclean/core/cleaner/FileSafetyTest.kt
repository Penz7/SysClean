package vn.sysclean.core.cleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.sysclean.core.model.JunkItem
import java.io.File

class FileSafetyTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(path: String, bytes: ByteArray) =
        File(tmp.root, path).apply { parentFile!!.mkdirs(); writeBytes(bytes) }

    private fun item(file: File, group: String = "g", original: Boolean = false) =
        JunkItem(file.path, file.name, file.length(), 0, groupId = group, isOriginal = original)

    @Test
    fun emptyFolderTreeIsDeletedButAFileInsideBlocksIt() {
        File(tmp.root, "a/b/c").mkdirs()
        File(tmp.root, "x/y").mkdirs()
        file("x/y/keep.txt", byteArrayOf(1))

        assertTrue(FileSafety.deleteIfEmpty(File(tmp.root, "a")))
        assertFalse(File(tmp.root, "a").exists())
        assertFalse(FileSafety.deleteIfEmpty(File(tmp.root, "x")))
        assertTrue(File(tmp.root, "x/y/keep.txt").exists())
    }

    @Test
    fun copiesMatchingTheKeptFileAreRemovable() {
        val body = ByteArray(300_000) { (it % 7).toByte() }
        val original = item(file("a.mp4", body), original = true)
        val copy = item(file("b.mp4", body))

        val plan = FileSafety.planDuplicates(selected = listOf(copy), all = listOf(original, copy))
        assertEquals(listOf(copy), plan.removable)
    }

    @Test
    fun sameHeadAndTailButDifferentMiddleIsNotRemoved() {
        val body = ByteArray(300_000) { (it % 7).toByte() }
        val tampered = body.copyOf().also { it[150_000] = 99 }
        val original = item(file("a.bin", body), original = true)
        val lookalike = item(file("b.bin", tampered))

        val plan = FileSafety.planDuplicates(listOf(lookalike), listOf(original, lookalike))
        assertTrue(plan.removable.isEmpty())
        assertEquals(listOf(lookalike), plan.unverified)
    }

    @Test
    fun selectingEveryCopyStillKeepsTheOriginal() {
        val body = byteArrayOf(1, 2, 3)
        val original = item(file("a", body), original = true)
        val copy1 = item(file("b", body))
        val copy2 = item(file("c", body))

        val plan = FileSafety.planDuplicates(listOf(original, copy1, copy2), listOf(original, copy1, copy2))
        assertEquals(setOf(copy1, copy2), plan.removable.toSet())
    }
}
