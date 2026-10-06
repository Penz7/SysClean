package vn.sysclean.feature.performance

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.sysclean.core.common.format.formatBytes
import vn.sysclean.core.designsystem.component.Banner
import vn.sysclean.core.designsystem.component.LoadingContent
import vn.sysclean.core.designsystem.component.SectionCard
import vn.sysclean.core.designsystem.component.Status
import vn.sysclean.core.designsystem.component.StatusChip
import vn.sysclean.core.designsystem.theme.SysCleanTheme
import vn.sysclean.core.model.ThermalStatus
import vn.sysclean.core.performance.FrameStats
import vn.sysclean.core.performance.OptimizeProgress
import vn.sysclean.core.performance.OptimizeResult
import vn.sysclean.core.performance.OptimizeStep
import vn.sysclean.core.performance.PerformanceReport
import vn.sysclean.core.ui.label
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PerformanceScreen(
    onOpenRam: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: PerformanceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = SysCleanTheme.spacing
    var confirmRestart by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.perf_title)) },
                actions = {
                    IconButton(onClick = viewModel::measure, enabled = !state.measuring && state.progress == null) {
                        Icon(Icons.Outlined.Refresh, stringResource(R.string.perf_refresh))
                    }
                },
            )
        },
    ) { padding ->
        val report = state.report
        if (report == null) {
            Column(Modifier.padding(padding)) {
                LoadingContent()
            }
            return@Scaffold
        }
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
            if (state.measuring) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!state.privileged) {
                item {
                    Banner(
                        icon = Icons.Outlined.Bolt,
                        title = stringResource(R.string.perf_needs_privilege_title),
                        body = stringResource(R.string.perf_needs_privilege_body),
                        actionLabel = stringResource(R.string.perf_open_settings),
                        onAction = onOpenSettings,
                    )
                }
            }
            report.launcherJank?.let { jank -> item { SmoothnessCard(jank, state, report) } }
            item { DiagnosisCard(report, state.privileged, onRestart = { confirmRestart = true }, onFastAnimations = viewModel::setFastAnimations) }
            if (state.privileged) {
                item {
                    OptimizeCard(
                        report = report,
                        steps = state.steps,
                        progress = state.progress,
                        result = state.result,
                        onToggle = viewModel::toggleStep,
                        onOptimize = viewModel::optimize,
                    )
                }
            }
            item {
                SectionCard(title = stringResource(R.string.perf_ram_manager), icon = Icons.Outlined.Memory) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = if (state.privileged) onOpenRam else onOpenSettings),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.perf_ram_manager_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
                    }
                }
            }
        }
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(stringResource(R.string.perf_restart_title)) },
            text = { Text(stringResource(R.string.perf_restart_body)) },
            confirmButton = {
                Button(onClick = {
                    confirmRestart = false
                    viewModel.restart()
                }) { Text(stringResource(R.string.perf_restart)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text(stringResource(R.string.ram_cancel)) } },
        )
    }
}

private fun percent(value: Double) = String.format(Locale.getDefault(), "%.1f%%", value)

/** Under ~3% stutter feels smooth, above ~8% people notice lag. */
private fun jankStatus(percent: Double) = when {
    percent <= 3 -> Status.GOOD
    percent <= 8 -> Status.WARNING
    else -> Status.CRITICAL
}

