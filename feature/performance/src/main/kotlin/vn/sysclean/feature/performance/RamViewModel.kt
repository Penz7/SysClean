package vn.sysclean.feature.performance

import androidx.lifecycle.ViewModel
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
import vn.sysclean.core.data.repository.AppsRepository
import vn.sysclean.core.performance.AppKind
import vn.sysclean.core.performance.AppMemory
import vn.sysclean.core.performance.MemoryGroup
import vn.sysclean.core.performance.RamManager
import vn.sysclean.core.performance.RamOverview
import vn.sysclean.core.performance.ReducedApp
import javax.inject.Inject

data class RamUiState(
    val loading: Boolean = true,
    val overview: RamOverview? = null,
    /** Idle user apps not in RAM right now: package to label. */
    val suggestions: List<Pair<String, String>> = emptyList(),
    /** Asleep or disabled, whether or not in RAM. */
    val reduced: List<ReducedApp> = emptyList(),
    val busy: Set<String> = emptySet(),
) {
    private val apps get() = overview?.apps.orEmpty()

    /** What the user can still act on, heaviest first. Cache-only apps are left out: Android frees those itself. */
    val reducible: List<AppMemory>
        get() = apps.filter {
            it.group != MemoryGroup.CACHED &&
                ((it.sleepable && !it.deepSleeping) || (it.kind == AppKind.OPTIONAL_SERVICE && !it.disabled))
        }

    val locked: List<AppMemory>
        get() = apps.filter { (it.kind == AppKind.LOCKED || it.kind == AppKind.SYSTEM_APP) && it.group != MemoryGroup.CACHED }

    fun groupKb(group: MemoryGroup): Long = apps.filter { it.group == group }.sumOf { it.pssKb }
}

sealed interface RamEvent {
    data class Slept(val app: AppMemory) : RamEvent
    data class Woken(val label: String) : RamEvent
    data class Disabled(val label: String) : RamEvent
    data class Enabled(val label: String) : RamEvent
    data class Failed(val label: String) : RamEvent
}

@HiltViewModel
class RamViewModel @Inject constructor(
    private val ramManager: RamManager,
    private val appsRepository: AppsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RamUiState())
    val uiState: StateFlow<RamUiState> = _state.asStateFlow()
    private val events = Channel<RamEvent>(Channel.BUFFERED)
    val eventFlow: Flow<RamEvent> = events.receiveAsFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val overview = runCatching { ramManager.overview() }.getOrNull()
            val inRam = overview?.apps.orEmpty().map { it.packageName }.toSet()
            val labels = appsRepository.installedApps().associate { it.packageName to it.label }
            val suggestions = runCatching { ramManager.deepSleepCandidates() }.getOrDefault(emptyList())
                .filter { it !in inRam }
                .map { it to (labels[it] ?: it) }
            val reduced = runCatching { ramManager.reducedApps() }.getOrDefault(emptyList())
            _state.update { it.copy(loading = false, overview = overview ?: it.overview, suggestions = suggestions, reduced = reduced) }
        }
    }

    fun deepSleep(app: AppMemory) = act(app.packageName) {
        if (ramManager.deepSleep(app.packageName)) {
            // Show the result at once; the full re-measure that follows takes a few seconds.
            moveToReduced(app.packageName, app.label, disabled = false)
            RamEvent.Slept(app)
        } else {
            RamEvent.Failed(app.label)
        }
    }

    fun wake(packageName: String, label: String) = act(packageName) {
        if (ramManager.wake(packageName)) {
            _state.update { it.copy(reduced = it.reduced.filterNot { r -> r.packageName == packageName }) }
            RamEvent.Woken(label)
        } else {
            RamEvent.Failed(label)
        }
    }

    private fun moveToReduced(packageName: String, label: String, disabled: Boolean) = _state.update { state ->
        state.copy(
            overview = state.overview?.copy(apps = state.overview.apps.filterNot { it.packageName == packageName }),
            reduced = (state.reduced.filterNot { it.packageName == packageName } + ReducedApp(packageName, label, disabled))
                .sortedBy { it.label.lowercase() },
        )
    }

    fun disable(app: AppMemory) = act(app.packageName) {
        if (ramManager.disableService(app.packageName)) {
            moveToReduced(app.packageName, app.label, disabled = true)
            RamEvent.Disabled(app.label)
        } else {
            RamEvent.Failed(app.label)
        }
    }

    fun enable(packageName: String, label: String) = act(packageName) {
        if (ramManager.enableService(packageName)) {
            _state.update { it.copy(reduced = it.reduced.filterNot { r -> r.packageName == packageName }) }
            RamEvent.Enabled(label)
        } else {
            RamEvent.Failed(label)
        }
    }

    fun sleepSuggestions() {
        val packages = _state.value.suggestions.map { it.first }
        viewModelScope.launch {
            _state.update { it.copy(busy = it.busy + packages) }
            packages.forEach { runCatching { ramManager.deepSleep(it) } }
            _state.update { it.copy(busy = it.busy - packages.toSet()) }
            reload()
        }
    }

    private fun act(packageName: String, block: suspend () -> RamEvent) {
        if (packageName in _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = it.busy + packageName) }
            val event = runCatching { block() }.getOrElse { RamEvent.Failed(packageName) }
            _state.update { it.copy(busy = it.busy - packageName) }
            events.send(event)
            reload()
        }
    }
}
