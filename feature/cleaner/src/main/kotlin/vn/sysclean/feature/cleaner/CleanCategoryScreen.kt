package vn.sysclean.feature.cleaner

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.sysclean.core.common.format.formatBytes
import vn.sysclean.core.designsystem.component.LoadingContent
import vn.sysclean.core.designsystem.component.MessageContent
import vn.sysclean.core.designsystem.component.Status
import vn.sysclean.core.designsystem.component.StatusChip
import vn.sysclean.core.designsystem.theme.SysCleanTheme
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.JunkItem
import vn.sysclean.core.model.isAppBased
import vn.sysclean.core.model.isGrouped
import vn.sysclean.core.model.isPhotoBased
import vn.sysclean.core.ui.MediaThumbnail
import vn.sysclean.core.ui.description
import vn.sysclean.core.ui.icon
import vn.sysclean.core.ui.label
import vn.sysclean.core.ui.R as UiR

const val CLEAN_BUTTON_TAG = "clean_button"

@Composable
internal fun CleanCategoryScreen(
    onBack: () -> Unit,
    onOpenAppDetails: (String) -> Unit,
    viewModel: CleanCategoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is CleanerEvent.Cleaned -> {
                    val r = event.result
                    val message = buildList {
                        add(
                            when {
                                event.isEmptyFolders -> context.getString(R.string.cleaner_result_folders, r.cleanedCount)
                                r.trashIds.isEmpty() -> context.getString(R.string.cleaner_result_deleted, r.cleanedCount, formatBytes(r.cleanedBytes))
                                else -> context.getString(R.string.cleaner_result, r.cleanedCount, formatBytes(r.cleanedBytes))
                            },
                        )
                        if (r.unverifiedCount > 0) add(context.getString(R.string.cleaner_result_unverified, r.unverifiedCount))
                        if (r.failedCount > 0) add(context.getString(R.string.cleaner_result_failed, r.failedCount))
                    }.joinToString("\n")
                    val action = context.getString(R.string.cleaner_undo).takeIf { r.trashIds.isNotEmpty() }
                    val outcome = snackbar.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Long)
                    if (outcome == SnackbarResult.ActionPerformed) viewModel.undo(r)
                }
                is CleanerEvent.Restored -> snackbar.showSnackbar(context.getString(R.string.cleaner_restored, event.count))
                CleanerEvent.Ignored -> snackbar.showSnackbar(context.getString(R.string.cleaner_ignored))
                CleanerEvent.AppCacheCleared -> snackbar.showSnackbar(context.getString(R.string.cleaner_app_cache_done))
                is CleanerEvent.AllCachesCleared ->
                    snackbar.showSnackbar(context.getString(R.string.cleaner_clear_all_done, formatBytes(event.bytes)))
            }
        }
    }

    CleanCategoryContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onToggle = viewModel::toggle,
        onSelectAll = viewModel::selectAll,
        onSelectNone = viewModel::selectNone,
        onSelectExtras = viewModel::selectExtras,
        onClean = viewModel::clean,
        onIgnore = viewModel::ignore,
        onOpenAppDetails = onOpenAppDetails,
        clearAllAppCacheIntent = viewModel::clearAllAppCacheIntent,
        onAppCacheCleared = viewModel::onAppCacheCleared,
        onClearAllCaches = viewModel::clearAllCaches,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CleanCategoryContent(
    state: CleanCategoryUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onSelectExtras: () -> Unit,
    onClean: () -> Unit,
    onIgnore: (JunkItem) -> Unit,
    onOpenAppDetails: (String) -> Unit,
    clearAllAppCacheIntent: () -> android.content.Intent?,
    onAppCacheCleared: () -> Unit,
    onClearAllCaches: () -> Unit,
) {
    val category = state.category
    var confirming by rememberSaveable { mutableStateOf(false) }
    var confirmClearAll by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.cleaner_back))
                    }
                },
                title = {
                    Column {
                        Text(category.label(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        state.group?.let { group ->
                            Text(
                                stringResource(
                                    R.string.cleaner_summary,
                                    pluralStringResource(UiR.plurals.ui_items, group.itemCount, group.itemCount),
                                    formatBytes(group.totalBytes),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if ((!category.isAppBased || state.privileged) && state.items.isNotEmpty()) {
                CleanBar(state, onClean = { confirming = true })
            }
        },
    ) { padding ->
        val group = state.group
        when {
            group == null -> MessageContent(
                icon = category.icon,
                title = stringResource(R.string.cleaner_no_scan_title),
                body = stringResource(R.string.cleaner_no_scan_body),
                modifier = Modifier.padding(padding),
            )
            group.items.isEmpty() -> MessageContent(
                icon = Icons.Outlined.CheckCircle,
                title = stringResource(R.string.cleaner_done_title),
                body = stringResource(R.string.cleaner_done_body),
                modifier = Modifier.padding(padding),
            )
            category.isAppBased && !state.privileged ->
                AppCacheList(state, padding, onOpenAppDetails, clearAllAppCacheIntent, onAppCacheCleared)
            category.isPhotoBased -> PhotoGrid(state, padding, onToggle, onIgnore, onSelectAll, onSelectNone, onSelectExtras)
            else -> FileList(state, padding, onToggle, onIgnore, onSelectAll, onSelectNone, onSelectExtras) {
                if (category.isAppBased) {
                    Text(
                        stringResource(R.string.cleaner_shizuku_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.clearingAll) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.cleaner_clear_all_running), style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedButton(onClick = { confirmClearAll = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cleaner_clear_all))
                        }
                    }
                }
            }
        }
    }

    if (confirming) {
        val isEmptyFolders = category == JunkCategory.EMPTY_FOLDERS
        val count = state.selectedItems.size
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = {
                Text(
                    when {
                        isEmptyFolders -> stringResource(R.string.cleaner_confirm_empty_title, count)
                        category.isAppBased -> stringResource(R.string.cleaner_app_cache_confirm_title, count)
                        state.selectionIsOnlyPrivileged -> stringResource(R.string.cleaner_confirm_delete_title, count)
                        else -> stringResource(R.string.cleaner_confirm_title, count)
                    },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.sm)) {
                    Text(
                        when {
                            isEmptyFolders -> stringResource(R.string.cleaner_confirm_empty_body)
                            category.isAppBased -> stringResource(R.string.cleaner_app_cache_confirm_body)
                            state.selectionIsOnlyPrivileged ->
                                stringResource(R.string.cleaner_confirm_delete_body, formatBytes(state.selectedBytes))
                            else -> stringResource(R.string.cleaner_confirm_body, formatBytes(state.selectedBytes), state.retentionDays)
                        },
                    )
                    if (state.selectionHasPrivilegedPaths) {
                        Text(stringResource(R.string.cleaner_privileged_note), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirming = false
                    onClean()
                }) { Text(stringResource(R.string.cleaner_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cleaner_cancel)) }
            },
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(stringResource(R.string.cleaner_clear_all_title)) },
            text = { Text(stringResource(R.string.cleaner_clear_all_body)) },
            confirmButton = {
                Button(onClick = {
                    confirmClearAll = false
                    onClearAllCaches()
                }) { Text(stringResource(R.string.cleaner_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text(stringResource(R.string.cleaner_cancel)) }
            },
        )
    }
}

@Composable
private fun CleanBar(state: CleanCategoryUiState, onClean: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(SysCleanTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.sm),
        ) {
            val progress = state.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.cleaner_progress, progress.done, progress.total))
            } else {
                Button(
                    onClick = onClean,
                    enabled = state.selected.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CLEAN_BUTTON_TAG),
                ) {
                    Text(stringResource(R.string.cleaner_clean_button, state.selected.size, formatBytes(state.selectedBytes)))
                }
            }
        }
    }
}

