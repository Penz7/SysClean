package vn.sysclean.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import vn.sysclean.core.designsystem.theme.SysCleanTheme

@Composable
fun UsageBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = usageColor(fraction),
) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

/** Green while there is headroom, amber past 75%, red past 90%. */
@Composable
fun usageColor(fraction: Float): Color {
    val status = SysCleanTheme.statusColors
    return when {
        fraction >= 0.9f -> status.critical
        fraction >= 0.75f -> status.warning
        else -> MaterialTheme.colorScheme.primary
    }
}

data class BarSegment(val label: String, val value: Long, val color: Color)

/** A stacked bar whose segments are proportional to [total]; the remainder is drawn as track. */
@Composable
fun SegmentedBar(
    segments: List<BarSegment>,
    total: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (total <= 0) return@Row
        segments.filter { it.value > 0 }.forEach { segment ->
            val weight = (segment.value.toFloat() / total).coerceIn(0.0001f, 1f)
            Box(
                Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(segment.color),
            )
        }
        val rest = total - segments.sumOf { it.value.coerceAtLeast(0) }
        if (rest > 0) Spacer(Modifier.weight(rest.toFloat() / total))
    }
}

@Composable
fun LegendItem(
    color: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.sm),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Categorical colors for storage segments, picked from the theme so they adapt to dark mode. */
@Composable
fun categoryColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val status = SysCleanTheme.statusColors
    return listOf(scheme.primary, scheme.tertiary, status.warning, Color(0xFF9C5BD6), scheme.secondary, scheme.outline)
}

@Composable
fun LabeledUsage(
    title: String,
    detail: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = usageColor(fraction),
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.xs)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(SysCleanTheme.spacing.sm))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        UsageBar(fraction, color = color)
    }
}
