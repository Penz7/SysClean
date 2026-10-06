package vn.sysclean.feature.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.sysclean.core.data.repository.UserPreferencesRepository
import vn.sysclean.core.data.repository.WhitelistRepository
import vn.sysclean.core.model.AccessState
import vn.sysclean.core.privilege.AccessRepository
import vn.sysclean.core.privilege.ShizukuQuirk
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accessRepository: AccessRepository,
    private val preferences: UserPreferencesRepository,
    whitelistRepository: WhitelistRepository,
) : ViewModel() {

    val access: StateFlow<AccessState> = accessRepository.state

    val retentionDays: StateFlow<Int> = preferences.trashRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferencesRepository.DEFAULT_RETENTION_DAYS)

    val whitelistCount: StateFlow<Int> = whitelistRepository.observe().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setRetentionDays(days: Int) {
        viewModelScope.launch { preferences.setTrashRetentionDays(days) }
    }

    val shizukuQuirk: ShizukuQuirk? = accessRepository.shizukuQuirk
    val hasWirelessDebugging: Boolean = accessRepository.hasWirelessDebugging

    val needsLegacyStoragePermission: Boolean = accessRepository.needsLegacyStoragePermission
    val legacyStoragePermissions: Array<String> = accessRepository.legacyStoragePermissions

    fun allFilesAccessIntents(): Pair<Intent, Intent> =
        accessRepository.allFilesAccessIntent() to accessRepository.allFilesAccessFallbackIntent()

    fun usageAccessIntents(): Pair<Intent, Intent> =
        accessRepository.usageAccessIntent() to accessRepository.usageAccessFallbackIntent()

    fun shizukuLaunchIntent(): Intent? = accessRepository.shizukuLaunchIntent()

    fun shizukuInstallIntents(): Pair<Intent, Intent> = accessRepository.shizukuInstallIntents()

    fun developerOptionsIntents(): Pair<Intent, Intent> =
        accessRepository.developerOptionsIntent() to accessRepository.aboutPhoneIntent()

    fun requestShizukuPermission() = accessRepository.requestShizukuPermission()

    fun refresh() = accessRepository.refresh()

    private val _enablingRoot = MutableStateFlow(false)
    val enablingRoot: StateFlow<Boolean> = _enablingRoot.asStateFlow()

    private val rootEvents = Channel<Boolean>(Channel.BUFFERED)

    /** true = granted, false = denied; for the snackbar. */
    val rootResults: Flow<Boolean> = rootEvents.receiveAsFlow()

    fun enableRootMode() {
        if (_enablingRoot.value) return
        viewModelScope.launch {
            _enablingRoot.value = true
            val granted = accessRepository.enableRootMode()
            _enablingRoot.value = false
            rootEvents.send(granted)
        }
    }

    fun disableRootMode() = accessRepository.disableRootMode()
}
