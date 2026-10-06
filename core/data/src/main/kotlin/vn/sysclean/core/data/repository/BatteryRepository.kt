package vn.sysclean.core.data.repository

import kotlinx.coroutines.flow.Flow
import vn.sysclean.core.model.BatteryInfo

interface BatteryRepository {
    /** Emits whenever the system broadcasts a battery or thermal change. */
    fun battery(): Flow<BatteryInfo>
}
