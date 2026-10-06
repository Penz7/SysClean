package vn.sysclean.feature.apps

import vn.sysclean.core.model.AppInfo
import java.util.concurrent.TimeUnit

enum class AppFilter { USER, SYSTEM, ALL, UNUSED }

enum class AppSort { SIZE, CACHE, LAST_USED, NAME }

data class AppListQuery(
    val text: String = "",
    val filter: AppFilter = AppFilter.USER,
    val sort: AppSort = AppSort.SIZE,
)

internal val UNUSED_AFTER_MILLIS = TimeUnit.DAYS.toMillis(90)

/** A user app counts as unused when the system has no record of it in the last 90 days. */
internal fun AppInfo.isUnused(now: Long): Boolean =
    !isSystem && now - (lastUsedAt ?: 0L) > UNUSED_AFTER_MILLIS && now - installedAt > UNUSED_AFTER_MILLIS

internal fun List<AppInfo>.applyQuery(query: AppListQuery, now: Long): List<AppInfo> {
    val needle = query.text.trim()
    val filtered = filter { app ->
        val matchesFilter = when (query.filter) {
            AppFilter.USER -> !app.isSystem
            AppFilter.SYSTEM -> app.isSystem
            AppFilter.ALL -> true
            AppFilter.UNUSED -> app.isUnused(now)
        }
        matchesFilter && (needle.isEmpty() || app.label.contains(needle, true) || app.packageName.contains(needle, true))
    }
    return when (query.sort) {
        AppSort.SIZE -> filtered.sortedByDescending { it.size?.totalBytes ?: 0 }
        AppSort.CACHE -> filtered.sortedByDescending { it.size?.cacheBytes ?: 0 }
        // Oldest first: the point of this sort is finding apps to remove.
        AppSort.LAST_USED -> filtered.sortedBy { it.lastUsedAt ?: 0 }
        AppSort.NAME -> filtered.sortedBy { it.label.lowercase() }
    }
}
