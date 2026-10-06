package vn.sysclean.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.sysclean.core.data.repository.WhitelistRepository
import vn.sysclean.core.designsystem.component.MessageContent
import vn.sysclean.core.designsystem.theme.SysCleanTheme
import vn.sysclean.core.model.WhitelistEntry
import javax.inject.Inject

@HiltViewModel
class WhitelistViewModel @Inject constructor(
    private val whitelistRepository: WhitelistRepository,
) : ViewModel() {
    val entries: StateFlow<List<WhitelistEntry>> = whitelistRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remove(entry: WhitelistEntry) {
        viewModelScope.launch { whitelistRepository.remove(entry.path) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhitelistScreen(onBack: () -> Unit, viewModel: WhitelistViewModel = hiltViewModel()) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.whitelist_back)) }
                },
                title = { Text(stringResource(R.string.whitelist_title)) },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            MessageContent(
                icon = Icons.Outlined.VisibilityOff,
                title = stringResource(R.string.whitelist_empty_title),
                body = stringResource(R.string.whitelist_empty_body),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            ) {
                items(entries, key = { it.path }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = SysCleanTheme.spacing.lg, end = SysCleanTheme.spacing.xs, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.path.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                entry.path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                        }
                        IconButton(onClick = { viewModel.remove(entry) }) {
                            Icon(Icons.Outlined.Close, "${stringResource(R.string.whitelist_remove)} ${entry.path.substringAfterLast('/')}")
                        }
                    }
                }
            }
        }
    }
}
