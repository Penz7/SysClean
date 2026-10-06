package vn.sysclean.core.cleaner

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import vn.sysclean.core.common.di.Dispatcher
import vn.sysclean.core.common.di.SysCleanDispatcher
import vn.sysclean.core.data.repository.AppsRepository
import vn.sysclean.core.data.repository.TrashRepository
import vn.sysclean.core.model.CleanResult
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.JunkItem
import vn.sysclean.core.model.JunkReport
import vn.sysclean.core.model.isAppBased
import vn.sysclean.core.privilege.shell.PrivilegedPaths
import vn.sysclean.core.privilege.shell.SystemActions
import vn.sysclean.core.scanner.JunkScanner
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultJunkCleaner @Inject constructor(
    @Dispatcher(SysCleanDispatcher.IO) private val io: CoroutineDispatcher,
    private val trashRepository: TrashRepository,
    private val junkScanner: JunkScanner,
    private val systemActions: SystemActions,
    private val appsRepository: AppsRepository,
) : JunkCleaner {

    override suspend fun clean(
        category: JunkCategory,
        selected: List<JunkItem>,
        all: List<JunkItem>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): CleanResult = withContext(io) {
        if (selected.isEmpty()) return@withContext CleanResult.Empty
        if (category.isAppBased) return@withContext cleanAppCaches(selected, onProgress)

        var unverified = 0
        val targets = if (category == JunkCategory.DUPLICATE_FILES) {
            FileSafety.planDuplicates(selected, all).also { unverified = it.unverified.size }.removable
        } else {
            selected
        }

        val cleanedPaths = mutableSetOf<String>()
        val trashIds = mutableListOf<String>()
        var bytes = 0L
        var failed = 0
        targets.forEachIndexed { index, item ->
            ensureActive()
            val ok = if (category == JunkCategory.EMPTY_FOLDERS) {
                // Nothing to restore in an empty folder, so it is deleted outright.
                FileSafety.deleteIfEmpty(File(item.path))
            } else if (PrivilegedPaths.isUnderPrivilegedRoot(item.path)) {
                // Our bin lives in shared storage the shell cannot write to, so these go for good.
                systemActions.isReady && runCatching { systemActions.deletePrivileged(item.path) }.getOrDefault(false)
                    .also { if (it) bytes += item.sizeBytes }
            } else {
                trashRepository.moveToTrash(item.path, category)?.also {
                    trashIds += it.id
                    bytes += it.sizeBytes
                } != null
            }
            if (ok) cleanedPaths += item.path else failed++
            onProgress(index + 1, targets.size)
        }

        junkScanner.removeItems(category, cleanedPaths)
        CleanResult(cleanedPaths.size, bytes, failed, unverified, trashIds)
    }

    override suspend fun cleanSafe(report: JunkReport, onProgress: (done: Int, total: Int) -> Unit): CleanResult {
        val groups = report.groups.filter { it.category.isSafeToClean && !it.category.isAppBased }
        val total = groups.sumOf { it.items.size }
        var offset = 0
        var result = CleanResult.Empty
        for (group in groups) {
            val start = offset
            result += clean(group.category, group.items, group.items) { done, _ -> onProgress(start + done, total) }
            offset += group.items.size
        }
        return result
    }

    override suspend fun undo(result: CleanResult): Int = trashRepository.restore(result.trashIds)

    /** With Shizuku only the external cache can be emptied per app; root clears the private cache too. */
    private suspend fun cleanAppCaches(selected: List<JunkItem>, onProgress: (Int, Int) -> Unit): CleanResult {
        if (!systemActions.isReady) return CleanResult.Empty
        var bytes = 0L
        var failed = 0
        val cleaned = mutableSetOf<String>()
        selected.forEachIndexed { index, item ->
            runCatching { systemActions.clearAppCache(item.path) }
                .onSuccess { freed ->
                    bytes += freed
                    cleaned += item.path
                }
                .onFailure { failed++ }
            onProgress(index + 1, selected.size)
        }
        junkScanner.removeItems(JunkCategory.APP_CACHE, cleaned)
        return CleanResult(cleaned.size, bytes, failed, 0, emptyList())
    }

    override suspend fun clearAllAppCaches(): Long = withContext(io) {
        if (!systemActions.isReady) return@withContext 0L
        val before = totalCacheBytes()
        systemActions.trimAllCaches()
        val after = totalCacheBytes()
        junkScanner.removeItems(JunkCategory.APP_CACHE, appsRepository.installedApps().map { it.packageName }.toSet())
        (before - after).coerceAtLeast(0)
    }

    private suspend fun totalCacheBytes(): Long =
        appsRepository.installedApps(forceRefresh = true).sumOf { it.size?.cacheBytes ?: 0 }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CleanerModule {
    @Binds
    abstract fun bindsJunkCleaner(impl: DefaultJunkCleaner): JunkCleaner
}

