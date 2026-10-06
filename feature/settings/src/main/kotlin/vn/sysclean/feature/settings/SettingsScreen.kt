package vn.sysclean.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vn.sysclean.core.data.repository.UserPreferencesRepository
import vn.sysclean.core.designsystem.component.Banner
import vn.sysclean.core.designsystem.component.InfoRow
import vn.sysclean.core.designsystem.component.SectionCard
import vn.sysclean.core.designsystem.component.Status
import vn.sysclean.core.designsystem.component.StatusChip
import vn.sysclean.core.designsystem.theme.SysCleanTheme
import vn.sysclean.core.model.AccessLevel
import vn.sysclean.core.model.AccessState
import vn.sysclean.core.model.ShizukuState
import vn.sysclean.core.privilege.startActivityWithFallback
import vn.sysclean.core.ui.grantedLabel
import vn.sysclean.core.ui.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    onOpenTrash: () -> Unit,
    onOpenWhitelist: () -> Unit,
    onAddWidget: () -> Boolean,
    onOpenShizukuSetup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val retentionDays by viewModel.retentionDays.collectAsStateWithLifecycle()
    val whitelistCount by viewModel.whitelistCount.collectAsStateWithLifecycle()
    val enablingRoot by viewModel.enablingRoot.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.rootResults.collect { granted ->
            snackbar.showSnackbar(resources.getString(if (granted) R.string.settings_root_enabled else R.string.settings_root_denied))
        }
    }
    val context = LocalContext.current
    val spacing = SysCleanTheme.spacing
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refresh()
    }
    // Google Play's "prominent disclosure": say what is accessed and why, before the system screen.
    var disclosure by rememberSaveable { mutableStateOf<Disclosure?>(null) }
    val grantAllFiles = {
        if (viewModel.needsLegacyStoragePermission) {
            legacyLauncher.launch(viewModel.legacyStoragePermissions)
        } else {
            val (primary, fallback) = viewModel.allFilesAccessIntents()
            context.startActivityWithFallback(primary, fallback)
        }
    }
    val grantUsage = {
        val (primary, fallback) = viewModel.usageAccessIntents()
        context.startActivityWithFallback(primary, fallback)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
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
            item { ModeCard(access) }
            item {
                SectionCard(title = stringResource(R.string.settings_permissions_title), icon = Icons.Outlined.VerifiedUser) {
                    PermissionRow(
                        title = stringResource(
                            if (viewModel.needsLegacyStoragePermission) R.string.settings_storage_legacy else R.string.settings_all_files,
                        ),
                        description = stringResource(R.string.settings_all_files_desc),
                        granted = access.allFilesAccess,
                        onGrant = { disclosure = Disclosure.ALL_FILES },
                    )
                    HorizontalDivider()
                    PermissionRow(
                        title = stringResource(R.string.settings_usage),
                        description = stringResource(R.string.settings_usage_desc),
                        granted = access.usageAccess,
                        onGrant = { disclosure = Disclosure.USAGE },
                    )
                }
            }
            item {
                AdvancedCard(
                    access = access,
                    onInstallShizuku = {
                        val (play, web) = viewModel.shizukuInstallIntents()
                        context.startActivityWithFallback(play, web)
                    },
                    onOpenShizuku = { viewModel.shizukuLaunchIntent()?.let { context.startActivityWithFallback(it, null) } },
                    onGrantShizuku = viewModel::requestShizukuPermission,
                    onOpenShizukuSetup = onOpenShizukuSetup,
                    enablingRoot = enablingRoot,
                    onEnableRoot = viewModel::enableRootMode,
                    onDisableRoot = viewModel::disableRootMode,
                )
            }
            item {
                CleaningCard(
                    retentionDays = retentionDays,
                    whitelistCount = whitelistCount,
                    onRetentionChange = viewModel::setRetentionDays,
                    onOpenTrash = onOpenTrash,
                    onOpenWhitelist = onOpenWhitelist,
                )
            }
            item {
                SectionCard(title = stringResource(R.string.settings_widget_title), icon = Icons.Outlined.Widgets) {
                    Text(
                        stringResource(R.string.settings_widget_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val manual = stringResource(R.string.settings_widget_manual)
                    val scope = rememberCoroutineScope()
                    FilledTonalButton(onClick = {
                        if (!onAddWidget()) scope.launch { snackbar.showSnackbar(manual) }
                    }) { Text(stringResource(R.string.settings_widget_add)) }
                }
            }
            item { LanguageCard() }
            item {
                SectionCard(title = stringResource(R.string.settings_about_title), icon = Icons.Outlined.Info) {
                    val version = remember {
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
                    }
                    InfoRow(stringResource(R.string.settings_version), version)
                    // Opened in the browser: SysClean itself has no internet permission.
                    TextButton(onClick = { context.startActivityWithFallback(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)), null) }) {
                        Text(stringResource(R.string.settings_privacy_policy))
                    }
                    TextButton(onClick = { context.startActivityWithFallback(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)), null) }) {
                        Text(stringResource(R.string.settings_source_code))
                    }
                }
            }
        }
    }
    disclosure?.let { shown ->
        PermissionDisclosureDialog(
            disclosure = shown,
            onContinue = {
                disclosure = null
                when (shown) {
                    Disclosure.ALL_FILES -> grantAllFiles()
                    Disclosure.USAGE -> grantUsage()
                }
            },
            onDismiss = { disclosure = null },
        )
    }
}

