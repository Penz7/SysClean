package vn.sysclean.feature.apps

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.sysclean.core.model.AppInfo
import vn.sysclean.core.model.AppSize
import java.util.concurrent.TimeUnit

class AppListQueryTest {
    private val now = TimeUnit.DAYS.toMillis(1000)
    private val day = TimeUnit.DAYS.toMillis(1)

    private fun app(
        label: String,
        system: Boolean = false,
        size: Long = 0,
        cache: Long = 0,
        lastUsedDaysAgo: Long? = 1,
        installedDaysAgo: Long = 365,
    ) = AppInfo(
        packageName = "pkg.${label.lowercase()}",
        label = label,
        versionName = null,
        isSystem = system,
        isEnabled = true,
        targetSdk = 36,
        installedAt = now - installedDaysAgo * day,
        updatedAt = now,
        lastUsedAt = lastUsedDaysAgo?.let { now - it * day },
        size = AppSize(apkBytes = size, dataBytes = 0, cacheBytes = cache),
    )

    private val apps = listOf(
        app("Zalo", size = 900, cache = 50, lastUsedDaysAgo = 0),
        app("Banking", size = 300, cache = 400, lastUsedDaysAgo = 200),
        app("Old Game", size = 500, lastUsedDaysAgo = null),
        app("New Install", size = 10, lastUsedDaysAgo = null, installedDaysAgo = 3),
        app("Settings", system = true, size = 2000),
    )

    @Test
    fun defaultQueryShowsUserAppsLargestFirst() {
        val result = apps.applyQuery(AppListQuery(), now).map { it.label }
        assertEquals(listOf("Zalo", "Old Game", "Banking", "New Install"), result)
    }

    @Test
    fun unusedSkipsRecentInstallsAndSystemApps() {
        val result = apps.applyQuery(AppListQuery(filter = AppFilter.UNUSED, sort = AppSort.NAME), now).map { it.label }
        assertEquals(listOf("Banking", "Old Game"), result)
    }

    @Test
    fun searchMatchesLabelOrPackageIgnoringCase() {
        assertEquals(listOf("Zalo"), apps.applyQuery(AppListQuery(text = "zal"), now).map { it.label })
        assertEquals(listOf("Banking"), apps.applyQuery(AppListQuery(text = "PKG.BANK"), now).map { it.label })
    }

    @Test
    fun cacheSortPutsHeaviestCacheFirst() {
        val result = apps.applyQuery(AppListQuery(filter = AppFilter.ALL, sort = AppSort.CACHE), now).first().label
        assertEquals("Banking", result)
    }
}
