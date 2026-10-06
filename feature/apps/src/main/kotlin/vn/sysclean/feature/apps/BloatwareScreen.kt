package vn.sysclean.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.sysclean.core.designsystem.component.LoadingContent
import vn.sysclean.core.designsystem.component.MessageContent
import vn.sysclean.core.designsystem.component.Status
import vn.sysclean.core.designsystem.component.StatusChip
import vn.sysclean.core.designsystem.theme.SysCleanTheme
import vn.sysclean.core.model.PreinstalledApp
import vn.sysclean.core.privilege.shell.BloatReason

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BloatwareScreen(onBack: () -> Unit, viewModel: BloatwareViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            val id = when (event.action) {
                BloatAction.REMOVED -> R.string.bloat_done_removed
                BloatAction.DISABLED -> R.string.bloat_done_disabled
                BloatAction.RESTORED -> R.string.bloat_done_restored
                BloatAction.FAILED -> R.string.bloat_failed
            }
            snackbar.showSnackbar(context.getString(id, event.label))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.bloat_back)) }
                },
                title = { Text(stringResource(R.string.bloat_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val spacing = SysCleanTheme.spacing
        when {
            state.loading -> LoadingContent(Modifier.padding(padding))
            !state.shizukuReady -> MessageContent(
                icon = Icons.Outlined.CheckCircle,
                title = stringResource(R.string.bloat_title),
                body = stringResource(R.string.bloat_needs_shizuku),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = spacing.lg,
                    end = spacing.lg,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + spacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                item {
                    Text(
                        stringResource(R.string.bloat_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { SectionTitle(stringResource(R.string.bloat_recommended)) }
                if (state.removable.isEmpty()) {
                    item { Text(stringResource(R.string.bloat_none), style = MaterialTheme.typography.bodyMedium) }
                }
                items(state.removable, key = { it.packageName }) { app ->
                    BloatRow(app, viewModel.reasonOf(app.packageName), busy = app.packageName in state.busy) {
                        OutlinedButton(onClick = { viewModel.disable(app) }) { Text(stringResource(R.string.bloat_disable)) }
                        FilledTonalButton(onClick = { viewModel.remove(app) }) { Text(stringResource(R.string.bloat_remove)) }
                    }
                }
                item { SectionTitle(stringResource(R.string.bloat_unused)) }
                item {
                    Text(
                        stringResource(if (state.usageAccess) R.string.bloat_unused_hint else R.string.bloat_unused_needs_usage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.usageAccess && state.unused.isEmpty()) {
                    item { Text(stringResource(R.string.bloat_unused_none), style = MaterialTheme.typography.bodyMedium) }
                }
                // Not on any list, so only turned off (reversible), never removed.
                items(state.unused, key = { "u-" + it.packageName }) { app ->
                    BloatRow(app, viewModel.reasonOf(app.packageName), busy = app.packageName in state.busy) {
                        OutlinedButton(onClick = { viewModel.disable(app) }) { Text(stringResource(R.string.bloat_disable)) }
                    }
                }
                if (state.restorable.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.bloat_removed)) }
                    items(state.restorable, key = { "r-" + it.packageName }) { app ->
                        BloatRow(app, viewModel.reasonOf(app.packageName), busy = app.packageName in state.busy) {
                            StatusChip(
                                stringResource(if (!app.installedForUser) R.string.bloat_state_removed else R.string.bloat_state_disabled),
                                Status.NEUTRAL,
                            )
                            FilledTonalButton(onClick = { viewModel.restore(app) }) { Text(stringResource(R.string.bloat_restore)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = SysCleanTheme.spacing.md),
    )
}

@Composable
private fun BloatRow(
    app: PreinstalledApp,
    reason: BloatReason?,
    busy: Boolean,
    actions: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = SysCleanTheme.spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                reason?.let {
                    Text(
                        stringResource(
                            when (it) {
                                BloatReason.FACEBOOK_PRELOAD -> R.string.bloat_reason_facebook
                                BloatReason.VENDOR_ADS -> R.string.bloat_reason_ads
                                BloatReason.RARELY_USED_PRELOAD -> R.string.bloat_reason_extra
                                BloatReason.UNUSED_PRELOAD -> R.string.bloat_reason_unused
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = SysCleanTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(24.dp)) else actions()
        }
    }
}
