package vn.sysclean.feature.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import vn.sysclean.core.privilege.startActivityWithFallback
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import vn.sysclean.core.ui.R as UiR
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.sysclean.core.common.format.formatBytes
import vn.sysclean.core.designsystem.component.Banner
import vn.sysclean.core.designsystem.component.BarSegment
import vn.sysclean.core.designsystem.component.HealthGauge
import vn.sysclean.core.designsystem.component.LabeledUsage
import vn.sysclean.core.designsystem.component.LegendItem
import vn.sysclean.core.designsystem.component.SectionCard
import vn.sysclean.core.designsystem.component.SegmentedBar
import vn.sysclean.core.designsystem.component.StatTile
import vn.sysclean.core.designsystem.component.Status
import vn.sysclean.core.designsystem.component.StatusChip
import vn.sysclean.core.designsystem.component.categoryColors
import vn.sysclean.core.designsystem.theme.SysCleanTheme
import vn.sysclean.core.model.AccessLevel
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.JunkGroup
import vn.sysclean.core.model.JunkReport
import vn.sysclean.core.model.ScanState
import vn.sysclean.core.model.StorageInfo
import vn.sysclean.core.ui.description
import vn.sysclean.core.ui.icon
import vn.sysclean.core.ui.label
import vn.sysclean.core.ui.usedOfTotal
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun DashboardScreen(
    onOpenSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenCategory: (JunkCategory) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenUnusedApps: () -> Unit,
    onOpenBloatware: () -> Unit,
    onOpenShizukuSetup: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.cleanResults.collect { result ->
            val outcome = snackbar.showSnackbar(
                message = context.getString(R.string.dashboard_clean_result, result.cleanedCount, formatBytes(result.cleanedBytes)),
                actionLabel = context.getString(R.string.dashboard_undo).takeIf { result.trashIds.isNotEmpty() },
                duration = SnackbarDuration.Long,
            )
            if (outcome == SnackbarResult.ActionPerformed) viewModel.undo(result)
        }
    }
    DashboardContent(
        state = state,
        snackbar = snackbar,
        onStartScan = viewModel::startScan,
        onCancelScan = viewModel::cancelScan,
        onQuickClean = viewModel::quickClean,
        onOpenSettings = onOpenSettings,
        onOpenMemory = onOpenMemory,
        onOpenBattery = onOpenBattery,
        onOpenCategory = onOpenCategory,
        onOpenTrash = onOpenTrash,
        onOpenUnusedApps = onOpenUnusedApps,
        onOpenBloatware = onOpenBloatware,
        onOpenShizuku = { viewModel.shizukuLaunchIntent()?.let { context.startActivityWithFallback(it, null) } },
        onOpenShizukuSetup = onOpenShizukuSetup,
        onForgetShizuku = viewModel::forgetShizuku,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardContent(
    state: DashboardUiState,
    snackbar: SnackbarHostState,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onQuickClean: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenCategory: (JunkCategory) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenUnusedApps: () -> Unit,
    onOpenBloatware: () -> Unit,
    onOpenShizuku: () -> Unit = {},
    onOpenShizukuSetup: () -> Unit = {},
    onForgetShizuku: () -> Unit = {},
) {
    val spacing = SysCleanTheme.spacing
    // One confirmation for quick clean, reachable from both the scan card and the tips.
    var confirmQuickClean by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.dashboard_title))
                        if (state.deviceName.isNotEmpty()) {
                            Text(
                                state.deviceName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTrash) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.dashboard_trash))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.lg,
                end = spacing.lg,
                top = padding.calculateTopPadding() + spacing.sm,
                bottom = padding.calculateBottomPadding() + spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item { HealthCard(state) }
            state.healthScore?.let { score ->
                item {
                    HealthTipsCard(
                        state = state,
                        score = score,
                        onStartScan = onStartScan,
                        onQuickClean = { confirmQuickClean = true },
                        onOpenCategory = onOpenCategory,
                        onOpenUnusedApps = onOpenUnusedApps,
                    )
                }
            }
            if (!state.access.hasRequiredPermissions) {
                item {
                    Banner(
                        icon = Icons.Outlined.Lock,
                        title = stringResource(R.string.dashboard_permissions_title),
                        body = stringResource(R.string.dashboard_permissions_body),
                        actionLabel = stringResource(R.string.dashboard_permissions_action),
                        onAction = onOpenSettings,
                    )
                }
            }
            if (state.access.shizukuStopped) {
                item { ShizukuStoppedCard(onOpenShizuku, onOpenShizukuSetup, onForgetShizuku) }
            }
            item { QuickStats(state, onOpenMemory, onOpenBattery) }
            state.storage?.let { storage -> item { StorageCard(storage) } }
            item {
                ScanCard(
                    state = state,
                    onStartScan = onStartScan,
                    onCancelScan = onCancelScan,
                    onQuickClean = { confirmQuickClean = true },
                    onOpenCategory = onOpenCategory,
                )
            }
            item {
                AdvancedModeCard(
                    level = state.access.activeLevel,
                    onOpenSettings = onOpenSettings,
                    onOpenBloatware = onOpenBloatware,
                    onOpenAppCache = { onOpenCategory(JunkCategory.APP_CACHE) },
                    hasAppCache = (state.scan as? ScanState.Finished)?.report?.groups?.any { it.category == JunkCategory.APP_CACHE } == true,
                )
            }
        }
    }

    if (confirmQuickClean) {
        AlertDialog(
            onDismissRequest = { confirmQuickClean = false },
            title = { Text(stringResource(R.string.dashboard_quick_clean_title)) },
            text = {
                Text(stringResource(R.string.dashboard_quick_clean_body, formatBytes(state.quickCleanBytes), state.retentionDays))
            },
            confirmButton = {
                Button(onClick = {
                    confirmQuickClean = false
                    onQuickClean()
                }) { Text(stringResource(R.string.dashboard_quick_clean_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmQuickClean = false }) { Text(stringResource(R.string.dashboard_cancel)) }
            },
        )
    }
}

