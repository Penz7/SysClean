package vn.sysclean.core.data.repository

import kotlinx.coroutines.flow.Flow
import vn.sysclean.core.domain.HealthScore
import vn.sysclean.core.model.BatteryInfo
import vn.sysclean.core.model.MemoryInfo
import vn.sysclean.core.model.StorageInfo

/** Everything the health score is made of, plus the score itself. */
data class HealthSnapshot(
    val score: HealthScore? = null,
    val storage: StorageInfo? = null,
    val memory: MemoryInfo? = null,
    val battery: BatteryInfo? = null,
    /** Safe junk from the last scan; null if the device was never scanned. */
    val safeJunkBytes: Long? = null,
)

/**
 * The single source of the health score. The dashboard and the home-screen widget both
 * read it, so they always show the same number.
 */
interface HealthRepository {
    /** Live snapshot; RAM is re-read every [memoryIntervalMillis]. */
    fun observe(memoryIntervalMillis: Long = 2_000): Flow<HealthSnapshot>

    /** One-off snapshot for callers that cannot keep a flow open (the widget host). */
    suspend fun current(): HealthSnapshot

    /**
     * Something just changed RAM or storage on purpose (optimize, clean, deep sleep):
     * every observer re-reads now instead of on its next tick, so the dashboard and the
     * widget show the result immediately.
     */
    fun invalidate()
}
