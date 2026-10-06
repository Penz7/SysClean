package vn.sysclean.feature.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import vn.sysclean.core.data.repository.HealthRepository
import vn.sysclean.core.data.repository.HealthSnapshot

/**
 * What the widget shows, rounded to what the eye can see: the widget only redraws when one
 * of these whole numbers changes, not on every RAM fluctuation.
 */
internal data class WidgetModel(
    val score: Int?,
    val storageUsedPercent: Int?,
    val freeBytes: Long?,
    val ramUsedPercent: Int?,
    val batteryPercent: Int?,
    val scanned: Boolean,
) {
    companion object {
        fun from(snapshot: HealthSnapshot): WidgetModel {
            val primary = snapshot.storage?.primary
            return WidgetModel(
                score = snapshot.score?.score,
                storageUsedPercent = primary?.let { (it.usedFraction * 100).toInt() },
                // Rounded to 100 MB so tiny writes do not trigger redraws.
                freeBytes = primary?.freeBytes?.let { it / 100_000_000 * 100_000_000 },
                ramUsedPercent = snapshot.memory?.let { (it.usedFraction * 100).toInt() },
                batteryPercent = snapshot.battery?.levelPercent,
                scanned = snapshot.safeJunkBytes != null,
            )
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun healthRepository(): HealthRepository
    fun widgetRefresher(): WidgetRefresher
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
