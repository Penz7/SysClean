package vn.sysclean.feature.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.sysclean.core.model.AccessLevel
import vn.sysclean.core.performance.Baseline
import vn.sysclean.core.performance.OptimizeProgress
import vn.sysclean.core.performance.OptimizeResult
import vn.sysclean.core.performance.OptimizeStep
import vn.sysclean.core.performance.Optimizer
import vn.sysclean.core.performance.PerformanceReport
import vn.sysclean.core.performance.PerformanceRepository
import vn.sysclean.core.performance.PerformanceStore
import vn.sysclean.core.performance.RamManager
import vn.sysclean.core.privilege.AccessRepository
import javax.inject.Inject

data class PerformanceUiState(
    val measuring: Boolean = true,
    val report: PerformanceReport? = null,
    val privileged: Boolean = false,
    val baseline: Baseline? = null,
    /** Null until the user changes it: then defaults follow what the report found. */
    val chosenSteps: Set<OptimizeStep>? = null,
    val progress: OptimizeProgress? = null,
    val result: OptimizeResult? = null,
) {
    /** Defaults are the steps with no downside; deep sleep changes app behaviour, so it is opt-in. */
    val steps: Set<OptimizeStep>
        get() = chosenSteps ?: buildSet {
            if (report?.unoptimizedApps.orEmpty().isNotEmpty()) add(OptimizeStep.COMPILE_APPS)
            add(OptimizeStep.TRIM_STORAGE)
            add(OptimizeStep.FREE_RAM)
        }
}

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val repository: PerformanceRepository,
    private val optimizer: Optimizer,
    private val ramManager: RamManager,
    store: PerformanceStore,
    accessRepository: AccessRepository,
) : ViewModel() {

    private val local = MutableStateFlow(PerformanceUiState())

    val uiState: StateFlow<PerformanceUiState> = combine(local, store.baseline, accessRepository.state) { state, baseline, access ->
        state.copy(baseline = baseline, privileged = access.activeLevel != AccessLevel.NORMAL)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerformanceUiState())

    init {
        // Re-measure when advanced mode appears, so Shizuku-only figures show up without a manual refresh.
        viewModelScope.launch {
            accessRepository.state.collect { measure() }
        }
    }

    fun measure() {
        viewModelScope.launch {
            local.update { it.copy(measuring = true) }
            val report = runCatching { repository.diagnose() }.getOrNull()
            local.update { it.copy(measuring = false, report = report ?: it.report) }
        }
    }

    fun toggleStep(step: OptimizeStep) = local.update { state ->
        val steps = state.steps
        state.copy(chosenSteps = if (step in steps) steps - step else steps + step)
    }

    fun optimize() {
        val state = uiState.value
        val report = state.report ?: return
        if (state.progress != null) return
        viewModelScope.launch {
            local.update { it.copy(result = null, progress = OptimizeProgress(state.steps.first(), 0, 0, null)) }
            val result = runCatching {
                optimizer.run(state.steps, report) { progress -> local.update { it.copy(progress = progress) } }
            }.getOrNull()
            local.update { it.copy(progress = null, result = result, chosenSteps = null) }
            measure()
        }
    }

    fun setFastAnimations(fast: Boolean) {
        viewModelScope.launch {
            optimizer.setAnimationScale(if (fast) Optimizer.FAST_ANIMATIONS else Optimizer.NORMAL_ANIMATIONS)
            measure()
        }
    }

    fun restart() {
        viewModelScope.launch { ramManager.restartDevice() }
    }
}
