package vn.sysclean.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.sysclean.core.designsystem.theme.SysCleanTheme

enum class Status { GOOD, WARNING, CRITICAL, NEUTRAL }

@Composable
fun StatusChip(
    text: String,
    status: Status,
    modifier: Modifier = Modifier,
) {
    val colors = SysCleanTheme.statusColors
    val (container, content) = when (status) {
        Status.GOOD -> colors.good to colors.onGood
        Status.WARNING -> colors.warning to colors.onWarning
        Status.CRITICAL -> colors.critical to colors.onCritical
        Status.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, contentColor = content, shape = CircleShape, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