@Composable
private fun SmoothnessCard(jank: FrameStats, state: PerformanceUiState, report: PerformanceReport) {
    SectionCard(title = stringResource(R.string.perf_smoothness), icon = Icons.Outlined.Speed) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.perf_jank, percent(jank.jankyPercent)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            val status = jankStatus(jank.jankyPercent)
            StatusChip(
                stringResource(
                    when (status) {
                        Status.GOOD -> R.string.perf_jank_good
                        Status.WARNING -> R.string.perf_jank_fair
                        else -> R.string.perf_jank_poor
                    },
                ),
                status,
            )
        }
        if (jank.p50Ms != null && jank.p99Ms != null) {
            Text(
                stringResource(R.string.perf_jank_detail, jank.p50Ms!!, jank.p99Ms!!),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val baseline = state.baseline ?: return@SectionCard
        HorizontalDivider()
        val before = baseline.jankPercent
        if (before != null && jank.totalFrames >= MIN_FRAMES_FOR_COMPARISON) {
            Text(stringResource(R.string.perf_compare, percent(before), percent(jank.jankyPercent)), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                stringResource(R.string.perf_compare_wait),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.perf_ram_compare, formatBytes(baseline.ramAvailableBytes), formatBytes(report.ramAvailableBytes)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val MIN_FRAMES_FOR_COMPARISON = 300

@Composable
private fun DiagnosisCard(
    report: PerformanceReport,
    privileged: Boolean,
    onRestart: () -> Unit,
    onFastAnimations: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.perf_diagnosis), icon = Icons.Outlined.MonitorHeart) {
        Diagnosis(
            label = stringResource(R.string.perf_uptime),
            value = pluralStringResource(R.plurals.perf_days, report.uptimeDays, report.uptimeDays),
            status = if (report.shouldRestart) Status.WARNING else Status.GOOD,
        )
        if (report.shouldRestart) {
            Hint(
                stringResource(
                    R.string.perf_uptime_hint,
                    report.systemServerKb?.let { stringResource(R.string.perf_system_server, formatBytes(it * 1024)) }.orEmpty(),
                ),
            )
            if (privileged) OutlinedButton(onClick = onRestart) { Text(stringResource(R.string.perf_restart)) }
        }
        HorizontalDivider()
        val freeRam = report.ramAvailableBytes.toFloat() / report.ramTotalBytes
        Diagnosis(
            label = stringResource(R.string.perf_free_ram),
            value = "${formatBytes(report.ramAvailableBytes)} / ${formatBytes(report.ramTotalBytes)}",
            status = if (freeRam < 0.2f) Status.WARNING else Status.GOOD,
        )
        report.memoryPressure?.let {
            Diagnosis(
                label = stringResource(R.string.perf_memory_pressure),
                value = stringResource(R.string.perf_pressure_value, percent(it.someAvg60)),
                status = if (it.someAvg60 > 5) Status.WARNING else Status.GOOD,
            )
        }
        report.ioPressure?.let {
            Diagnosis(
                label = stringResource(R.string.perf_io_pressure),
                value = stringResource(R.string.perf_pressure_value, percent(it.someAvg60)),
                status = if (it.someAvg60 > 5) Status.WARNING else Status.GOOD,
            )
        }
        report.storageFreeFraction?.let {
            Diagnosis(
                label = stringResource(R.string.perf_free_storage),
                value = "${(it * 100).toInt()}%",
                status = if (it < 0.15f) Status.CRITICAL else if (it < 0.25f) Status.WARNING else Status.GOOD,
            )
        }
        report.unoptimizedApps?.let {
            Diagnosis(
                label = stringResource(R.string.perf_unoptimized),
                value = it.size.toString(),
                status = if (it.isEmpty()) Status.GOOD else Status.WARNING,
            )
        }
        report.deepSleepCandidates?.let {
            Diagnosis(
                label = stringResource(R.string.perf_sleep_candidates),
                value = it.size.toString(),
                status = if (it.isEmpty()) Status.GOOD else Status.NEUTRAL,
            )
        }
        Diagnosis(
            label = stringResource(R.string.perf_power_save),
            value = stringResource(if (report.powerSaveMode) R.string.perf_on else R.string.perf_off),
            status = if (report.powerSaveMode) Status.WARNING else Status.GOOD,
        )
        if (report.powerSaveMode) {
            Hint(stringResource(R.string.perf_power_save_hint))
            TextButton(onClick = { context.openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS) }) {
                Text(stringResource(R.string.perf_open_system_settings))
            }
        }
        Diagnosis(
            label = stringResource(R.string.perf_accessibility),
            value = report.accessibilityServices.size.toString(),
            status = if (report.accessibilityServices.isEmpty()) Status.GOOD else Status.NEUTRAL,
        )
        if (report.accessibilityServices.isNotEmpty()) {
            Hint(stringResource(R.string.perf_accessibility_hint, report.accessibilityServices.joinToString()))
            TextButton(onClick = { context.openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) {
                Text(stringResource(R.string.perf_open_system_settings))
            }
        }
        if (report.dontKeepActivities) {
            Diagnosis(
                label = stringResource(R.string.perf_dont_keep),
                value = stringResource(R.string.perf_on),
                status = Status.CRITICAL,
            )
            Hint(stringResource(R.string.perf_dont_keep_hint))
            TextButton(onClick = { context.openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }) {
                Text(stringResource(R.string.perf_open_dev_options))
            }
        }
        Diagnosis(
            label = stringResource(R.string.perf_thermal),
            value = report.thermal.takeIf { it != ThermalStatus.UNKNOWN }?.label() ?: stringResource(R.string.perf_ok),
            status = if (report.thermal.ordinal >= ThermalStatus.MODERATE.ordinal && report.thermal != ThermalStatus.UNKNOWN) Status.WARNING else Status.GOOD,
        )
        HorizontalDivider()
        val fast = report.animationScale < 1f
        Row(
            modifier = Modifier.toggleable(value = fast, enabled = privileged, role = Role.Switch, onValueChange = onFastAnimations),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.perf_animations_fast), style = MaterialTheme.typography.bodyMedium)
                Hint(stringResource(R.string.perf_animations) + ": ${report.animationScale}x")
            }
            if (privileged) {
                Switch(checked = fast, onCheckedChange = null)
            }
        }
        if (!privileged) {
            TextButton(onClick = { context.openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }) {
                Text(stringResource(R.string.perf_open_dev_options))
            }
        }
    }
}