@Composable
private fun SelectionHeader(
    state: CleanCategoryUiState,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onSelectExtras: () -> Unit,
) {
    Column(Modifier.padding(horizontal = SysCleanTheme.spacing.lg)) {
        Text(
            state.category.description(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.cleaner_selected, state.selected.size, formatBytes(state.selectedBytes)),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (state.category.isGrouped) {
                TextButton(onClick = onSelectExtras) { Text(stringResource(R.string.cleaner_select_extras)) }
            }
            if (state.selected.size == state.items.size) {
                TextButton(onClick = onSelectNone) { Text(stringResource(R.string.cleaner_select_none)) }
            } else {
                TextButton(onClick = onSelectAll) { Text(stringResource(R.string.cleaner_select_all)) }
            }
        }
    }
}

/** Items in display order: sets stay together, biggest sets first. */
private fun orderedSets(items: List<JunkItem>): List<List<JunkItem>> =
    items.groupBy { it.groupId }.values
        .map { set -> set.sortedWith(compareByDescending<JunkItem> { it.isOriginal }.thenByDescending { it.sizeBytes }) }
        .sortedByDescending { set -> set.sumOf { it.sizeBytes } }

@Composable
private fun FileList(
    state: CleanCategoryUiState,
    padding: PaddingValues,
    onToggle: (String) -> Unit,
    onIgnore: (JunkItem) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onSelectExtras: () -> Unit,
    header: @Composable ColumnScope.() -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
    ) {
        item {
            Column(
                Modifier.padding(horizontal = SysCleanTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(SysCleanTheme.spacing.sm),
                content = header,
            )
        }
        item { SelectionHeader(state, onSelectAll, onSelectNone, onSelectExtras) }
        if (state.category.isGrouped) {
            orderedSets(state.items).forEachIndexed { index, set ->
                groupHeader(index, set.size)
                items(set, key = { it.path }) { FileRow(it, state, onToggle, onIgnore) }
            }
        } else {
            items(state.items, key = { it.path }) { FileRow(it, state, onToggle, onIgnore) }
        }
    }
}

