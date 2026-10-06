package vn.sysclean.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import vn.sysclean.core.data.repository.AppsRepository
import vn.sysclean.core.model.PreinstalledApp
import vn.sysclean.core.model.AccessLevel
import vn.sysclean.core.privilege.AccessRepository
import vn.sysclean.core.privilege.shell.BloatReason
import vn.sysclean.core.privilege.shell.BloatwarePolicy
import vn.sysclean.core.privilege.shell.SystemActions
import javax.inject.Inject

data class BloatwareUiState(
    val loading: Boolean = true,
    val shizukuReady: Boolean = false,
    /** Recommended packages still present and enabled. */
    val removable: List<PreinstalledApp> = emptyList(),
    /** Other pre-installed apps the user has not opened for a month: found on any brand. */
    val unused: List<PreinstalledApp> = emptyList(),
    /** Without usage access, unused apps cannot be found. */
    val usageAccess: Boolean = true,
    /** Every pre-installed package removed for this user or turned off. */
    val restorable: List<PreinstalledApp> = emptyList(),
    /** Packages with an action in flight. */
    val busy: Set<String> = emptySet(),
)

enum class BloatAction { REMOVED, DISABLED, RESTORED, FAILED }

data class BloatEvent(val action: BloatAction, val label: String)

@HiltViewModel
class BloatwareViewModel @Inject constructor(
    private val appsRepository: AppsRepository,
    private val systemActions: SystemActions,
    accessRepository: AccessRepository,
) : ViewModel() {

    private val apps = MutableStateFlow<List<PreinstalledApp>?>(null)
    private val unusedPackages = MutableStateFlow<Set<String>>(emptySet())
    private val busy = MutableStateFlow<Set<String>>(emptySet())
    private val events = Channel<BloatEvent>(Channel.BUFFERED)
    val eventFlow: Flow<BloatEvent> = events.receiveAsFlow()

    val uiState: StateFlow<BloatwareUiState> = combine(apps, unusedPackages, busy, accessRepository.state) { list, unused, working, access ->
        val inUse = appsRepository.packagesInActiveUse()
        val all = list.orEmpty()
        BloatwareUiState(
            loading = list == null,
            shizukuReady = access.activeLevel != AccessLevel.NORMAL,
            removable = all.filter { BloatwarePolicy.isRecommended(it.packageName, inUse) && !it.isRemoved },
            unused = all.filter {
                it.packageName in unused && it.packageName !in BloatwarePolicy.recommended &&
                    !BloatwarePolicy.isProtected(it.packageName, inUse) && !it.isRemoved
            },
            usageAccess = access.usageAccess,
            restorable = all.filter { it.isRemoved },
            busy = working,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BloatwareUiState())

    init {
        reload()
    }

    fun reasonOf(packageName: String): BloatReason? =
        BloatwarePolicy.recommended[packageName] ?: BloatReason.UNUSED_PRELOAD.takeIf { packageName in unusedPackages.value }

    fun remove(app: PreinstalledApp) = act(app, BloatAction.REMOVED) { systemActions.removeForUser(it) }

    fun disable(app: PreinstalledApp) = act(app, BloatAction.DISABLED) { systemActions.disable(it) }

    /** Undoes either kind of removal: reinstalls for this user and re-enables. */
    fun restore(app: PreinstalledApp) = act(app, BloatAction.RESTORED) { pkg ->
        val installed = app.installedForUser || systemActions.restore(pkg)
        installed && (!app.disabledByUser || systemActions.enable(pkg))
    }

    private fun act(app: PreinstalledApp, action: BloatAction, block: suspend (String) -> Boolean) {
        if (app.packageName in busy.value) return
        viewModelScope.launch {
            busy.update { it + app.packageName }
            val ok = runCatching { block(app.packageName) }.getOrDefault(false)
            reload()
            busy.update { it - app.packageName }
            events.send(BloatEvent(if (ok) action else BloatAction.FAILED, app.label))
        }
    }

    private fun reload() {
        viewModelScope.launch {
            val unused = runCatching { appsRepository.unusedPreinstalled() }.getOrDefault(emptySet())
            val known = appsRepository.preinstalled(BloatwarePolicy.recommended.keys + unused)
            val disabled = runCatching { appsRepository.disabledPreinstalled() }.getOrDefault(emptyList())
            unusedPackages.value = unused
            apps.value = (known + disabled).distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        }
    }
}