/** Opens a system settings page; some manufacturers drop pages, then the main settings open instead. */
private fun Context.openSettings(action: String) {
    listOf(action, Settings.ACTION_SETTINGS).firstOrNull { candidate ->
        runCatching { startActivity(Intent(candidate).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}

/** RAM in use before and after, in the same percent the dashboard and the widget show. */
@Composable
private fun RamResult(result: OptimizeResult, totalBytes: Long) {
    if (totalBytes <= 0) return
    fun usedPercent(available: Long) = ((totalBytes - available) * 100 / totalBytes).toInt()
    val freed = result.ramAfterBytes - result.ramBeforeBytes
    val before = usedPercent(result.ramBeforeBytes)
    val after = usedPercent(result.ramAfterBytes)
    Diagnosis(
        label = stringResource(R.string.perf_result_ram, before, after),
        value = if (freed >= RAM_NOISE_BYTES) "+${formatBytes(freed)}" else stringResource(R.string.perf_result_ram_same),
        status = if (after < before) Status.GOOD else Status.NEUTRAL,
    )
    Hint(
        stringResource(
            when {
                freed >= RAM_NOISE_BYTES -> R.string.perf_result_ram_hint
                result.freedRam -> R.string.perf_result_ram_nothing_cached
                else -> R.string.perf_result_ram_not_chosen
            },
        ),
    )
}

/** Below this the difference is ordinary fluctuation, not something the optimization did. */
private const val RAM_NOISE_BYTES = 30L * 1024 * 1024

@Composable
private fun Diagnosis(label: String, value: String, status: Status) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(SysCleanTheme.spacing.sm))
        StatusChip(value, status)
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun OptimizeCard(
    report: PerformanceReport,
    steps: Set<OptimizeStep>,
    progress: OptimizeProgress?,
    result: OptimizeResult?,
    onToggle: (OptimizeStep) -> Unit,
    onOptimize: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.perf_optimize), icon = Icons.Outlined.Bolt) {
        val available = buildList {
            if (report.unoptimizedApps.orEmpty().isNotEmpty()) {
                add(OptimizeStep.COMPILE_APPS to stringResource(R.string.perf_step_compile, report.unoptimizedApps!!.size))
            }
            add(OptimizeStep.TRIM_STORAGE to stringResource(R.string.perf_step_trim))
            if (report.deepSleepCandidates.orEmpty().isNotEmpty()) {
                add(OptimizeStep.DEEP_SLEEP_IDLE_APPS to stringResource(R.string.perf_step_sleep, report.deepSleepCandidates!!.size))
            }
            add(OptimizeStep.FREE_RAM to stringResource(R.string.perf_step_free_ram))
            if (report.animationScale >= 1f) add(OptimizeStep.FASTER_ANIMATIONS to stringResource(R.string.perf_step_animations))
        }
        available.forEach { (step, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = step in steps, enabled = progress == null, role = Role.Checkbox) { onToggle(step) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = step in steps, onCheckedChange = null)
                Spacer(Modifier.width(SysCleanTheme.spacing.sm))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { if (progress.total > 0) progress.done.toFloat() / progress.total else 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                when (progress.step) {
                    OptimizeStep.COMPILE_APPS -> stringResource(R.string.perf_running_compile, progress.done + 1, progress.total)
                    OptimizeStep.TRIM_STORAGE -> stringResource(R.string.perf_running_trim)
                    OptimizeStep.DEEP_SLEEP_IDLE_APPS -> stringResource(R.string.perf_running_sleep, progress.done + 1, progress.total)
                    OptimizeStep.FREE_RAM -> stringResource(R.string.perf_running_free_ram)
                    OptimizeStep.FASTER_ANIMATIONS -> stringResource(R.string.perf_running_animations)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            progress.current?.let { Hint(it) }
        } else {
            Button(onClick = onOptimize, enabled = steps.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.perf_optimize_button))
            }
            Hint(stringResource(R.string.perf_optimize_hint))
        }
        result?.let {
            HorizontalDivider()
            Text(
                stringResource(
                    R.string.perf_result,
                    it.compiledApps,
                    it.sleptApps,
                    if (it.trimmed) stringResource(R.string.perf_result_trimmed) else "",
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            RamResult(it, report.ramTotalBytes)
        }
    }
}