private fun LazyListScope.groupHeader(index: Int, size: Int) {
    item(key = "header-$index") {
        Text(
            stringResource(R.string.cleaner_group, index + 1, size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = SysCleanTheme.spacing.lg, top = SysCleanTheme.spacing.md),
        )
    }
}

@Composable
private fun FileRow(item: JunkItem, state: CleanCategoryUiState, onToggle: (String) -> Unit, onIgnore: (JunkItem) -> Unit) {
    val checked = item.path in state.selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = state.progress == null) { onToggle(item.path) }
            .padding(start = SysCleanTheme.spacing.sm, end = SysCleanTheme.spacing.xs, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(SysCleanTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            item.fullCacheBytes?.let {
                Text(
                    stringResource(R.string.cleaner_full_cache, formatBytes(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item.note?.let { StatusChip(it.label(), Status.NEUTRAL, Modifier.padding(top = 2.dp)) }
                if (item.isOriginal) StatusChip(stringResource(R.string.cleaner_keep), Status.GOOD, Modifier.padding(top = 2.dp))
            }
        }
        if (state.category != JunkCategory.EMPTY_FOLDERS) {
            Text(formatBytes(item.sizeBytes), style = MaterialTheme.typography.bodySmall)
        }
        ItemMenu(item, onIgnore)
    }
}

@Composable
private fun ItemMenu(item: JunkItem, onIgnore: (JunkItem) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        // Naming the item lets TalkBack users tell a long list of identical buttons apart.
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, "${stringResource(R.string.cleaner_more)} ${item.title}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cleaner_ignore)) },
                onClick = {
                    open = false
                    onIgnore(item)
                },
            )
        }
    }
}

@Composable
private fun PhotoGrid(
    state: CleanCategoryUiState,
    padding: PaddingValues,
    onToggle: (String) -> Unit,
    onIgnore: (JunkItem) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onSelectExtras: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = SysCleanTheme.spacing.sm,
            end = SysCleanTheme.spacing.sm,
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { SelectionHeader(state, onSelectAll, onSelectNone, onSelectExtras) }
        if (state.category.isGrouped) {
            orderedSets(state.items).forEachIndexed { index, set ->
                gridHeader(index, set.size)
                items(set, key = { it.path }) { PhotoCell(it, state, onToggle, onIgnore) }
            }
        } else {
            items(state.items, key = { it.path }) { PhotoCell(it, state, onToggle, onIgnore) }
        }
    }
}

private fun LazyGridScope.gridHeader(index: Int, size: Int) {
    item(key = "header-$index", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            stringResource(R.string.cleaner_group, index + 1, size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = SysCleanTheme.spacing.sm, top = SysCleanTheme.spacing.md),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoCell(item: JunkItem, state: CleanCategoryUiState, onToggle: (String) -> Unit, onIgnore: (JunkItem) -> Unit) {
    val checked = item.path in state.selected
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .then(if (checked) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(
                enabled = state.progress == null,
                onClick = { onToggle(item.path) },
                onLongClick = { menu = true },
            ),
    ) {
        MediaThumbnail(item.path, item.mediaId, Modifier.fillMaxSize())
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (item.isOriginal) StatusChip(stringResource(R.string.cleaner_keep), Status.GOOD)
            StatusChip(formatBytes(item.sizeBytes), Status.NEUTRAL)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cleaner_ignore)) },
                onClick = {
                    menu = false
                    onIgnore(item)
                },
            )
        }
    }
}

@Composable
private fun AppCacheList(
    state: CleanCategoryUiState,
    padding: PaddingValues,
    onOpenAppDetails: (String) -> Unit,
    clearAllAppCacheIntent: () -> android.content.Intent?,
    onAppCacheCleared: () -> Unit,
) {
    val spacing = SysCleanTheme.spacing
    val intent = remember { clearAllAppCacheIntent() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) onAppCacheCleared()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    stringResource(if (intent != null) R.string.cleaner_app_cache_hint else R.string.cleaner_app_cache_legacy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (intent != null) {
                    Button(onClick = { launcher.launch(intent) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.cleaner_app_cache_button))
                    }
                }
                Spacer(Modifier.width(spacing.sm))
            }
        }
        items(state.items, key = { it.path }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenAppDetails(item.path) }
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.cleaner_app_open),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(formatBytes(item.sizeBytes), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
