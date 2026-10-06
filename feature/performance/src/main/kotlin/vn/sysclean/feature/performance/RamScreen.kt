package vn.sysclean.feature.performance

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.sysclean.core.common.format.formatBytes
import vn.sysclean.core.designsystem.component.BarSegment
import vn.sysclean.core.designsystem.component.LegendItem
import vn.sysclean.core.designsystem.component.LoadingContent
import vn.sysclean.core.designsystem.component.SectionCard
import vn.sysclean.core.designsystem.component.SegmentedBar
import vn.sysclean.core.designsystem.component.Status
import vn.sysclean.core.designsystem.component.StatusChip
import vn.sysclean.core.designsystem.component.categoryColors
import vn.sysclean.core.designsystem.theme.SysCleanTheme
import vn.sysclean.core.performance.AppKind
import vn.sysclean.core.performance.AppMemory
import vn.sysclean.core.performance.MemoryGroup
import vn.sysclean.core.performance.ReducedApp
import vn.sysclean.core.performance.ServiceImpact

private fun kb(value: Long) = formatBytes(value * 1024)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RamScreen(onBack: () -> Unit, viewModel: RamViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmDisable by remember { mutableStateOf<AppMemory?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is RamEvent.Slept -> {
                    val result = snackbar.showSnackbar(
                        context.getString(R.string.ram_slept, event.app.label),
                        actionLabel = context.getString(R.string.ram_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.wake(event.app.packageName, event.app.label)
                }
                is RamEvent.Woken -> snackbar.showSnackbar(context.getString(R.string.ram_woken, event.label))
                is RamEvent.Disabled -> snackbar.showSnackbar(context.getString(R.string.ram_disabled, event.label))
                is RamEvent.Enabled -> snackbar.showSnackbar(context.getString(R.string.ram_enabled, event.label))
                is RamEvent.Failed -> snackbar.showSnackbar(context.getString(R.string.ram_failed, event.label))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.ram_back)) } },
                title = { Text(stringResource(R.string.ram_title)) },
                actions = {
                    IconButton(onClick = viewModel::reload, enabled = !state.loading) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.perf_refresh))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val overview = state.overview
        if (overview == null) {
            Column(Modifier.padding(padding)) {
                Text(
                    stringResource(R.string.ram_measuring),
                    modifier = Modifier.padding(SysCleanTheme.spacing.lg),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LoadingContent()
            }
            return@Scaffold
        }
        val spacing = SysCleanTheme.spacing
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
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { SummaryCard(state) }
            item { SectionTitle(stringResource(R.string.ram_reducible), stringResource(R.string.ram_reducible_hint)) }
            if (state.reducible.isEmpty()) {
                item { Text(stringResource(R.string.ram_nothing_reducible), style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.reducible, key = { "r-" + it.packageName }) { app ->
                AppRow(app, busy = app.packageName in state.busy) {
                    if (app.sleepable) {
                        FilledTonalButton(onClick = { viewModel.deepSleep(app) }) { Text(stringResource(R.string.ram_deep_sleep)) }
                    } else {
                        OutlinedButton(onClick = { confirmDisable = app }) { Text(stringResource(R.string.ram_disable)) }
                    }
                }
            }
            if (state.suggestions.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.ram_suggest), stringResource(R.string.ram_suggest_hint)) }
                item {
                    SectionCard {
                        Text(
                            state.suggestions.joinToString(" · ") { it.second },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = viewModel::sleepSuggestions, enabled = state.busy.isEmpty()) {
                            Text(stringResource(R.string.ram_sleep_all, state.suggestions.size))
                        }
                    }
                }
            }
            if (state.reduced.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.ram_reduced), null) }
                items(state.reduced, key = { "d-" + it.packageName }) { app ->
                    ReducedRow(app, busy = app.packageName in state.busy) {
                        TextButton(onClick = {
                            if (app.disabled) viewModel.enable(app.packageName, app.label) else viewModel.wake(app.packageName, app.label)
                        }) { Text(stringResource(if (app.disabled) R.string.ram_enable else R.string.ram_wake)) }
                    }
                }
            }
            item { LockedSection(state.locked) }
            item {
                Text(
                    stringResource(R.string.ram_cache_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    confirmDisable?.let { app ->
        AlertDialog(
            onDismissRequest = { confirmDisable = null },
            title = { Text(stringResource(R.string.ram_disable_title, app.label)) },
            text = { Text(stringResource(R.string.ram_disable_body, impactText(app.impact))) },
            confirmButton = {
                Button(onClick = {
                    confirmDisable = null
                    viewModel.disable(app)
                }) { Text(stringResource(R.string.ram_disable)) }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = null }) { Text(stringResource(R.string.ram_cancel)) } },
        )
    }
}

