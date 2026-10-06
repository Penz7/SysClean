package vn.sysclean.core.scanner

import kotlin.math.sqrt

/** Grayscale image as 0..255 luminance values, row-major. */
internal class GrayImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height)
    }

    operator fun get(x: Int, y: Int) = pixels[y * width + x]
}

/**
 * Pure image maths behind the photo categories. Kept free of android.graphics so the
 * thresholds can be unit-tested on synthetic images.
 */
internal object PhotoMath {

    /** Box-filter downsample; unlike nearest-neighbour it does not alias on large ratios. */
    fun downsample(image: GrayImage, width: Int, height: Int): GrayImage {
        val out = IntArray(width * height)
        for (ty in 0 until height) {
            val y0 = ty * image.height / height
            val y1 = maxOf(y0 + 1, (ty + 1) * image.height / height)
            for (tx in 0 until width) {
                val x0 = tx * image.width / width
                val x1 = maxOf(x0 + 1, (tx + 1) * image.width / width)
                var sum = 0L
                for (y in y0 until y1) for (x in x0 until x1) sum += image[x, y]
                out[ty * width + tx] = (sum / ((y1 - y0) * (x1 - x0))).toInt()
            }
        }
        return GrayImage(width, height, out)
    }

    /**
     * 64-bit difference hash: each bit says whether a pixel is brighter than its right
     * neighbour on a 9x8 thumbnail. Robust to resizing, recompression and small edits.
     */
    fun dHash(image: GrayImage): Long {
        val small = downsample(image, 9, 8)
        var hash = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            hash = hash shl 1
            if (small[x, y] > small[x + 1, y]) hash = hash or 1L
        }
        return hash
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Variance of the 4-neighbour Laplacian: low values mean few sharp edges, i.e. blur. */
    fun sharpness(image: GrayImage): Double {
        if (image.width < 3 || image.height < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until image.height - 1) for (x in 1 until image.width - 1) {
            val lap = (4 * image[x, y] - image[x - 1, y] - image[x + 1, y] - image[x, y - 1] - image[x, y + 1]).toDouble()
            sum += lap
            sumSq += lap * lap
            n++
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    /** Standard deviation of brightness. Near-uniform shots (black, sky) have tiny values. */
    fun contrast(image: GrayImage): Double {
        val mean = image.pixels.average()
        val variance = image.pixels.sumOf { (it - mean) * (it - mean) } / image.pixels.size
        return sqrt(variance)
    }

    /**
     * Brightness spread of the 9x8 grid the hash is computed from. White charts, documents
     * and flat skies collapse to an almost uniform grid whose hash is mostly noise, so
     * such images must not be compared by hash at all.
     */
    fun structure(image: GrayImage): Double = contrast(downsample(image, 9, 8))

    class Candidate(val takenAt: Long, val hash: Long)

    /**
     * Leader clustering: photos are walked in time order and join a set only if they are
     * close to that set's first photo, both visually and in time. Unlike union-find this
     * never chains A~B~C into one set when A and C look nothing alike.
     */
    fun similarSets(photos: List<Candidate>, maxDistance: Int, windowMillis: Long): List<List<Int>> {
        val order = photos.indices.sortedBy { photos[it].takenAt }
        val sets = mutableListOf<MutableList<Int>>()
        for (index in order) {
            val photo = photos[index]
            val home = sets
                .filter { photo.takenAt - photos[it.first()].takenAt <= windowMillis }
                .minByOrNull { hammingDistance(photos[it.first()].hash, photo.hash) }
                ?.takeIf { hammingDistance(photos[it.first()].hash, photo.hash) <= maxDistance }
            if (home != null) home += index else sets += mutableListOf(index)
        }
        return sets.filter { it.size > 1 }
    }
}
