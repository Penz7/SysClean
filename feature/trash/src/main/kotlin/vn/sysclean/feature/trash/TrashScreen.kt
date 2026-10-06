package vn.sysclean.feature.trash

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
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.sysclean.core.common.format.formatBytes
import vn.sysclean.core.designsystem.component.LoadingContent
import vn.sysclean.core.designsystem.component.MessageContent
import vn.sysclean.core.designsystem.theme.SysCleanTheme
import vn.sysclean.core.model.TrashItem
import vn.sysclean.core.model.isPhotoBased
import vn.sysclean.core.ui.MediaThumbnail
import vn.sysclean.core.ui.expiresLabel
import vn.sysclean.core.ui.R as UiR

const val TRASH_PROGRESS_TAG = "trash_progress"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrashScreen(onBack: () -> Unit, viewModel: TrashViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<TrashItem?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            snackbar.showSnackbar(
                context.getString(
                    when (event) {
                        TrashEvent.RESTORED -> R.string.trash_restored
                        TrashEvent.DELETED -> R.string.trash_deleted
                        TrashEvent.EMPTIED -> R.string.trash_emptied
                    },
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.trash_back)) }
                },
                title = { Text(stringResource(R.string.trash_title)) },
                actions = {
                    if (state.items.isNotEmpty()) {
                        IconButton(onClick = { confirmEmpty = true }, enabled = !state.busy) {
                            Icon(Icons.Outlined.DeleteForever, stringResource(R.string.trash_empty_action))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.busy) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(top = padding.calculateTopPadding())
                    .testTag(TRASH_PROGRESS_TAG),
            )
        }
        when {
            state.loading -> LoadingContent(Modifier.padding(padding))
            state.items.isEmpty() -> MessageContent(
                icon = Icons.Outlined.DeleteOutline,
                title = stringResource(R.string.trash_nothing_title),
                body = stringResource(R.string.trash_nothing_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            ) {
                item {
                    Text(
                        stringResource(
                            R.string.trash_summary,
                            pluralStringResource(UiR.plurals.ui_items, state.items.size, state.items.size),
                            formatBytes(state.totalBytes),
                            state.retentionDays,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = SysCleanTheme.spacing.lg, vertical = SysCleanTheme.spacing.sm),
                    )
                }
                items(state.items, key = { it.id }) { item ->
                    TrashRow(
                        item = item,
                        enabled = !state.busy,
                        onRestore = { viewModel.restore(item) },
                        onDelete = { confirmDelete = item },
                    )
                }
            }
        }
    }

    if (confirmEmpty) {
        DestructiveDialog(
            title = stringResource(R.string.trash_confirm_empty_title),
            body = stringResource(R.string.trash_confirm_empty_body, formatBytes(state.totalBytes)),
            action = stringResource(R.string.trash_empty_action),
            onConfirm = { viewModel.empty() },
            onDismiss = { confirmEmpty = false },
        )
    }
    confirmDelete?.let { item ->
        DestructiveDialog(
            title = stringResource(R.string.trash_confirm_delete_title),
            body = stringResource(R.string.trash_confirm_delete_body, item.name),
            action = stringResource(R.string.trash_delete),
            onConfirm = { viewModel.delete(item) },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun TrashRow(item: TrashItem, enabled: Boolean, onRestore: () -> Unit, onDelete: () -> Unit) {
    val spacing = SysCleanTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = spacing.lg, end = spacing.xs, top = spacing.sm, bottom = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.category?.isPhotoBased == true) {
            // The file now sits in the bin under its id, so decode it from there, not via MediaStore.
            MediaThumbnail(path = item.trashPath, mediaId = null, modifier = Modifier.size(44.dp))
        } else {
            Icon(
                if (item.isDirectory) Icons.Outlined.Folder else Icons.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(spacing.md))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.originalPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(
                "${formatBytes(item.sizeBytes)} · ${expiresLabel(item.expiresAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRestore, enabled = enabled) { Icon(Icons.Outlined.Restore, "${stringResource(R.string.trash_restore)} ${item.name}") }
        IconButton(onClick = onDelete, enabled = enabled) { Icon(Icons.Outlined.DeleteForever, "${stringResource(R.string.trash_delete)} ${item.name}") }
    }
}

@Composable
private fun DestructiveDialog(title: String, body: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
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
            ) { Text(action) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.trash_cancel)) } },
    )
}