@Composable
private fun SummaryCard(state: RamUiState) {
    val overview = state.overview ?: return
    val colors = categoryColors()
    val segments = listOf(
        BarSegment(stringResource(R.string.ram_part_system), overview.systemKb, MaterialTheme.colorScheme.outline),
        BarSegment(stringResource(R.string.ram_part_active), state.groupKb(MemoryGroup.ACTIVE) + state.groupKb(MemoryGroup.SYSTEM), colors[0]),
        BarSegment(stringResource(R.string.ram_part_background), state.groupKb(MemoryGroup.BACKGROUND), colors[2]),
        BarSegment(stringResource(R.string.ram_part_cached), state.groupKb(MemoryGroup.CACHED), colors[1]),
    )
    SectionCard(title = stringResource(R.string.ram_title), icon = Icons.Outlined.Memory) {
        Text(
            stringResource(R.string.ram_summary, kb(overview.usedKb), kb(overview.totalKb), kb(overview.freeKb)),
            style = MaterialTheme.typography.titleSmall,
        )
        SegmentedBar(segments, total = overview.totalKb)
        segments.forEach { LegendItem(it.color, it.label, kb(it.value)) }
        if (overview.swapUsedKb > 0) {
            Text(
                stringResource(R.string.ram_zram, kb(overview.swapUsedKb), kb(overview.zramPhysicalKb)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, hint: String?) {
    Column(Modifier.padding(top = SysCleanTheme.spacing.sm)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        hint?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppRow(app: AppMemory, busy: Boolean, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(kb(app.pssKb), style = MaterialTheme.typography.labelLarge)
                StatusChip(groupLabel(app.group), Status.NEUTRAL)
            }
            when {
                app.impact != null -> impactText(app.impact)
                app.kind == AppKind.IDLE_PRELOAD -> stringResource(R.string.ram_idle_preload)
                else -> null
            }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(SysCleanTheme.spacing.sm))
        if (busy) CircularProgressIndicator(Modifier.size(24.dp)) else action()
    }
}

@Composable
private fun ReducedRow(app: ReducedApp, busy: Boolean, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            StatusChip(stringResource(if (app.disabled) R.string.ram_state_disabled else R.string.ram_state_asleep), Status.NEUTRAL)
        }
        Spacer(Modifier.width(SysCleanTheme.spacing.sm))
        if (busy) CircularProgressIndicator(Modifier.size(24.dp)) else action()
    }
}

@Composable
private fun LockedSection(apps: List<AppMemory>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = SysCleanTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.ram_locked) + " · " + kb(apps.sumOf { it.pssKb }),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
        if (expanded) {
            apps.forEach { app ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(app.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(kb(app.pssKb), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun groupLabel(group: MemoryGroup) = stringResource(
    when (group) {
        MemoryGroup.ACTIVE -> R.string.ram_group_active
        MemoryGroup.BACKGROUND -> R.string.ram_group_background
        MemoryGroup.CACHED -> R.string.ram_group_cached
        MemoryGroup.SYSTEM -> R.string.ram_group_system
    },
)

@Composable
private fun impactText(impact: ServiceImpact?) = stringResource(
    when (impact) {
        ServiceImpact.GOOGLE_ASSISTANT -> R.string.ram_impact_google
        ServiceImpact.CUSTOMIZATION -> R.string.ram_impact_customization
        ServiceImpact.ROUTINES -> R.string.ram_impact_routines
        ServiceImpact.VOICE_WAKE -> R.string.ram_impact_voice
        ServiceImpact.NEARBY_ACCESSORIES -> R.string.ram_impact_nearby
        ServiceImpact.VENDOR_ANALYTICS, null -> R.string.ram_impact_analytics
    },
)
