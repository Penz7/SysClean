package vn.sysclean.core.data.source

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.sysclean.core.data.repository.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_preferences")

@Singleton
internal class UserPreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserPreferencesRepository {

    override val trashRetentionDays: Flow<Int> = context.dataStore.data.map {
        it[RETENTION_KEY] ?: UserPreferencesRepository.DEFAULT_RETENTION_DAYS
    }

    override suspend fun setTrashRetentionDays(days: Int) {
        context.dataStore.edit { it[RETENTION_KEY] = days }
    }

    override val lastSafeJunkBytes: Flow<Long?> = context.dataStore.data.map { it[LAST_SAFE_JUNK_KEY] }

    override suspend fun setLastSafeJunkBytes(bytes: Long) {
        context.dataStore.edit { it[LAST_SAFE_JUNK_KEY] = bytes }
    }

    private companion object {
        val RETENTION_KEY = intPreferencesKey("trash_retention_days")
        val LAST_SAFE_JUNK_KEY = longPreferencesKey("last_safe_junk_bytes")
    }
}
