package vn.sysclean.core.data.repository

import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val trashRetentionDays: Flow<Int>
    suspend fun setTrashRetentionDays(days: Int)

    /** Safe-to-clean bytes found by the last scan; null until the first scan. */
    val lastSafeJunkBytes: Flow<Long?>
    suspend fun setLastSafeJunkBytes(bytes: Long)

    companion object {
        val RETENTION_OPTIONS = listOf(7, 14, 30)
        const val DEFAULT_RETENTION_DAYS = 14
    }
}
