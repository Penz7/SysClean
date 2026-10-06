package vn.sysclean.feature.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import vn.sysclean.core.designsystem.component.Banner
import vn.sysclean.core.designsystem.theme.SysCleanTheme
import vn.sysclean.core.privilege.AccessRepository
import vn.sysclean.core.privilege.ShizukuQuirk
import vn.sysclean.core.privilege.startActivityWithFallback
import javax.inject.Inject

data class ShizukuSetupUiState(
    val progress: SetupProgress? = null,
    val quirk: ShizukuQuirk? = null,
    /** Paired before (it has worked for SysClean): starting it again is one tap, no pairing. */
    val pairedBefore: Boolean = false,
)

@HiltViewModel
class ShizukuSetupViewModel @Inject constructor(
    private val accessRepository: AccessRepository,
) : ViewModel() {

    /** The switches live in system Settings; re-read them while the guide is on screen. */
    private val status = flow {
        while (true) {
            emit(accessRepository.setupStatus())
            delay(STATUS_INTERVAL)
        }
    }

    val uiState: StateFlow<ShizukuSetupUiState> = combine(accessRepository.state, status) { access, status ->
        ShizukuSetupUiState(
            progress = ShizukuSetupSteps.progress(access.shizuku, status, accessRepository.hasWirelessDebugging),
            quirk = accessRepository.shizukuQuirk,
            pairedBefore = access.shizukuWasSetUp,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShizukuSetupUiState())

    val brand: Brand = Brand.of(Build.MANUFACTURER, Build.BRAND)

    fun refresh() = accessRepository.refresh()

    fun shizukuInstallIntents(): Pair<Intent, Intent> = accessRepository.shizukuInstallIntents()
    fun aboutPhoneIntent(): Intent = accessRepository.aboutPhoneIntent()
    fun wirelessDebuggingIntent(): Intent = accessRepository.wirelessDebuggingIntent()
    fun developerOptionsIntent(): Intent = accessRepository.developerOptionsIntent()
    fun shizukuLaunchIntent(): Intent? = accessRepository.shizukuLaunchIntent()
    fun requestShizukuPermission() = accessRepository.requestShizukuPermission()

    /** Where "Build number" hides differs by brand; only the well-known ones get a specific path. */
    enum class Brand {
        SAMSUNG, XIAOMI, OTHER;

        companion object {
            fun of(manufacturer: String, brand: String): Brand {
                val names = setOf(manufacturer.lowercase(), brand.lowercase())
                return when {
                    "samsung" in names -> SAMSUNG
                    names.any { it in setOf("xiaomi", "redmi", "poco") } -> XIAOMI
                    else -> OTHER
                }
            }
        }
    }

    private companion object {
        const val STATUS_INTERVAL = 1_500L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShizukuSetupScreen(onBack: () -> Unit, viewModel: ShizukuSetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Coming back from Settings or Shizuku: Shizuku's own state is re-read too.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.whitelist_back)) }
                },
                title = { Text(stringResource(R.string.setup_title)) },
            )
        },
    ) { padding ->
        val progress = state.progress ?: return@Scaffold
        val spacing = SysCleanTheme.spacing
        val doneCount = progress.steps.count { it.done != false }
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
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { doneCount.toFloat() / progress.steps.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.setup_progress, doneCount, progress.steps.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (progress.finished) {
                item {
                    Banner(
                        icon = Icons.Outlined.CheckCircle,
                        title = stringResource(R.string.setup_done_title),
                        body = stringResource(R.string.setup_done_body),
                        actionLabel = stringResource(R.string.setup_done_action),
                        onAction = onBack,
                    )
                }
            }
            items(progress.steps, key = { it.step }) { item ->
                val index = progress.steps.indexOf(item) + 1
                StepCard(index = index, item = item, current = item.step == progress.current) {
                    StepDetails(
                        step = item.step,
                        unknown = item.done == null,
                        pairedBefore = state.pairedBefore,
                        brand = viewModel.brand,
                        quirk = state.quirk,
                        onInstall = {
                            val (play, web) = viewModel.shizukuInstallIntents()
                            context.startActivityWithFallback(play, web)
                        },
                        onAboutPhone = { context.startActivityWithFallback(viewModel.aboutPhoneIntent(), Intent(Settings.ACTION_SETTINGS)) },
                        onWifi = { context.startActivityWithFallback(Intent(Settings.ACTION_WIFI_SETTINGS), Intent(Settings.ACTION_SETTINGS)) },
                        onWirelessDebugging = {
                            context.startActivityWithFallback(viewModel.wirelessDebuggingIntent(), viewModel.developerOptionsIntent())
                        },
                        onDeveloperOptions = { context.startActivityWithFallback(viewModel.developerOptionsIntent(), viewModel.aboutPhoneIntent()) },
                        onOpenShizuku = { viewModel.shizukuLaunchIntent()?.let { context.startActivityWithFallback(it, null) } },
                        onAllow = viewModel::requestShizukuPermission,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.setup_after_reboot),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StepCard(index: Int, item: SetupStepState, current: Boolean, details: @Composable () -> Unit) {
    val spacing = SysCleanTheme.spacing
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                StepBadge(index, item.done == true)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(item.step.title()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(
                            when (item.done) {
                                true -> R.string.setup_state_done
                                null -> R.string.setup_state_unknown
                                false -> if (current) R.string.setup_state_now else R.string.setup_state_todo
                            },
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Only the step to do now is expanded: one thing to read at a time.
            if (current || item.done == null) details()
        }
    }
}

@Composable
private fun StepBadge(index: Int, done: Boolean) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
        } else {
            Text("$index", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepDetails(
    step: SetupStep,
    unknown: Boolean,
    pairedBefore: Boolean,
    brand: ShizukuSetupViewModel.Brand,
    quirk: ShizukuQuirk?,
    onInstall: () -> Unit,
    onAboutPhone: () -> Unit,
    onWifi: () -> Unit,
    onWirelessDebugging: () -> Unit,
    onDeveloperOptions: () -> Unit,
    onOpenShizuku: () -> Unit,
    onAllow: () -> Unit,
) {
    val spacing = SysCleanTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        when (step) {
            SetupStep.INSTALL -> {
                Body(R.string.setup_install_body)
                Button(onClick = onInstall) { Text(stringResource(R.string.settings_shizuku_install)) }
            }
            SetupStep.DEVELOPER_OPTIONS -> {
                Body(
                    when (brand) {
                        ShizukuSetupViewModel.Brand.SAMSUNG -> R.string.setup_dev_body_samsung
                        ShizukuSetupViewModel.Brand.XIAOMI -> R.string.setup_dev_body_xiaomi
                        ShizukuSetupViewModel.Brand.OTHER -> R.string.setup_dev_body
                    },
                )
                Button(onClick = onAboutPhone) { Text(stringResource(R.string.setup_dev_action)) }
            }
            SetupStep.WIFI -> {
                Body(R.string.setup_wifi_body)
                Button(onClick = onWifi) { Text(stringResource(R.string.setup_wifi_action)) }
            }
            SetupStep.WIRELESS_DEBUGGING -> {
                Body(R.string.setup_wireless_body)
                if (unknown) Body(R.string.setup_wireless_unknown)
                Button(onClick = onWirelessDebugging) { Text(stringResource(R.string.setup_wireless_action)) }
            }
            SetupStep.PAIR_AND_START -> {
                if (pairedBefore) {
                    Body(R.string.setup_restart_body)
                } else {
                    Body(R.string.setup_pair_body)
                    Banner(
                        icon = Icons.Outlined.Info,
                        title = stringResource(R.string.settings_shizuku_pairing_tip_title),
                        body = stringResource(R.string.settings_shizuku_pairing_tip),
                    )
                }
                QuirkBanner(quirk, onDeveloperOptions)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Button(onClick = onOpenShizuku) { Text(stringResource(R.string.settings_shizuku_open)) }
                    OutlinedButton(onClick = onWirelessDebugging) { Text(stringResource(R.string.setup_wireless_short)) }
                }
            }
            SetupStep.USB_DEBUGGING -> {
                Body(R.string.setup_usb_body)
                Button(onClick = onDeveloperOptions) { Text(stringResource(R.string.settings_open_dev_options)) }
            }
            SetupStep.START_WITH_COMPUTER -> {
                Body(R.string.settings_shizuku_step_pc_only)
                QuirkBanner(quirk, onDeveloperOptions)
                Button(onClick = onOpenShizuku) { Text(stringResource(R.string.settings_shizuku_open)) }
            }
            SetupStep.ALLOW -> {
                Body(R.string.setup_allow_body)
                Body(R.string.settings_shizuku_restart_tip)
                QuirkBanner(quirk, onDeveloperOptions)
                Button(onClick = onAllow) { Text(stringResource(R.string.settings_shizuku_grant)) }
            }
        }
    }
}

/** Only the brands that need an extra switch see one. */
@Composable
private fun QuirkBanner(quirk: ShizukuQuirk?, onDeveloperOptions: () -> Unit) {
    val (title, body) = when (quirk ?: return) {
        ShizukuQuirk.XIAOMI -> R.string.settings_shizuku_xiaomi_title to R.string.settings_shizuku_xiaomi_body
        ShizukuQuirk.COLOROS -> R.string.settings_shizuku_coloros_title to R.string.settings_shizuku_coloros_body
        ShizukuQuirk.FLYME -> R.string.settings_shizuku_flyme_title to R.string.settings_shizuku_flyme_body
    }
    Banner(
        icon = Icons.Outlined.WarningAmber,
        title = stringResource(title),
        body = stringResource(body),
        actionLabel = stringResource(R.string.settings_open_dev_options),
        onAction = onDeveloperOptions,
    )
}

@Composable
private fun Body(id: Int) {
    Text(stringResource(id), style = MaterialTheme.typography.bodyMedium)
}

private fun SetupStep.title(): Int = when (this) {
    SetupStep.INSTALL -> R.string.setup_install
    SetupStep.DEVELOPER_OPTIONS -> R.string.setup_dev
    SetupStep.WIFI -> R.string.setup_wifi
    SetupStep.WIRELESS_DEBUGGING -> R.string.setup_wireless
    SetupStep.PAIR_AND_START -> R.string.setup_pair
    SetupStep.USB_DEBUGGING -> R.string.setup_usb
    SetupStep.START_WITH_COMPUTER -> R.string.setup_computer
    SetupStep.ALLOW -> R.string.setup_allow
}
