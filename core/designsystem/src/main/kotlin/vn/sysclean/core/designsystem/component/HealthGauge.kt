package vn.sysclean.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vn.sysclean.core.designsystem.theme.SysCleanTheme

private const val SWEEP = 270f
private const val START = 135f

/** A 270° arc gauge for a 0..100 score. A null score draws an empty track (not yet measured). */
@Composable
fun HealthGauge(
    score: Int?,
    caption: String,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
) {
    val target = (score ?: 0).coerceIn(0, 100) / 100f
    val progress by animateFloatAsState(target, animationSpec = tween(900), label = "gauge")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val color = scoreColor(score)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            val topLeft = Offset(inset, inset)
            drawArc(track, START, SWEEP, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            if (score != null) {
                drawArc(color, START, SWEEP * progress, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score?.toString() ?: "--",
                style = MaterialTheme.typography.displaySmall,
                color = if (score != null) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun scoreColor(score: Int?): Color {
    val status = SysCleanTheme.statusColors
    return when {
        score == null -> MaterialTheme.colorScheme.outline
        score >= 80 -> status.good
        score >= 50 -> status.warning
        else -> status.critical
    }
}
