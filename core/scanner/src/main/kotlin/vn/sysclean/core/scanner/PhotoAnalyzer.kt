package vn.sysclean.core.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import vn.sysclean.core.database.dao.PhotoSignatureDao
import vn.sysclean.core.database.model.PhotoSignatureEntity
import vn.sysclean.core.model.JunkItem
import java.util.concurrent.atomic.AtomicInteger

internal class PhotoResult(val similar: TopItems, val blurry: TopItems)

/**
 * Finds near-identical shots and blurry photos among MediaStore images. Signatures are
 * cached by media id + modification time, so only new or edited photos are decoded again.
 */
internal class PhotoAnalyzer(
    private val context: Context,
    private val dao: PhotoSignatureDao,
    private val io: CoroutineDispatcher,
    private val isIgnored: (String) -> Boolean,
    private val onProgress: (name: String, done: Int, total: Int) -> Unit,
) {
    private class Photo(
        val id: Long,
        val path: String,
        val name: String,
        val size: Long,
        val modified: Long,
        /** Capture time in ms; falls back to modification time for images without EXIF date. */
        val takenAt: Long,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun analyze(excludedPaths: Set<String>): PhotoResult = coroutineScope {
        val photos = queryPhotos().filter { it.path !in excludedPaths && !isIgnored(it.path) }
        val cached = dao.getAll().associateBy { it.mediaId }
        val done = AtomicInteger()
        val decoder = io.limitedParallelism(DECODE_PARALLELISM)

        val signatures = photos.map { photo ->
            async(decoder) {
                ensureActive()
                val hit = cached[photo.id]?.takeIf {
                    it.dateModified == photo.modified && it.sizeBytes == photo.size && it.algorithm == ALGORITHM_VERSION
                }
                val signature = hit ?: signatureOf(photo)
                onProgress(photo.name, done.incrementAndGet(), photos.size)
                signature
            }
        }.awaitAll()

        dao.upsert(signatures.filterNotNull())
        dao.deleteAllExcept(photos.map { it.id })

        val analysed = photos.zip(signatures).mapNotNull { (photo, sig) -> sig?.let { photo to it } }
        PhotoResult(similar = similarItems(analysed), blurry = blurryItems(analysed))
    }

    private fun similarItems(analysed: List<Pair<Photo, PhotoSignatureEntity>>): TopItems {
        val result = TopItems(StorageWalker.MAX_ITEMS)
        val candidates = analysed.filter { it.second.structure >= MIN_STRUCTURE }
        PhotoMath.similarSets(
            candidates.map { PhotoMath.Candidate(it.first.takenAt, it.second.dHash) },
            SIMILAR_MAX_DISTANCE,
            SIMILAR_WINDOW_MILLIS,
        ).forEachIndexed { index, set ->
            val members = set.map { candidates[it] }
            val best = members.maxWith(compareBy({ it.second.sharpness }, { it.first.size }))
            members.forEach { (photo, _) ->
                val keep = photo === best.first
                result.add(
                    item = photo.toItem(groupId = "sim-$index", isOriginal = keep),
                    countsAs = if (keep) 0 else 1,
                    bytes = if (keep) 0 else photo.size,
                )
            }
        }
        return result
    }

    private fun blurryItems(analysed: List<Pair<Photo, PhotoSignatureEntity>>): TopItems {
        val result = TopItems(StorageWalker.MAX_ITEMS)
        analysed.filter { (_, sig) -> sig.contrast >= MIN_CONTRAST && sig.sharpness < BLUR_THRESHOLD }
            .forEach { (photo, _) -> result.add(photo.toItem()) }
        return result
    }

    private fun queryPhotos(): List<Photo> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            @Suppress("DEPRECATION") MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_TAKEN,
        )
        val photos = mutableListOf<Photo>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val path = cursor.getString(1) ?: continue
                val width = cursor.getInt(5)
                val height = cursor.getInt(6)
                // Icons, stickers and emoji are not photos worth reviewing.
                if (width in 1 until MIN_DIMENSION || height in 1 until MIN_DIMENSION) continue
                if (EXCLUDED_SEGMENTS.any { path.contains(it, ignoreCase = true) }) continue
                val modified = cursor.getLong(4)
                val taken = cursor.getLong(7).takeIf { it > 0 } ?: (modified * 1000)
                photos += Photo(cursor.getLong(0), path, cursor.getString(2) ?: path, cursor.getLong(3), modified, taken)
            }
        }
        return photos
    }

    private fun signatureOf(photo: Photo): PhotoSignatureEntity? {
        val gray = decodeGray(photo.path) ?: return null
        return PhotoSignatureEntity(
            mediaId = photo.id,
            dateModified = photo.modified,
            sizeBytes = photo.size,
            dHash = PhotoMath.dHash(gray),
            sharpness = PhotoMath.sharpness(gray),
            contrast = PhotoMath.contrast(gray),
            structure = PhotoMath.structure(gray),
            algorithm = ALGORITHM_VERSION,
        )
    }

    /** Decodes at roughly [ANALYSIS_SIZE] px on the long side so sharpness is comparable across photos. */
    private fun decodeGray(path: String): GrayImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ANALYSIS_SIZE) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = ANALYSIS_SIZE.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
                .also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        val argb = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val gray = GrayImage(bitmap.width, bitmap.height, IntArray(argb.size) {
            val c = argb[it]
            ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
        })
        bitmap.recycle()
        return gray
    }

    private fun Photo.toItem(groupId: String? = null, isOriginal: Boolean = false) = JunkItem(
        path = path,
        title = name,
        sizeBytes = size,
        lastModified = modified * 1000,
        groupId = groupId,
        isOriginal = isOriginal,
        mediaId = id,
    )

    companion object {
        /** Bump when the signature maths changes so cached signatures are recomputed. */
        const val ALGORITHM_VERSION = 2
        const val ANALYSIS_SIZE = 512
        const val SIMILAR_MAX_DISTANCE = 4
        val SIMILAR_WINDOW_MILLIS = java.util.concurrent.TimeUnit.MINUTES.toMillis(30)
        const val MIN_CONTRAST = 20.0

        /** Below this the 9x8 hash grid is nearly flat and the hash means little. */
        const val MIN_STRUCTURE = 15.0

        /** Laplacian variance below this at 512 px is visibly out of focus or shaken. */
        const val BLUR_THRESHOLD = 60.0
        private const val MIN_DIMENSION = 300
        private const val DECODE_PARALLELISM = 4

        // Screenshots are their own category, and app-private folders are off limits.
        private val EXCLUDED_SEGMENTS = listOf("/Screenshots/", "/.thumbnails/", "/Android/data/", "/Android/obb/")
    }
}

