package vn.sysclean.core.data.repository

import kotlinx.coroutines.flow.Flow
import vn.sysclean.core.model.CameraItem
import vn.sysclean.core.model.CpuInfo
import vn.sysclean.core.model.DeviceOverview
import vn.sysclean.core.model.DisplayInfo
import vn.sysclean.core.model.MemoryInfo
import vn.sysclean.core.model.SecurityInfo
import vn.sysclean.core.model.SensorItem

interface DeviceInfoRepository {
    suspend fun overview(): DeviceOverview
    suspend fun display(): DisplayInfo
    suspend fun sensors(): List<SensorItem>
    suspend fun cameras(): List<CameraItem>
    suspend fun security(): SecurityInfo

    /** Live CPU frequencies, re-read every [intervalMillis]. */
    fun cpu(intervalMillis: Long = 1_000): Flow<CpuInfo>

    fun memory(intervalMillis: Long = 2_000): Flow<MemoryInfo>
}
