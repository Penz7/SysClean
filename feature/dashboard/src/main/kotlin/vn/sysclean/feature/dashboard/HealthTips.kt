package vn.sysclean.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import vn.sysclean.core.common.format.formatBytes
import vn.sysclean.core.designsystem.component.SectionCard
import vn.sysclean.core.domain.HealthFactor
import vn.sysclean.core.domain.HealthScore
import vn.sysclean.core.domain.HealthScoreCalculator
import vn.sysclean.core.designsystem.component.Status
import vn.sysclean.core.designsystem.component.StatusChip
import vn.sysclean.core.designsystem.theme.SysCleanTheme
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.ScanState
import vn.sysclean.core.ui.label

private class Tip(
    val gain: Int,
    val title: String,
    val body: String,
    val actions: List<Pair<String, () -> Unit>>,
)

/**
 * Turns the health score into concrete steps, biggest gain first. Every action leads to a
 * real fix in the app; there is deliberately no fake "boost" button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HealthTipsCard(
    state: DashboardUiState,
    score: HealthScore,
    onStartScan: () -> Unit,
    onQuickClean: () -> Unit,
    onOpenCategory: (JunkCategory) -> Unit,
    onOpenUnusedApps: () -> Unit,
) {
    val report = (state.scan as? ScanState.Finished)?.report
    val categories = report?.groups?.map { it.category }?.toSet().orEmpty()
    val tips = buildList {
        val primary = state.storage?.primary
        val storageGain = score.potentialGain(HealthFactor.STORAGE)
        if (primary != null && storageGain > 0) {
            val freePercent = (primary.freeBytes * 100 / primary.totalBytes).toInt()
            val needed = (primary.totalBytes * HealthScoreCalculator.HEALTHY_FREE_RATIO).toLong() - primary.freeBytes
            add(
                Tip(
                    gain = storageGain,
                    title = stringResource(R.string.dashboard_tip_storage_title),
                    body = stringResource(R.string.dashboard_tip_storage_body, freePercent, formatBytes(needed)),
                    actions = buildList {
                        if (JunkCategory.LARGE_FILES in categories) {
                            add(stringResource(R.string.dashboard_tip_action_large) to { onOpenCategory(JunkCategory.LARGE_FILES) })
                        }
                        add(stringResource(R.string.dashboard_tip_action_unused) to onOpenUnusedApps)
                    },
                ),
            )
        }
        val lastJunk = state.health.safeJunkBytes
        if (report == null && lastJunk == null) {
            add(
                Tip(
                    gain = 0,
                    title = stringResource(R.string.dashboard_tip_scan_title),
                    body = stringResource(R.string.dashboard_tip_scan_body),
                    actions = listOf(stringResource(R.string.dashboard_scan_start) to onStartScan),
                ),
            )
        } else if (report == null) {
            // Junk is known from an earlier session; a fresh scan is needed to clean it.
            val junkGain = score.potentialGain(HealthFactor.JUNK)
            if (junkGain > 0) {
                add(
                    Tip(
                        gain = junkGain,
                        title = stringResource(R.string.dashboard_tip_junk_title),
                        body = stringResource(R.string.dashboard_tip_junk_previous, formatBytes(lastJunk ?: 0)),
                        actions = listOf(stringResource(R.string.dashboard_scan_start) to onStartScan),
                    ),
                )
            }
        } else {
            val junkGain = score.potentialGain(HealthFactor.JUNK)
            if (junkGain > 0) {
                val cache = report.groups.firstOrNull { it.category == JunkCategory.APP_CACHE }?.totalBytes ?: 0
                add(
                    Tip(
                        gain = junkGain,
                        title = stringResource(R.string.dashboard_tip_junk_title),
                        body = stringResource(R.string.dashboard_tip_junk_body, formatBytes(report.safeBytes), formatBytes(cache)),
                        actions = buildList {
                            if (state.quickCleanBytes > 0) {
                                add(stringResource(R.string.dashboard_quick_clean, formatBytes(state.quickCleanBytes)) to onQuickClean)
                            }
                            if (cache > 0) {
                                add(stringResource(R.string.dashboard_tip_action_cache) to { onOpenCategory(JunkCategory.APP_CACHE) })
                            }
                        },
                    ),
                )
            }
        }
        val memory = state.memory
        val memoryGain = score.potentialGain(HealthFactor.MEMORY)
        if (memory != null && memoryGain > 0) {
            add(
                Tip(
                    gain = memoryGain,
                    title = stringResource(R.string.dashboard_tip_memory_title),
                    body = stringResource(R.string.dashboard_tip_memory_body, (memory.availableBytes * 100 / memory.totalBytes).toInt()),
                    actions = emptyList(),
                ),
            )
        }
        val battery = state.battery
        val batteryGain = score.potentialGain(HealthFactor.BATTERY)
        if (battery != null && batteryGain > 0) {
            add(
                Tip(
                    gain = batteryGain,
                    title = stringResource(R.string.dashboard_tip_battery_title),
                    body = stringResource(R.string.dashboard_tip_battery_body, battery.health.label()),
                    actions = emptyList(),
                ),
            )
        }
    }.sortedByDescending { it.gain }

    SectionCard(title = stringResource(R.string.dashboard_tips_title), icon = Icons.Outlined.TipsAndUpdates) {
        if (tips.isEmpty()) {
            Text(stringResource(R.string.dashboard_tip_all_good), style = MaterialTheme.typography.bodyMedium)
        }
        tips.forEachIndexed { index, tip ->
            if (index > 0) HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tip.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (tip.gain > 0) StatusChip(stringResource(R.string.dashboard_tip_gain, tip.gain), Status.GOOD)
                }
                Text(tip.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (tip.actions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.sm)) {
                        tip.actions.forEach { (label, action) ->
                            AssistChip(onClick = action, label = { Text(label) })
                        }
                    }
                }
            }
        }
    }
}
