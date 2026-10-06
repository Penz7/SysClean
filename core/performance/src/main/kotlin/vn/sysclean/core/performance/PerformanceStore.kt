package vn.sysclean.core.performance

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.performanceStore by preferencesDataStore(name = "performance")

/** Keeps the "before" measurement from the last optimization. */
@Singleton
class PerformanceStore @Inject constructor(@ApplicationContext private val context: Context) {

    val baseline: Flow<Baseline?> = context.performanceStore.data.map { prefs ->
        val at = prefs[MEASURED_AT] ?: return@map null
        Baseline(at, prefs[JANK], prefs[RAM] ?: 0)
    }

    suspend fun saveBaseline(baseline: Baseline) {
        context.performanceStore.edit {
            it[MEASURED_AT] = baseline.measuredAt
            it[RAM] = baseline.ramAvailableBytes
            baseline.jankPercent?.let { jank -> it[JANK] = jank } ?: it.remove(JANK)
        }
    }

    private companion object {
        val MEASURED_AT = longPreferencesKey("baseline_at")
        val JANK = doublePreferencesKey("baseline_jank")
        val RAM = longPreferencesKey("baseline_ram")
    }
}
