package vn.sysclean.feature.apps

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.sysclean.core.common.format.formatBytes
import vn.sysclean.core.designsystem.component.Banner
import vn.sysclean.core.designsystem.component.LoadingContent
import vn.sysclean.core.designsystem.component.MessageContent
import vn.sysclean.core.designsystem.component.Status
import vn.sysclean.core.designsystem.component.StatusChip
import vn.sysclean.core.designsystem.theme.SysCleanTheme
import vn.sysclean.core.model.AppInfo
import vn.sysclean.core.ui.lastUsedLabel

@Composable
internal fun AppsScreen(
    onOpenAppDetails: (String) -> Unit,
    onRequestUsageAccess: () -> Unit,
    onOpenBloatware: () -> Unit,
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // One system uninstall dialog at a time; the queue advances when each one closes.
    val uninstaller = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onUninstallDialogClosed()
    }
    val current = state.uninstall?.current
    LaunchedEffect(current, state.uninstall?.index) {
        if (current != null && state.uninstall?.silent == false) {
            uninstaller.launch(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", current, null)))
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            val message = when (event) {
                is AppsEvent.Uninstalled -> context.resources.getQuantityString(R.plurals.apps_uninstalled, event.count, event.count)
                is AppsEvent.ForceStopped -> context.resources.getQuantityString(R.plurals.apps_force_stopped, event.count, event.count)
            }
            snackbar.showSnackbar(message)
        }
    }

    AppsContent(
        state = state,
        snackbar = snackbar,
        onSearch = viewModel::onSearch,
        onFilter = viewModel::onFilter,
        onSort = viewModel::onSort,
        onOpenAppDetails = onOpenAppDetails,
        onRequestUsageAccess = onRequestUsageAccess,
        onStartSelection = viewModel::startSelection,
        onToggleSelection = viewModel::toggleSelection,
        onSelectAll = viewModel::selectAllShown,
        onClearSelection = viewModel::clearSelection,
        onUninstall = viewModel::uninstall,
        onForceStop = viewModel::forceStop,
        onOpenBloatware = onOpenBloatware,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppsContent(
    state: AppsUiState,
    snackbar: SnackbarHostState,
    onSearch: (String) -> Unit,
    onFilter: (AppFilter) -> Unit,
    onSort: (AppSort) -> Unit,
    onOpenAppDetails: (String) -> Unit,
    onRequestUsageAccess: () -> Unit,
    onStartSelection: (AppInfo) -> Unit,
    onToggleSelection: (AppInfo) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onUninstall: (List<String>) -> Unit,
    onForceStop: (List<String>) -> Unit,
    onOpenBloatware: () -> Unit,
) {
    val spacing = SysCleanTheme.spacing
    val selecting = state.selection != null
    var confirmBatch by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = selecting) { onClearSelection() }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.apps_close_selection))
                        }
                    },
                    title = {
                        Text(
                            stringResource(
                                R.string.apps_selected,
                                state.selectedApps.size,
                                formatBytes(state.selectedApps.sumOf { it.size?.totalBytes ?: 0 }),
                            ),
                        )
                    },
                    actions = {
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Outlined.SelectAll, stringResource(R.string.apps_select_all))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.apps_title)) },
                    actions = {
                        if (state.privileged) {
                            IconButton(onClick = onOpenBloatware) {
                                Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.apps_bloatware))
                            }
                        }
                        SortMenu(state.query.sort, onSort)
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val queue = state.uninstall
            if (queue != null || (selecting && state.selectedApps.isNotEmpty())) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        if (queue != null) {
                            LinearProgressIndicator(
                                progress = { queue.index.toFloat() / queue.packages.size },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(stringResource(R.string.apps_uninstall_progress, queue.index + 1, queue.packages.size))
                        } else {
                            if (state.privileged) {
                                OutlinedButton(
                                    onClick = { onForceStop(state.selectedApps.map { it.packageName }) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(stringResource(R.string.apps_force_stop_selected, state.selectedApps.size)) }
                            }
                            Button(
                                onClick = { confirmBatch = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    stringResource(
                                        R.string.apps_uninstall_selected,
                                        state.selectedApps.size,
                                        formatBytes(state.selectedApps.sumOf { it.size?.totalBytes ?: 0 }),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + spacing.xl,
            ),
        ) {
            item {
                OutlinedTextField(
                    value = state.query.text,
                    onValueChange = onSearch,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(stringResource(R.string.apps_search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.lg),
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = spacing.lg, vertical = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    AppFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.query.filter == filter,
                            onClick = { onFilter(filter) },
                            label = { Text(filter.label()) },
                        )
                    }
                }
            }
            if (!state.hasUsageAccess) {
                item {
                    Banner(
                        icon = Icons.Outlined.QueryStats,
                        title = stringResource(R.string.apps_usage_title),
                        body = stringResource(R.string.apps_usage_body),
                        actionLabel = stringResource(R.string.apps_usage_action),
                        onAction = onRequestUsageAccess,
                        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                    )
                }
            }
            when {
                state.loading -> item { LoadingContent(Modifier.padding(top = spacing.xxl)) }
                state.apps.isEmpty() -> item {
                    MessageContent(icon = Icons.Outlined.Apps, title = stringResource(R.string.apps_empty), body = "")
                }
                else -> {
                    item {
                        Text(
                            text = if (state.hasUsageAccess) {
                                stringResource(
                                    R.string.apps_summary,
                                    state.apps.size,
                                    formatBytes(state.totalBytes),
                                    formatBytes(state.cacheBytes),
                                )
                            } else {
                                stringResource(R.string.apps_summary_no_size, state.apps.size)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                        )
                    }
                    if (!selecting) {
                        item {
                            Text(
                                stringResource(R.string.apps_select_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = spacing.lg),
                            )
                        }
                    }
                    items(state.apps, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            now = state.now,
                            showUsage = state.hasUsageAccess,
                            selected = state.selection?.contains(app.packageName),
                            onClick = { if (selecting) onToggleSelection(app) else onOpenAppDetails(app.packageName) },
                            onLongClick = { onStartSelection(app) },
                            onUninstall = { onUninstall(listOf(app.packageName)) },
                            privileged = state.privileged,
                            onForceStop = { onForceStop(listOf(app.packageName)) },
                            onAppInfo = { onOpenAppDetails(app.packageName) },
                        )
                    }
                }
            }
        }
    }

    if (confirmBatch) {
        ConfirmBatchUninstall(
            count = state.selectedApps.size,
            silent = state.privileged,
            onConfirm = { onUninstall(state.selectedApps.map { it.packageName }) },
            onDismiss = { confirmBatch = false },
        )
    }
}

@Composable
private fun ConfirmBatchUninstall(count: Int, silent: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.apps_uninstall_confirm_title, count)) },
        text = {
            Text(stringResource(if (silent) R.string.apps_uninstall_silent_body else R.string.apps_uninstall_confirm_body))
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.apps_uninstall)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.apps_cancel)) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppInfo,
    now: Long,
    showUsage: Boolean,
    /** Null outside selection mode. */
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUninstall: () -> Unit,
    privileged: Boolean,
    onForceStop: () -> Unit,
    onAppInfo: () -> Unit,
) {
    val spacing = SysCleanTheme.spacing
    val removable = !app.isSystem
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = spacing.lg, end = spacing.xs, top = spacing.md, bottom = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected != null) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = removable)
            Spacer(Modifier.width(spacing.sm))
        }
        AppIcon(app.packageName)
        Spacer(Modifier.width(spacing.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showUsage) {
                Text(
                    lastUsedLabel(app.lastUsedAt, now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                if (showUsage && app.isUnused(now)) StatusChip(stringResource(R.string.apps_unused), Status.WARNING)
                if (!app.isEnabled) StatusChip(stringResource(R.string.apps_disabled), Status.NEUTRAL)
            }
        }
        app.size?.let { size ->
            Spacer(Modifier.width(spacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                Text(formatBytes(size.totalBytes), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(R.string.apps_cache, formatBytes(size.cacheBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected == null) {
            // System apps are removed from the "Pre-installed apps" screen, never from here.
            if (removable) {
                IconButton(onClick = onUninstall) {
                    Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.apps_uninstall_one, app.label))
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            AppMenu(app.label, privileged, onForceStop, onAppInfo)
        }
    }
}

@Composable
private fun AppMenu(label: String, privileged: Boolean, onForceStop: () -> Unit, onAppInfo: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.apps_more, label)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (privileged) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.apps_force_stop)) },
                    onClick = {
                        open = false
                        onForceStop()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.apps_app_info)) },
                onClick = {
                    open = false
                    onAppInfo()
                },
            )
        }
    }
}

/** Icons are decoded off the main thread and only for rows on screen. */
@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        val bitmap = icon
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Icon(Icons.Outlined.Apps, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SortMenu(current: AppSort, onSort: (AppSort) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = stringResource(R.string.apps_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AppSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label()) },
                    leadingIcon = { RadioButton(selected = sort == current, onClick = null) },
                    onClick = {
                        onSort(sort)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AppFilter.label(): String = stringResource(
    when (this) {
        AppFilter.USER -> R.string.apps_filter_user
        AppFilter.SYSTEM -> R.string.apps_filter_system
        AppFilter.ALL -> R.string.apps_filter_all
        AppFilter.UNUSED -> R.string.apps_filter_unused
    },
)

@Composable
private fun AppSort.label(): String = stringResource(
    when (this) {
        AppSort.SIZE -> R.string.apps_sort_size
        AppSort.CACHE -> R.string.apps_sort_cache
        AppSort.LAST_USED -> R.string.apps_sort_last_used
        AppSort.NAME -> R.string.apps_sort_name
    },
)
