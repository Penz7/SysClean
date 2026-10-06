package vn.sysclean.feature.cleaner

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.sysclean.core.cleaner.JunkCleaner
import vn.sysclean.core.data.repository.UserPreferencesRepository
import vn.sysclean.core.data.repository.WhitelistRepository
import vn.sysclean.core.model.CleanResult
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.JunkGroup
import vn.sysclean.core.model.JunkItem
import vn.sysclean.core.model.ScanState
import vn.sysclean.core.model.AccessLevel
import vn.sysclean.core.privilege.AccessRepository
import vn.sysclean.core.privilege.shell.PrivilegedPaths
import vn.sysclean.core.scanner.JunkScanner
import javax.inject.Inject

data class CleaningProgress(val done: Int, val total: Int)

data class CleanCategoryUiState(
    val category: JunkCategory,
    /** Null when there is no finished scan to show (e.g. the process was restarted). */
    val group: JunkGroup?,
    val selected: Set<String> = emptySet(),
    val progress: CleaningProgress? = null,
    val retentionDays: Int = UserPreferencesRepository.DEFAULT_RETENTION_DAYS,
    /** Shizuku is ready: app cache and Android/data items can be cleaned directly. */
    val privileged: Boolean = false,
    val clearingAll: Boolean = false,
) {
    val selectionHasPrivilegedPaths: Boolean
        get() = selectedItems.any { PrivilegedPaths.isUnderPrivilegedRoot(it.path) }

    /** Nothing selected goes to the bin: the dialog must say "delete for good". */
    val selectionIsOnlyPrivileged: Boolean
        get() = selectedItems.isNotEmpty() && selectedItems.all { PrivilegedPaths.isUnderPrivilegedRoot(it.path) }

    val items: List<JunkItem> get() = group?.items.orEmpty()
    val selectedItems: List<JunkItem> get() = items.filter { it.path in selected }
    val selectedBytes: Long get() = selectedItems.sumOf { it.sizeBytes }
}

sealed interface CleanerEvent {
    data class Cleaned(val result: CleanResult, val isEmptyFolders: Boolean) : CleanerEvent
    data class Restored(val count: Int) : CleanerEvent
    data object Ignored : CleanerEvent
    data object AppCacheCleared : CleanerEvent
    data class AllCachesCleared(val bytes: Long) : CleanerEvent
}

@HiltViewModel
class CleanCategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val junkScanner: JunkScanner,
    private val junkCleaner: JunkCleaner,
    private val whitelistRepository: WhitelistRepository,
    private val accessRepository: AccessRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    private val category = JunkCategory.valueOf(savedStateHandle.toRoute<CleanCategoryRoute>().category)

    /** Null until the user touches the selection, so the default follows the live report. */
    private val selection = MutableStateFlow<Set<String>?>(null)
    private val progress = MutableStateFlow<CleaningProgress?>(null)
    private val clearingAll = MutableStateFlow(false)
    private val events = Channel<CleanerEvent>(Channel.BUFFERED)
    val eventFlow: Flow<CleanerEvent> = events.receiveAsFlow()

    /** Items removed by the last clean, kept so undo can put them back in the report. */
    private var lastCleaned: List<JunkItem> = emptyList()

    val uiState: StateFlow<CleanCategoryUiState> = combine(
        junkScanner.state,
        selection,
        combine(progress, clearingAll, ::Pair),
        preferences.trashRetentionDays,
        accessRepository.state,
    ) { scan, chosen, (running, allRunning), days, access ->
        val group = (scan as? ScanState.Finished)?.report?.groups?.firstOrNull { it.category == category }
            ?: if (scan is ScanState.Finished) JunkGroup(category, 0, 0, emptyList()) else null
        val items = group?.items.orEmpty()
        val paths = items.map { it.path }.toSet()
        CleanCategoryUiState(
            category = category,
            group = group,
            selected = (chosen ?: defaultSelection(category, items)) intersect paths,
            progress = running,
            retentionDays = days,
            privileged = access.activeLevel != AccessLevel.NORMAL,
            clearingAll = allRunning,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CleanCategoryUiState(category, null))

    fun toggle(path: String) = selection.update { current ->
        val base = current ?: uiState.value.selected
        if (path in base) base - path else base + path
    }

    fun selectAll() {
        selection.value = uiState.value.items.map { it.path }.toSet()
    }

    fun selectNone() {
        selection.value = emptySet()
    }

    fun selectExtras() {
        selection.value = extrasOf(uiState.value.items)
    }

    fun clean() {
        val state = uiState.value
        val chosen = state.selectedItems
        if (chosen.isEmpty() || state.progress != null) return
        viewModelScope.launch {
            progress.value = CleaningProgress(0, chosen.size)
            val result = junkCleaner.clean(category, chosen, state.items) { done, total ->
                progress.value = CleaningProgress(done, total)
            }
            // Everything that left the report, including set survivors dropped with their copies.
            val remaining = (junkScanner.state.value as? ScanState.Finished)?.report?.groups
                ?.firstOrNull { it.category == category }?.items.orEmpty().map { it.path }.toSet()
            lastCleaned = state.items.filter { it.path !in remaining }
            progress.value = null
            selection.value = null
            events.send(CleanerEvent.Cleaned(result, category == JunkCategory.EMPTY_FOLDERS))
        }
    }

    fun undo(result: CleanResult) {
        viewModelScope.launch {
            val restored = junkCleaner.undo(result)
            junkScanner.restoreItems(category, lastCleaned)
            lastCleaned = emptyList()
            events.send(CleanerEvent.Restored(restored))
        }
    }

    fun ignore(item: JunkItem) {
        viewModelScope.launch {
            whitelistRepository.add(item.path)
            junkScanner.removeItems(category, setOf(item.path))
            events.send(CleanerEvent.Ignored)
        }
    }

    fun clearAllAppCacheIntent(): Intent? = accessRepository.clearAllAppCacheIntent()

    fun clearAllCaches() {
        if (clearingAll.value) return
        viewModelScope.launch {
            clearingAll.value = true
            val freed = junkCleaner.clearAllAppCaches()
            clearingAll.value = false
            events.send(CleanerEvent.AllCachesCleared(freed))
        }
    }

    fun onAppCacheCleared() {
        junkScanner.removeItems(JunkCategory.APP_CACHE, uiState.value.items.map { it.path }.toSet())
        viewModelScope.launch { events.send(CleanerEvent.AppCacheCleared) }
    }
}
