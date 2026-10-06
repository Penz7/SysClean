package vn.sysclean.feature.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Widgets cannot draw arcs, so the score ring is rendered to a bitmap: the same 270° gauge
 * as the dashboard. The track is translucent so it works on light and dark wallpapers.
 */
internal object GaugeBitmap {
    private const val START = 135f
    private const val SWEEP = 270f

    fun render(score: Int?, color: Int, trackColor: Int, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stroke = sizePx * 0.1f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        val bounds = RectF(stroke / 2, stroke / 2, sizePx - stroke / 2, sizePx - stroke / 2)
        canvas.drawArc(bounds, START, SWEEP, false, paint.apply { this.color = trackColor })
        if (score != null) {
            val fraction = score.coerceIn(0, 100) / 100f
            canvas.drawArc(bounds, START, SWEEP * fraction, false, paint.apply { this.color = color })
        }
        return bitmap
    }
}