@Composable
private fun ModeCard(access: AccessState) {
    val level = access.activeLevel
    SectionCard(title = stringResource(R.string.settings_mode_title), icon = Icons.Outlined.Shield) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_mode_current), modifier = Modifier.weight(1f))
            StatusChip(level.label(), if (level == AccessLevel.NORMAL) Status.NEUTRAL else Status.GOOD)
        }
        Text(
            if (level == AccessLevel.NORMAL) {
                stringResource(R.string.settings_mode_normal_body)
            } else {
                stringResource(R.string.settings_mode_advanced_body, level.label())
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onGrant: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            StatusChip(grantedLabel(granted), if (granted) Status.GOOD else Status.WARNING)
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!granted) {
            FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.settings_grant)) }
        }
    }
}

@Composable
private fun AdvancedCard(
    access: AccessState,
    onInstallShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
    onGrantShizuku: () -> Unit,
    onOpenShizukuSetup: () -> Unit,
    enablingRoot: Boolean,
    onEnableRoot: () -> Unit,
    onDisableRoot: () -> Unit,
) {
    val spacing = SysCleanTheme.spacing
    SectionCard(title = stringResource(R.string.settings_advanced_title), icon = Icons.Outlined.AdminPanelSettings) {
        Text(stringResource(R.string.settings_advanced_intro), style = MaterialTheme.typography.bodyMedium)
        listOf(
            R.string.settings_advanced_feature_data,
            R.string.settings_advanced_feature_cache,
            R.string.settings_advanced_feature_stop,
            R.string.settings_advanced_feature_bloat,
        ).forEach { feature ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(18.dp),
                )
                Spacer(Modifier.width(spacing.sm))
                Text(stringResource(feature), style = MaterialTheme.typography.bodyMedium)
            }
        }
        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_shizuku), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            StatusChip(
                stringResource(
                    when (access.shizuku) {
                        ShizukuState.NOT_INSTALLED -> R.string.settings_shizuku_not_installed
                        ShizukuState.NOT_RUNNING -> R.string.settings_shizuku_not_running
                        ShizukuState.PERMISSION_REQUIRED -> R.string.settings_shizuku_permission
                        ShizukuState.READY -> R.string.settings_shizuku_ready
                    },
                ),
                if (access.shizuku == ShizukuState.READY) Status.GOOD else Status.NEUTRAL,
            )
        }
        when (access.shizuku) {
            ShizukuState.NOT_INSTALLED ->
                FilledTonalButton(onClick = onInstallShizuku) { Text(stringResource(R.string.settings_shizuku_install)) }
            ShizukuState.NOT_RUNNING ->
                FilledTonalButton(onClick = onOpenShizuku) { Text(stringResource(R.string.settings_shizuku_open)) }
            ShizukuState.PERMISSION_REQUIRED ->
                FilledTonalButton(onClick = onGrantShizuku) { Text(stringResource(R.string.settings_shizuku_grant)) }
            ShizukuState.READY -> Unit
        }
        // The step-by-step guide replaces the wall of text: it ticks steps off from the phone's real state.
        if (access.shizuku != ShizukuState.READY) {
            Button(onClick = onOpenShizukuSetup, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_shizuku_setup))
            }
        }
        HorizontalDivider()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_root), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            StatusChip(
                stringResource(
                    when {
                        access.rootMode -> R.string.settings_root_on
                        access.rootDetected -> R.string.settings_root_detected
                        else -> R.string.settings_root_not_detected
                    },
                ),
                if (access.rootMode || access.rootDetected) Status.GOOD else Status.NEUTRAL,
            )
        }
        when {
            enablingRoot -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(spacing.sm))
                Text(stringResource(R.string.settings_root_waiting), style = MaterialTheme.typography.bodyMedium)
            }
            access.rootMode -> {
                OutlinedButton(onClick = onDisableRoot) { Text(stringResource(R.string.settings_root_disable)) }
                if (access.shizuku == ShizukuState.READY) {
                    Text(
                        stringResource(R.string.settings_root_shizuku_first),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            access.rootDetected ->
                FilledTonalButton(onClick = onEnableRoot) { Text(stringResource(R.string.settings_root_enable)) }
            // Hidden root (e.g. Zygisk DenyList) escapes detection; let the user try explicitly.
            else -> TextButton(onClick = onEnableRoot) { Text(stringResource(R.string.settings_root_try)) }
        }
        Text(
            stringResource(R.string.settings_root_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CleaningCard(
    retentionDays: Int,
    whitelistCount: Int,
    onRetentionChange: (Int) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenWhitelist: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.settings_cleaning_title), icon = Icons.Outlined.CleaningServices) {
        LinkRow(
            title = stringResource(R.string.settings_trash),
            description = stringResource(R.string.settings_trash_desc),
            onClick = onOpenTrash,
        )
        HorizontalDivider()
        Text(stringResource(R.string.settings_retention), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.sm)) {
            UserPreferencesRepository.RETENTION_OPTIONS.forEach { days ->
                FilterChip(
                    selected = days == retentionDays,
                    onClick = { onRetentionChange(days) },
                    label = { Text(pluralStringResource(R.plurals.settings_days, days, days)) },
                )
            }
        }
        HorizontalDivider()
        LinkRow(
            title = stringResource(R.string.settings_whitelist),
            description = stringResource(R.string.settings_whitelist_desc, whitelistCount),
            onClick = onOpenWhitelist,
        )
    }
}

@Composable
private fun LinkRow(title: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun LanguageCard() {
    // Empty tag = follow the system language.
    var selected by remember { mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags()) }
    val options = listOf(
        "" to stringResource(R.string.settings_language_system),
        "en" to stringResource(R.string.settings_language_en),
        "vi" to stringResource(R.string.settings_language_vi),
    )
    SectionCard(title = stringResource(R.string.settings_language_title), icon = Icons.Outlined.Language) {
        Column(Modifier.selectableGroup()) {
            options.forEach { (tag, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected == tag,
                            role = Role.RadioButton,
                            onClick = {
                                selected = tag
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == tag, onClick = null)
                    Spacer(Modifier.width(SysCleanTheme.spacing.sm))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

internal enum class Disclosure { ALL_FILES, USAGE }

@Composable
private fun PermissionDisclosureDialog(disclosure: Disclosure, onContinue: () -> Unit, onDismiss: () -> Unit) {
    val (title, body) = when (disclosure) {
        Disclosure.ALL_FILES -> R.string.disclosure_files_title to R.string.disclosure_files_body
        Disclosure.USAGE -> R.string.disclosure_usage_title to R.string.disclosure_usage_body
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.VerifiedUser, contentDescription = null) },
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.sm)) {
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.disclosure_on_device), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.disclosure_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.disclosure_not_now)) } },
    )
}

private const val PRIVACY_POLICY_URL = "https://penz7.github.io/SysClean/privacy-policy.html"
private const val SOURCE_URL = "https://github.com/Penz7/SysClean"
