package vn.sysclean.feature.apps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.sysclean.core.data.repository.AppsRepository
import vn.sysclean.core.model.AppInfo
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.AccessLevel
import vn.sysclean.core.privilege.AccessRepository
import vn.sysclean.core.privilege.shell.SystemActions
import vn.sysclean.core.scanner.JunkScanner
import javax.inject.Inject

/**
 * A batch uninstall in progress. Without Shizuku the system dialog is shown for [current],
 * one app at a time; with Shizuku ([silent]) the apps are removed directly.
 */
data class UninstallQueue(
    val packages: List<String>,
    val index: Int = 0,
    val removed: Int = 0,
    val silent: Boolean = false,
) {
    val current: String? get() = packages.getOrNull(index)
}

data class AppsUiState(
    val loading: Boolean = true,
    val query: AppListQuery = AppListQuery(),
    val apps: List<AppInfo> = emptyList(),
    val hasUsageAccess: Boolean = true,
    val now: Long = System.currentTimeMillis(),
    /** Non-null while in multi-select mode. */
    val selection: Set<String>? = null,
    val uninstall: UninstallQueue? = null,
    /** Shizuku is ready: force stop and silent uninstall are available. */
    val privileged: Boolean = false,
) {
    val totalBytes: Long get() = apps.sumOf { it.size?.totalBytes ?: 0 }
    val cacheBytes: Long get() = apps.sumOf { it.size?.cacheBytes ?: 0 }
    val selectedApps: List<AppInfo> get() = apps.filter { it.packageName in selection.orEmpty() }
}

sealed interface AppsEvent {
    data class Uninstalled(val count: Int) : AppsEvent
    data class ForceStopped(val count: Int) : AppsEvent
}

@HiltViewModel
class AppsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appsRepository: AppsRepository,
    private val junkScanner: JunkScanner,
    private val systemActions: SystemActions,
    accessRepository: AccessRepository,
) : ViewModel() {

    private val query = MutableStateFlow(
        AppListQuery(filter = savedStateHandle.toRoute<AppsRoute>().filter?.let(AppFilter::valueOf) ?: AppFilter.USER),
    )
    private val selection = MutableStateFlow<Set<String>?>(null)
    private val uninstall = MutableStateFlow<UninstallQueue?>(null)
    private val reload = MutableStateFlow(0)
    private val events = Channel<AppsEvent>(Channel.BUFFERED)

    /** Results of finished batches, for the snackbar. */
    val eventFlow: Flow<AppsEvent> = events.receiveAsFlow()

    private val usageAccess = accessRepository.state.map { it.usageAccess }.distinctUntilChanged()
    private val privileged = accessRepository.state.map { it.activeLevel != AccessLevel.NORMAL }.distinctUntilChanged()

    // The repository drops its cache itself when usage access changes, so a recent scan's list is reused.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val allApps = combine(usageAccess, reload) { _, generation -> generation }
        .mapLatest { generation -> appsRepository.installedApps(forceRefresh = generation > 0) }
        .map<List<AppInfo>, List<AppInfo>?> { it }
        .onStart { emit(null) }

    val uiState: StateFlow<AppsUiState> = combine(
        allApps,
        query,
        combine(usageAccess, privileged, ::Pair),
        selection,
        uninstall,
    ) { apps, query, (usage, shizuku), selected, queue ->
        val now = System.currentTimeMillis()
        AppsUiState(
            loading = apps == null,
            query = query,
            apps = apps?.applyQuery(query, now).orEmpty(),
            hasUsageAccess = usage,
            now = now,
            selection = selected,
            uninstall = queue,
            privileged = shizuku,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    fun onSearch(text: String) = query.update { it.copy(text = text) }

    fun onFilter(filter: AppFilter) = query.update { it.copy(filter = filter) }

    fun onSort(sort: AppSort) = query.update { it.copy(sort = sort) }

    fun startSelection(app: AppInfo) {
        if (app.canUninstall()) selection.value = setOf(app.packageName)
    }

    fun toggleSelection(app: AppInfo) {
        if (!app.canUninstall()) return
        selection.update { current ->
            val next = current.orEmpty().let { if (app.packageName in it) it - app.packageName else it + app.packageName }
            next.ifEmpty { null }
        }
    }

    fun selectAllShown() {
        selection.value = uiState.value.apps.filter { it.canUninstall() }.map { it.packageName }.toSet().ifEmpty { null }
    }

    fun clearSelection() {
        selection.value = null
    }

    fun uninstall(packages: List<String>) {
        if (packages.isEmpty() || uninstall.value != null) return
        if (!systemActions.isReady) {
            uninstall.value = UninstallQueue(packages)
            return
        }
        viewModelScope.launch {
            var queue = UninstallQueue(packages, silent = true)
            uninstall.value = queue
            packages.forEach { pkg ->
                val ok = runCatching { systemActions.uninstall(pkg) }.getOrDefault(false)
                queue = queue.copy(index = queue.index + 1, removed = queue.removed + if (ok) 1 else 0)
                uninstall.value = queue
            }
            finishUninstall(queue)
        }
    }

    fun forceStop(packages: List<String>) {
        viewModelScope.launch {
            val stopped = packages.count { runCatching { systemActions.forceStop(it) }.getOrDefault(false) }
            selection.value = null
            events.send(AppsEvent.ForceStopped(stopped))
        }
    }

    /** Called when the system dialog for the current app closes, whatever the user chose. */
    fun onUninstallDialogClosed() {
        val queue = uninstall.value ?: return
        val current = queue.current ?: return
        val removed = queue.removed + if (appsRepository.isInstalled(current)) 0 else 1
        val next = queue.copy(index = queue.index + 1, removed = removed)
        if (next.current != null) {
            uninstall.value = next
            return
        }
        finishUninstall(next)
    }

    private fun finishUninstall(queue: UninstallQueue) {
        uninstall.value = null
        selection.value = null
        val gone = queue.packages.filterNot(appsRepository::isInstalled).toSet()
        junkScanner.removeItems(JunkCategory.APP_CACHE, gone)
        reload.update { it + 1 }
        viewModelScope.launch { events.send(AppsEvent.Uninstalled(gone.size)) }
    }

    private fun AppInfo.canUninstall() = !isSystem && packageName != SELF_PACKAGE

    private companion object {
        const val SELF_PACKAGE = "vn.sysclean"
    }
}
