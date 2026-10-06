package vn.sysclean.feature.performance

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.sysclean.core.designsystem.component.Banner
import vn.sysclean.core.designsystem.component.LoadingContent
import vn.sysclean.core.designsystem.component.MessageContent
import vn.sysclean.core.designsystem.component.SectionCard
import vn.sysclean.core.designsystem.component.Status
import vn.sysclean.core.designsystem.component.StatusChip
import vn.sysclean.core.designsystem.theme.SysCleanTheme
import vn.sysclean.core.performance.AppKind
import vn.sysclean.core.performance.BatteryAnalyzer
import vn.sysclean.core.performance.BatteryApp
import vn.sysclean.core.performance.BatteryReport
import vn.sysclean.core.performance.DrainSign
import vn.sysclean.core.performance.RamManager
import java.util.Locale
import javax.inject.Inject

data class BatteryUiState(
    val loading: Boolean = true,
    val report: BatteryReport? = null,
    val busy: Set<String> = emptySet(),
)

sealed interface BatteryEvent {
    data class Slept(val app: BatteryApp) : BatteryEvent
    data class Woken(val label: String) : BatteryEvent
    data class Disabled(val label: String) : BatteryEvent
    data class Failed(val label: String) : BatteryEvent
}

@HiltViewModel
class BatteryViewModel @Inject constructor(
    private val analyzer: BatteryAnalyzer,
    private val ramManager: RamManager,
) : ViewModel() {

    private val _state = MutableStateFlow(BatteryUiState())
    val uiState: StateFlow<BatteryUiState> = _state.asStateFlow()
    private val events = Channel<BatteryEvent>(Channel.BUFFERED)
    val eventFlow: Flow<BatteryEvent> = events.receiveAsFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val report = runCatching { analyzer.report() }.getOrNull()
            _state.update { it.copy(loading = false, report = report ?: it.report) }
        }
    }

    fun deepSleep(app: BatteryApp) = act(app.packageName) {
        if (ramManager.deepSleep(app.packageName)) {
            markSleeping(app.packageName, true)
            BatteryEvent.Slept(app)
        } else {
            BatteryEvent.Failed(app.label)
        }
    }

    fun wake(app: BatteryApp) = act(app.packageName) {
        if (ramManager.wake(app.packageName)) {
            markSleeping(app.packageName, false)
            BatteryEvent.Woken(app.label)
        } else {
            BatteryEvent.Failed(app.label)
        }
    }

    fun disable(app: BatteryApp) = act(app.packageName) {
        if (ramManager.disableService(app.packageName)) {
            // Disabled services stop drawing power; they stay listed (the history is past use).
            markSleeping(app.packageName, true)
            BatteryEvent.Disabled(app.label)
        } else {
            BatteryEvent.Failed(app.label)
        }
    }

    // The counters describe the past, so the list is not re-read: only the row's state changes.
    private fun markSleeping(packageName: String, sleeping: Boolean) = _state.update { state ->
        state.copy(
            report = state.report?.copy(
                apps = state.report.apps.map { if (it.packageName == packageName) it.copy(deepSleeping = sleeping) else it },
            ),
        )
    }

    private fun act(packageName: String, block: suspend () -> BatteryEvent) {
        if (packageName in _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = it.busy + packageName) }
            val event = runCatching { block() }.getOrElse { BatteryEvent.Failed(packageName) }
            _state.update { it.copy(busy = it.busy - packageName) }
            events.send(event)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatteryScreen(onBack: () -> Unit, viewModel: BatteryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is BatteryEvent.Slept -> {
                    val result = snackbar.showSnackbar(
                        message = context.getString(R.string.ram_slept, event.app.label),
                        actionLabel = context.getString(R.string.ram_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.wake(event.app)
                }
                is BatteryEvent.Woken -> snackbar.showSnackbar(context.getString(R.string.ram_woken, event.label))
                is BatteryEvent.Disabled -> snackbar.showSnackbar(context.getString(R.string.ram_disabled, event.label))
                is BatteryEvent.Failed -> snackbar.showSnackbar(context.getString(R.string.ram_failed, event.label))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.ram_back)) } },
                title = { Text(stringResource(R.string.battery_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val report = state.report
        when {
            report == null && state.loading -> LoadingContent(Modifier.padding(padding))
            report == null -> MessageContent(
                icon = Icons.Outlined.BatteryAlert,
                title = stringResource(R.string.battery_title),
                body = stringResource(R.string.battery_unavailable),
                modifier = Modifier.padding(padding),
            )
            else -> BatteryContent(
                report = report,
                busy = state.busy,
                padding = padding,
                onDeepSleep = viewModel::deepSleep,
                onWake = viewModel::wake,
                onDisable = viewModel::disable,
                onDetails = { app ->
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun BatteryContent(
    report: BatteryReport,
    busy: Set<String>,
    padding: PaddingValues,
    onDeepSleep: (BatteryApp) -> Unit,
    onWake: (BatteryApp) -> Unit,
    onDisable: (BatteryApp) -> Unit,
    onDetails: (BatteryApp) -> Unit,
) {
    val spacing = SysCleanTheme.spacing
    // Apps that misbehave and can be acted on come first: that is the point of the screen.
    val flagged = report.apps.filter { it.signs.isNotEmpty() }
    val others = report.apps.filter { it.signs.isEmpty() }.take(MAX_OTHERS)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.lg,
            end = spacing.lg,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item { SummaryCard(report) }
        if (!report.reliable) {
            item {
                Banner(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.battery_short_title),
                    body = stringResource(R.string.battery_short_body),
                )
            }
        }
        item {
            SectionTitle(
                stringResource(R.string.battery_flagged),
                stringResource(if (flagged.isEmpty()) R.string.battery_flagged_none else R.string.battery_flagged_hint),
            )
        }
        items(flagged, key = { "f-" + it.packageName }) { app ->
            AppBatteryRow(app, report, app.packageName in busy, onDeepSleep, onWake, onDisable, onDetails)
        }
        item { SectionTitle(stringResource(R.string.battery_others), stringResource(R.string.battery_others_hint)) }
        items(others, key = { "o-" + it.packageName }) { app ->
            AppBatteryRow(app, report, app.packageName in busy, onDeepSleep, onWake, onDisable, onDetails)
        }
        if (report.hasPowerEstimates && report.systemMah > 0) {
            item {
                Text(
                    stringResource(R.string.battery_system, mah(report.systemMah)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text(
                stringResource(R.string.battery_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCard(report: BatteryReport) {
    SectionCard(title = stringResource(R.string.battery_since_charge), icon = Icons.Outlined.BatteryAlert) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.battery_on_battery), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            StatusChip(duration(report.periodMillis), Status.NEUTRAL)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.battery_screen_off), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            StatusChip(duration(report.screenOffMillis), Status.NEUTRAL)
        }
        val used = report.dischargeMah
        val capacity = report.capacityMah
        if (used != null && capacity != null && used > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.battery_used), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                StatusChip(stringResource(R.string.battery_used_value, mah(used), capacity), Status.NEUTRAL)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppBatteryRow(
    app: BatteryApp,
    report: BatteryReport,
    busy: Boolean,
    onDeepSleep: (BatteryApp) -> Unit,
    onWake: (BatteryApp) -> Unit,
    onDisable: (BatteryApp) -> Unit,
    onDetails: (BatteryApp) -> Unit,
) {
    val spacing = SysCleanTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (report.hasPowerEstimates) {
                        stringResource(R.string.battery_share, mah(app.powerMah), percent(app.sharePercent))
                    } else {
                        stringResource(R.string.battery_share_activity, percent(app.sharePercent))
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.width(spacing.sm))
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                when {
                    app.deepSleeping -> TextButton(onClick = { if (app.sleepable) onWake(app) else onDetails(app) }) {
                        Text(stringResource(if (app.sleepable) R.string.ram_wake else R.string.battery_details))
                    }
                    app.sleepable -> FilledTonalButton(onClick = { onDeepSleep(app) }) { Text(stringResource(R.string.ram_deep_sleep)) }
                    app.kind == AppKind.OPTIONAL_SERVICE -> OutlinedButton(onClick = { onDisable(app) }) { Text(stringResource(R.string.ram_disable)) }
                    else -> TextButton(onClick = { onDetails(app) }) { Text(stringResource(R.string.battery_details)) }
                }
            }
        }
        if (app.signs.isNotEmpty() || app.deepSleeping) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                if (app.deepSleeping) StatusChip(stringResource(R.string.ram_state_asleep), Status.GOOD)
                app.signs.forEach { sign ->
                    StatusChip(
                        when (sign) {
                            DrainSign.WAKES_OFTEN -> stringResource(R.string.battery_sign_wakes, app.wakeupsPerHour.toInt())
                            DrainSign.KEEPS_AWAKE -> stringResource(R.string.battery_sign_awake, oneDecimal(app.awakeMinutesPerHour))
                            DrainSign.BUSY_IN_BACKGROUND -> stringResource(R.string.battery_sign_background)
                        },
                        Status.WARNING,
                    )
                }
            }
        }
        HorizontalDivider(Modifier.fillMaxWidth())
    }
}

@Composable
private fun SectionTitle(title: String, hint: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun duration(millis: Long): String {
    val minutes = millis / 60_000
    return stringResource(R.string.battery_duration, minutes / 60, minutes % 60)
}

private fun mah(value: Double) = String.format(Locale.getDefault(), "%.1f", value)
private fun percent(value: Double) = String.format(Locale.getDefault(), "%.1f", value)
private fun oneDecimal(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

/** Past the flagged ones, the heaviest few are enough to see where the battery went. */
private const val MAX_OTHERS = 15