@Composable
private fun HealthCard(state: DashboardUiState) {
    val health = state.healthScore
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HealthGauge(score = health?.score, caption = stringResource(R.string.dashboard_health_caption), size = 128.dp)
            Spacer(Modifier.width(SysCleanTheme.spacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.xs)) {
                Text(stringResource(R.string.dashboard_health), style = MaterialTheme.typography.labelLarge)
                if (health != null) {
                    Text(
                        stringResource(
                            when {
                                health.score >= 80 -> R.string.dashboard_health_good
                                health.score >= 50 -> R.string.dashboard_health_fair
                                else -> R.string.dashboard_health_poor
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                // The score already counts junk from the last scan, even one from an earlier session.
                if (state.health.safeJunkBytes == null) {
                    Text(
                        stringResource(R.string.dashboard_health_hint_scan),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickStats(state: DashboardUiState, onOpenMemory: () -> Unit, onOpenBattery: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.md)) {
        val memory = state.memory
        StatTile(
            icon = Icons.Outlined.Memory,
            label = stringResource(R.string.dashboard_ram),
            value = memory?.let { "${(it.usedFraction * 100).toInt()}%" } ?: "--",
            supporting = memory?.let { usedOfTotal(formatBytes(it.usedBytes), formatBytes(it.totalBytes)) },
            onClick = onOpenMemory,
            modifier = Modifier.weight(1f),
        )
        val battery = state.battery
        StatTile(
            icon = Icons.Outlined.BatteryStd,
            label = stringResource(R.string.dashboard_battery),
            value = battery?.let { "${it.levelPercent}%" } ?: "--",
            supporting = battery?.let { b ->
                listOfNotNull(b.status.label(), b.temperatureCelsius?.let { "%.1f°C".format(it) }).joinToString(" · ")
            },
            onClick = onOpenBattery,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StorageCard(storage: StorageInfo) {
    SectionCard(title = stringResource(R.string.dashboard_storage), icon = Icons.Outlined.Storage) {
        storage.volumes.forEach { volume ->
            LabeledUsage(
                title = volume.label,
                detail = usedOfTotal(formatBytes(volume.usedBytes), formatBytes(volume.totalBytes)),
                fraction = volume.usedFraction,
            )
        }
        val primary = storage.primary
        val breakdown = storage.breakdown
        if (primary != null && breakdown != null) {
            val colors = categoryColors()
            val segments = listOf(
                BarSegment(stringResource(R.string.dashboard_storage_apps), breakdown.appsBytes, colors[0]),
                BarSegment(stringResource(R.string.dashboard_storage_images), breakdown.imagesBytes, colors[1]),
                BarSegment(stringResource(R.string.dashboard_storage_videos), breakdown.videosBytes, colors[2]),
                BarSegment(stringResource(R.string.dashboard_storage_audio), breakdown.audioBytes, colors[3]),
                BarSegment(stringResource(R.string.dashboard_storage_other), breakdown.otherBytes, colors[4]),
                BarSegment(stringResource(R.string.dashboard_storage_system), breakdown.systemBytes, colors[5]),
            )
            HorizontalDivider()
            SegmentedBar(segments, total = primary.totalBytes)
            segments.forEach { LegendItem(it.color, it.label, formatBytes(it.value)) }
            Text(
                stringResource(R.string.dashboard_free, formatBytes(primary.freeBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.dashboard_storage_breakdown_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScanCard(
    state: DashboardUiState,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onQuickClean: () -> Unit,
    onOpenCategory: (JunkCategory) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.dashboard_scan_title),
        modifier = Modifier.animateContentSize(),
    ) {
        when (val scan = state.scan) {
            ScanState.Idle -> {
                Text(stringResource(R.string.dashboard_scan_idle), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dashboard_scan_start))
                }
            }
            is ScanState.Running -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    stringResource(
                        R.string.dashboard_scan_running,
                        NumberFormat.getIntegerInstance().format(scan.progress.filesScanned),
                        formatBytes(scan.progress.bytesScanned),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    scan.progress.currentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                OutlinedButton(onClick = onCancelScan, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dashboard_scan_cancel))
                }
            }
            is ScanState.Finished -> ScanReport(state, scan.report, onStartScan, onQuickClean, onOpenCategory)
            is ScanState.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(SysCleanTheme.spacing.sm))
                    Text(stringResource(R.string.dashboard_scan_failed, scan.message))
                }
                Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dashboard_scan_again))
                }
            }
        }
    }
}

@Composable
private fun ScanReport(
    state: DashboardUiState,
    report: JunkReport,
    onStartScan: () -> Unit,
    onQuickClean: () -> Unit,
    onOpenCategory: (JunkCategory) -> Unit,
) {
    val spacing = SysCleanTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        Summary(stringResource(R.string.dashboard_scan_safe), formatBytes(report.safeBytes), Modifier.weight(1f))
        Summary(stringResource(R.string.dashboard_scan_review), formatBytes(report.reviewBytes), Modifier.weight(1f))
    }
    Text(
        stringResource(
            R.string.dashboard_scan_summary,
            NumberFormat.getIntegerInstance().format(report.filesScanned),
            String.format(Locale.getDefault(), "%.1f", report.durationMillis / 1000.0),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (report.skipped.isNotEmpty()) {
        StatusChip(stringResource(R.string.dashboard_scan_skipped), Status.WARNING)
    }

    val progress = state.cleaning
    when {
        progress != null -> {
            LinearProgressIndicator(
                progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.dashboard_cleaning, progress.done, progress.total), style = MaterialTheme.typography.bodySmall)
        }
        state.quickCleanBytes > 0 -> Button(onClick = onQuickClean, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(spacing.sm))
            Text(stringResource(R.string.dashboard_quick_clean, formatBytes(state.quickCleanBytes)))
        }
    }

    val byCategory = report.groups.associateBy { it.category }
    listOf(true, false).forEach { safe ->
        val groups = JunkCategory.entries.filter { it.isSafeToClean == safe }.mapNotNull { byCategory[it] }
        if (groups.isNotEmpty()) {
            HorizontalDivider()
            Text(
                stringResource(if (safe) R.string.dashboard_scan_safe else R.string.dashboard_scan_review),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            groups.forEach { JunkGroupRow(it, enabled = progress == null) { onOpenCategory(it.category) } }
        }
    }
    if (report.groups.isEmpty()) {
        Text(stringResource(R.string.dashboard_scan_nothing), style = MaterialTheme.typography.bodyMedium)
    }

    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Info, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(spacing.sm))
        Text(
            stringResource(R.string.dashboard_trash_note, state.retentionDays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedButton(onClick = onStartScan, enabled = progress == null, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.dashboard_scan_again))
    }

}

@Composable
private fun Summary(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun JunkGroupRow(group: JunkGroup, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = SysCleanTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(group.category.icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(SysCleanTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            Text(group.category.label(), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${pluralStringResource(UiR.plurals.ui_items, group.itemCount, group.itemCount)} · ${group.category.description()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (group.category != JunkCategory.EMPTY_FOLDERS) {
            Spacer(Modifier.width(SysCleanTheme.spacing.sm))
            Text(formatBytes(group.totalBytes), style = MaterialTheme.typography.labelLarge)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedModeCard(
    level: AccessLevel,
    onOpenSettings: () -> Unit,
    onOpenBloatware: () -> Unit,
    onOpenAppCache: () -> Unit,
    hasAppCache: Boolean,
) {
    val active = level != AccessLevel.NORMAL
    SectionCard(
        title = stringResource(if (active) R.string.dashboard_advanced_active_title else R.string.dashboard_advanced_title),
        icon = Icons.Outlined.AdminPanelSettings,
    ) {
        if (active) {
            Text(stringResource(R.string.dashboard_advanced_ready, level.label()), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.sm)) {
                AssistChip(onClick = onOpenBloatware, label = { Text(stringResource(R.string.dashboard_advanced_bloat)) })
                if (hasAppCache) {
                    AssistChip(onClick = onOpenAppCache, label = { Text(stringResource(R.string.dashboard_advanced_cache)) })
                }
            }
        } else {
            Text(stringResource(R.string.dashboard_advanced_body), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.dashboard_advanced_action)) }
        }
    }
}


/**
 * Without root Shizuku stops at every restart, and the user rarely notices until a deep-clean
 * feature quietly disappears. Shown only to people who had it working.
 */
@Composable
private fun ShizukuStoppedCard(onOpenShizuku: () -> Unit, onOpenSetup: () -> Unit, onForget: () -> Unit) {
    Column {
        Banner(
            icon = Icons.Outlined.PowerSettingsNew,
            title = stringResource(R.string.dashboard_shizuku_stopped_title),
            body = stringResource(R.string.dashboard_shizuku_stopped_body),
            actionLabel = stringResource(R.string.dashboard_shizuku_stopped_action),
            onAction = onOpenShizuku,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onOpenSetup) { Text(stringResource(R.string.dashboard_shizuku_stopped_guide)) }
            TextButton(onClick = onForget) { Text(stringResource(R.string.dashboard_shizuku_stopped_forget)) }
        }
    }
}
