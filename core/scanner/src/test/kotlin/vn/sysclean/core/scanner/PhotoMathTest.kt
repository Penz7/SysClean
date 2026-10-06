package vn.sysclean.core.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PhotoMathTest {
    /** A synthetic "photo": a few rectangles with hard edges on a gradient. */
    private fun scene(width: Int = 120, height: Int = 90, seed: Int = 1, shift: Int = 0): GrayImage {
        val random = Random(seed)
        val pixels = IntArray(width * height) { i -> (i % width) * 200 / width + 20 }
        repeat(6) {
            val x0 = random.nextInt(width - 20) + shift
            val y0 = random.nextInt(height - 20)
            val shade = random.nextInt(256)
            for (y in y0 until minOf(height, y0 + 20)) for (x in x0 until minOf(width, x0 + 20)) {
                pixels[y * width + x] = shade
            }
        }
        return GrayImage(width, height, pixels)
    }

    private fun blur(image: GrayImage, radius: Int): GrayImage {
        val out = IntArray(image.pixels.size)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            var sum = 0
            var n = 0
            for (dy in -radius..radius) for (dx in -radius..radius) {
                val xx = (x + dx).coerceIn(0, image.width - 1)
                val yy = (y + dy).coerceIn(0, image.height - 1)
                sum += image[xx, yy]
                n++
            }
            out[y * image.width + x] = sum / n
        }
        return GrayImage(image.width, image.height, out)
    }

    @Test
    fun dHashIsStableUnderResizeAndMildBlur() {
        val original = scene()
        val resized = PhotoMath.downsample(original, 60, 45)
        val softened = blur(original, 1)
        assertTrue(PhotoMath.hammingDistance(PhotoMath.dHash(original), PhotoMath.dHash(resized)) <= 5)
        assertTrue(PhotoMath.hammingDistance(PhotoMath.dHash(original), PhotoMath.dHash(softened)) <= 5)
    }

    @Test
    fun differentScenesHaveDistantHashes() {
        val a = PhotoMath.dHash(scene(seed = 1))
        val b = PhotoMath.dHash(scene(seed = 99))
        assertTrue(PhotoMath.hammingDistance(a, b) > 10)
    }

    @Test
    fun blurLowersSharpnessByAnOrderOfMagnitude() {
        val sharp = scene()
        val blurry = blur(sharp, 4)
        assertTrue(PhotoMath.sharpness(sharp) > 10 * PhotoMath.sharpness(blurry))
    }

    @Test
    fun uniformImageHasZeroContrastAndSharpness() {
        val flat = GrayImage(10, 10, IntArray(100) { 128 })
        assertEquals(0.0, PhotoMath.contrast(flat), 1e-9)
        assertEquals(0.0, PhotoMath.sharpness(flat), 1e-9)
    }

    @Test
    fun whiteChartsHaveLowStructureWhileScenesHaveHigh() {
        // A white page with a few 1-px lines, like a saved price chart.
        val pixels = IntArray(200 * 150) { 255 }
        for (x in 0 until 200 step 17) for (y in 20 until 130) pixels[y * 200 + x] = 0
        val chart = GrayImage(200, 150, pixels)
        assertTrue(PhotoMath.structure(chart) < 15)
        assertTrue(PhotoMath.structure(scene()) > 25)
    }

    @Test
    fun setsNeedSimilarLookAndCloseTime() {
        val minute = 60_000L
        val photos = listOf(
            PhotoMath.Candidate(0, 0b0000L),
            PhotoMath.Candidate(1 * minute, 0b0001L), // burst of the same scene
            PhotoMath.Candidate(2 * minute, -1L), // different scene, same moment
            PhotoMath.Candidate(600 * minute, 0b0000L), // same look, hours later
        )
        assertEquals(listOf(listOf(0, 1)), PhotoMath.similarSets(photos, maxDistance = 4, windowMillis = 30 * minute))
    }

    @Test
    fun setsDoNotChainThroughIntermediatePhotos() {
        // 0~1 and 1~2 are close, but 0 and 2 are 6 bits apart: 2 must not join 0's set.
        val photos = listOf(
            PhotoMath.Candidate(0, 0L),
            PhotoMath.Candidate(1, 0b111L),
            PhotoMath.Candidate(2, 0b111111L),
        )
        assertEquals(listOf(listOf(0, 1)), PhotoMath.similarSets(photos, maxDistance = 3, windowMillis = 10))
    }
}
