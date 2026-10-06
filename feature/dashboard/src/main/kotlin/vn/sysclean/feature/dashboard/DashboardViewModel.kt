package vn.sysclean.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.sysclean.core.cleaner.JunkCleaner
import vn.sysclean.core.data.repository.DeviceInfoRepository
import vn.sysclean.core.data.repository.HealthRepository
import vn.sysclean.core.data.repository.HealthSnapshot
import vn.sysclean.core.data.repository.UserPreferencesRepository
import vn.sysclean.core.domain.HealthScore
import vn.sysclean.core.model.AccessState
import vn.sysclean.core.model.BatteryInfo
import vn.sysclean.core.model.CleanResult
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.JunkItem
import vn.sysclean.core.model.JunkReport
import vn.sysclean.core.model.MemoryInfo
import vn.sysclean.core.model.ScanState
import vn.sysclean.core.model.StorageInfo
import vn.sysclean.core.model.isAppBased
import vn.sysclean.core.privilege.AccessRepository
import vn.sysclean.core.scanner.JunkScanner
import javax.inject.Inject

data class QuickCleanProgress(val done: Int, val total: Int)

data class DashboardUiState(
    val deviceName: String = "",
    /** Shared with the home-screen widget, so both always show the same score. */
    val health: HealthSnapshot = HealthSnapshot(),
    val access: AccessState = AccessState.Unknown,
    val scan: ScanState = ScanState.Idle,
    val cleaning: QuickCleanProgress? = null,
    val retentionDays: Int = UserPreferencesRepository.DEFAULT_RETENTION_DAYS,
) {
    /** Safe junk that can be removed in one tap; app cache needs the system dialog instead. */
    val quickCleanBytes: Long
        get() = (scan as? ScanState.Finished)?.report?.groups
            ?.filter { it.category.isSafeToClean && !it.category.isAppBased }
            ?.sumOf { it.totalBytes } ?: 0

    val storage: StorageInfo? get() = health.storage
    val memory: MemoryInfo? get() = health.memory
    val battery: BatteryInfo? get() = health.battery
    val healthScore: HealthScore? get() = health.score
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    deviceInfoRepository: DeviceInfoRepository,
    healthRepository: HealthRepository,
    accessRepository: AccessRepository,
    private val junkScanner: JunkScanner,
    private val junkCleaner: JunkCleaner,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    private val cleaning = MutableStateFlow<QuickCleanProgress?>(null)
    private val events = Channel<CleanResult>(Channel.BUFFERED)

    /** Results of quick cleans, for the snackbar with undo. */
    val cleanResults: Flow<CleanResult> = events.receiveAsFlow()
    private var lastCleaned: Map<JunkCategory, List<JunkItem>> = emptyMap()

    private val deviceName = flow {
        val overview = deviceInfoRepository.overview()
        emit("${overview.manufacturer} ${overview.model}")
    }.onStart { emit("") }

    val uiState: StateFlow<DashboardUiState> = combine(
        healthRepository.observe(),
        combine(accessRepository.state, deviceName) { access, name -> access to name },
        combine(junkScanner.state, cleaning, preferences.trashRetentionDays, ::Triple),
    ) { health, (access, name), (scan, progress, days) ->
        DashboardUiState(name, health, access, scan, progress, days)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun startScan() = junkScanner.start()

    fun cancelScan() = junkScanner.cancel()

    fun quickClean() {
        val report = (junkScanner.state.value as? ScanState.Finished)?.report ?: return
        if (cleaning.value != null) return
        viewModelScope.launch {
            cleaning.value = QuickCleanProgress(0, 0)
            val result = junkCleaner.cleanSafe(report) { done, total -> cleaning.value = QuickCleanProgress(done, total) }
            lastCleaned = cleanedSince(report)
            cleaning.value = null
            events.send(result)
        }
    }

    fun undo(result: CleanResult) {
        viewModelScope.launch {
            junkCleaner.undo(result)
            lastCleaned.forEach { (category, items) -> junkScanner.restoreItems(category, items) }
            lastCleaned = emptyMap()
        }
    }

    /** Items present in [before] that the current report no longer lists. */
    private fun cleanedSince(before: JunkReport): Map<JunkCategory, List<JunkItem>> {
        val now = (junkScanner.state.value as? ScanState.Finished)?.report ?: return emptyMap()
        return before.groups.associate { group ->
            val remaining = now.groups.firstOrNull { it.category == group.category }?.items.orEmpty().map { it.path }.toSet()
            group.category to group.items.filter { it.path !in remaining }
        }.filterValues { it.isNotEmpty() }
    }
}
