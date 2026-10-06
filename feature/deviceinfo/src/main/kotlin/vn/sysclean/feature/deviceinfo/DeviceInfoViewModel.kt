package vn.sysclean.feature.deviceinfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import vn.sysclean.core.data.repository.BatteryRepository
import vn.sysclean.core.data.repository.DeviceInfoRepository
import vn.sysclean.core.data.repository.StorageRepository
import vn.sysclean.core.model.BatteryInfo
import vn.sysclean.core.model.CameraItem
import vn.sysclean.core.model.CpuInfo
import vn.sysclean.core.model.DeviceOverview
import vn.sysclean.core.model.DisplayInfo
import vn.sysclean.core.model.MemoryInfo
import vn.sysclean.core.model.SecurityInfo
import vn.sysclean.core.model.SensorItem
import vn.sysclean.core.model.StorageInfo
import javax.inject.Inject

/** Facts that do not change while the screen is open; loaded once, in parallel. */
data class StaticDeviceInfo(
    val overview: DeviceOverview,
    val display: DisplayInfo,
    val storage: StorageInfo,
    val sensors: List<SensorItem>,
    val cameras: List<CameraItem>,
    val security: SecurityInfo,
)

@HiltViewModel
class DeviceInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val deviceInfoRepository: DeviceInfoRepository,
    private val storageRepository: StorageRepository,
    batteryRepository: BatteryRepository,
) : ViewModel() {

    val initialTab: DeviceTab = savedStateHandle.toRoute<DeviceInfoRoute>().tab

    val staticInfo: StateFlow<StaticDeviceInfo?> = flow { emit(loadStatic()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val cpu: StateFlow<CpuInfo?> = deviceInfoRepository.cpu()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val memory: StateFlow<MemoryInfo?> = deviceInfoRepository.memory(intervalMillis = 1_000)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val battery: StateFlow<BatteryInfo?> = batteryRepository.battery()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private suspend fun loadStatic(): StaticDeviceInfo = coroutineScope {
        val overview = async { deviceInfoRepository.overview() }
        val display = async { deviceInfoRepository.display() }
        val storage = async { storageRepository.storageInfo() }
        val sensors = async { deviceInfoRepository.sensors() }
        val cameras = async { deviceInfoRepository.cameras() }
        val security = async { deviceInfoRepository.security() }
        StaticDeviceInfo(overview.await(), display.await(), storage.await(), sensors.await(), cameras.await(), security.await())
    }
}
