package vn.sysclean.core.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.sysclean.core.common.di.ApplicationScope
import vn.sysclean.core.common.di.Dispatcher
import vn.sysclean.core.common.di.SysCleanDispatcher
import vn.sysclean.core.data.repository.AppsRepository
import vn.sysclean.core.data.repository.TrashRepository
import vn.sysclean.core.data.repository.UserPreferencesRepository
import vn.sysclean.core.data.repository.WhitelistRepository
import vn.sysclean.core.data.repository.covers
import vn.sysclean.core.database.dao.PhotoSignatureDao
import vn.sysclean.core.model.AppInfo
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.JunkGroup
import vn.sysclean.core.model.JunkItem
import vn.sysclean.core.model.JunkReport
import vn.sysclean.core.model.ScanProgress
import vn.sysclean.core.model.ScanState
import vn.sysclean.core.model.isGrouped
import vn.sysclean.core.privilege.AccessRepository
import vn.sysclean.core.privilege.shell.PrivilegedPaths
import vn.sysclean.core.privilege.shell.PrivilegedShell
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultJunkScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(SysCleanDispatcher.IO) private val io: CoroutineDispatcher,
    private val appsRepository: AppsRepository,
    private val accessRepository: AccessRepository,
    private val whitelistRepository: WhitelistRepository,
    private val trashRepository: TrashRepository,
    private val photoSignatureDao: PhotoSignatureDao,
    private val privilegedShell: PrivilegedShell,
    private val preferences: UserPreferencesRepository,
) : JunkScanner {

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    override val state: StateFlow<ScanState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastFinished: ScanState.Finished? = null

    init {
        // Persist safe junk whenever the report changes, so the health score (dashboard and
        // widget alike) reflects scans and cleans even after the app process is gone.
        scope.launch {
            state.filterIsInstance<ScanState.Finished>()
                .map { it.report.safeBytes }
                .distinctUntilChanged()
                .collect { preferences.setLastSafeJunkBytes(it) }
        }
    }

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = ScanState.Running(ScanProgress("", 0, 0))
            _state.value = try {
                ScanState.Finished(scan()).also { lastFinished = it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: WalkCancelledException) {
                lastFinished ?: ScanState.Idle
            } catch (e: Exception) {
                ScanState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    override fun cancel() {
        job?.cancel()
        _state.value = lastFinished ?: ScanState.Idle
    }

    override fun removeItems(category: JunkCategory, paths: Set<String>) {
        val finished = _state.value as? ScanState.Finished ?: return
        val groups = finished.report.groups.mapNotNull { group ->
            if (group.category != category) return@mapNotNull group
            val (removed, kept) = group.items.partition { it.path in paths }
            val updated = if (category.isGrouped) {
                // Recount sets from what is left: every member but one is reclaimable.
                val remaining = kept.dropLonelyGroups()
                val sets = remaining.groupBy { it.groupId }.values
                group.copy(
                    itemCount = sets.sumOf { it.size - 1 },
                    totalBytes = sets.sumOf { set -> set.sumOf { it.sizeBytes } - set.keeperSize() },
                    items = remaining,
                )
            } else {
                group.copy(
                    itemCount = (group.itemCount - removed.size).coerceAtLeast(0),
                    totalBytes = (group.totalBytes - removed.sumOf { it.sizeBytes }).coerceAtLeast(0),
                    items = kept,
                )
            }
            updated.takeIf { it.itemCount > 0 }
        }
        _state.value = ScanState.Finished(finished.report.copy(groups = groups)).also { lastFinished = it }
    }

    override fun restoreItems(category: JunkCategory, items: List<JunkItem>) {
        val finished = _state.value as? ScanState.Finished ?: return
        if (items.isEmpty()) return
        val existing = finished.report.groups.firstOrNull { it.category == category }
        val merged = (existing?.items.orEmpty() + items).distinctBy { it.path }.sortedByDescending { it.sizeBytes }
        val group = if (category.isGrouped) {
            val sets = merged.groupBy { it.groupId }.values.filter { it.size > 1 }
            JunkGroup(category, sets.sumOf { it.size - 1 }, sets.sumOf { set -> set.sumOf { it.sizeBytes } - set.keeperSize() }, merged)
        } else {
            val added = merged.size - existing?.items.orEmpty().size
            JunkGroup(
                category = category,
                itemCount = (existing?.itemCount ?: 0) + added,
                totalBytes = (existing?.totalBytes ?: 0) + items.filter { i -> existing?.items.orEmpty().none { it.path == i.path } }.sumOf { it.sizeBytes },
                items = merged,
            )
        }
        val groups = (finished.report.groups.filter { it.category != category } + group).sortedBy { it.category.ordinal }
        _state.value = ScanState.Finished(finished.report.copy(groups = groups)).also { lastFinished = it }
    }

    private suspend fun scan(): JunkReport = withContext(io) {
        val startedAt = System.currentTimeMillis()
        accessRepository.refreshNow()
        val access = accessRepository.state.value
        val apps = appsRepository.installedApps(forceRefresh = true)
        // Our own media folder holds the recycle bin; it must never show up as junk itself.
        val ownMediaDir = java.io.File(trashRepository.trashRoot).parent
        val ignored = whitelistRepository.paths() + listOfNotNull(trashRepository.trashRoot, ownMediaDir)
        val isIgnored: (String) -> Boolean = { ignored.covers(it) }
        val groups = mutableListOf<JunkGroup>()
        val skipped = mutableSetOf<JunkCategory>()

        if (access.usageAccess) {
            appCacheGroup(apps.filterNot { isIgnored(it.packageName) }, privilegedShell.isReady)?.let(groups::add)
        } else {
            skipped += JunkCategory.APP_CACHE
        }

        var filesScanned = 0L
        var bytesScanned = 0L
        if (access.allFilesAccess) {
            val coroutineContext = currentCoroutineContext()
            val walker = StorageWalker(
                root = Environment.getExternalStorageDirectory(),
                installedPackages = installedVersions(),
                inspectApk = ::inspectApk,
                isActive = { coroutineContext.isActive },
                isIgnored = isIgnored,
                onProgress = { _state.value = ScanState.Running(it) },
            )
            val result = walker.walk()
            filesScanned = result.filesScanned
            bytesScanned = result.bytesScanned
            // With Shizuku, folders of uninstalled apps inside Android/data and obb become visible.
            if (privilegedShell.isReady) {
                val leftovers = result.categories.getOrPut(JunkCategory.LEFTOVER_FOLDERS) { TopItems(StorageWalker.MAX_ITEMS) }
                privilegedLeftovers(allKnownPackages(), isIgnored).forEach { leftovers.add(it) }
            }
            result.categories.forEach { (category, top) ->
                if (top.count > 0) {
                    groups += JunkGroup(category, top.count, top.totalBytes, top.items())
                }
            }

            // Exact duplicates are already reported; do not offer them again as "similar".
            val duplicatePaths = result.categories[JunkCategory.DUPLICATE_FILES]?.items()?.map { it.path }?.toSet().orEmpty()
            val photos = PhotoAnalyzer(
                context = context,
                dao = photoSignatureDao,
                io = io,
                isIgnored = isIgnored,
                onProgress = { name, done, total ->
                    _state.value = ScanState.Running(ScanProgress("$name ($done/$total)", filesScanned, bytesScanned))
                },
            ).analyze(duplicatePaths)
            listOf(JunkCategory.SIMILAR_PHOTOS to photos.similar, JunkCategory.BLURRY_PHOTOS to photos.blurry)
                .filter { it.second.count > 0 }
                .forEach { (category, top) -> groups += JunkGroup(category, top.count, top.totalBytes, top.items()) }
        } else {
            skipped += JunkCategory.entries.filter { it != JunkCategory.APP_CACHE }
        }

        JunkReport(
            groups = groups.sortedBy { it.category.ordinal },
            filesScanned = filesScanned,
            bytesScanned = bytesScanned,
            durationMillis = System.currentTimeMillis() - startedAt,
            finishedAt = System.currentTimeMillis(),
            skipped = skipped,
        )
    }

    private suspend fun privilegedLeftovers(installed: Set<String>, isIgnored: (String) -> Boolean): List<JunkItem> =
        runCatching {
            listOf(PrivilegedPaths.ANDROID_DATA, PrivilegedPaths.ANDROID_OBB).flatMap { root ->
                privilegedShell.list(root)
                    .filter { name -> PrivilegedPaths.looksLikePackage(name) && name !in installed && name != context.packageName }
                    .map { "$root/$it" }
                    .filterNot(isIgnored)
                    .map { path ->
                        JunkItem(path = path, title = path.substringAfterLast('/'), sizeBytes = privilegedShell.sizeOf(path), lastModified = 0)
                    }
                    // Empty folders of hidden system packages free nothing and only add noise.
                    .filter { it.sizeBytes > 0 }
            }
        }.getOrDefault(emptyList()) // Shizuku stopped mid-scan: report what normal mode found.

    private suspend fun appCacheGroup(apps: List<AppInfo>, privileged: Boolean): JunkGroup? {
        val withCache = apps.filter { (it.size?.cacheBytes ?: 0) > 0 }.sortedByDescending { it.size!!.cacheBytes }
        if (withCache.isEmpty()) return null
        val items = withCache.take(StorageWalker.MAX_ITEMS).map { app ->
            val total = app.size!!.cacheBytes
            if (privileged && !privilegedShell.canClearInternalCache) {
                // Per app Shizuku only reaches Android/data/<pkg>/cache; show exactly that.
                val external = runCatching { privilegedShell.sizeOf(PrivilegedPaths.externalCacheOf(app.packageName)) }.getOrDefault(0)
                JunkItem(path = app.packageName, title = app.label, sizeBytes = external, lastModified = app.updatedAt, fullCacheBytes = total)
            } else {
                JunkItem(path = app.packageName, title = app.label, sizeBytes = total, lastModified = app.updatedAt)
            }
        }.sortedByDescending { it.sizeBytes }
        return JunkGroup(
            category = JunkCategory.APP_CACHE,
            itemCount = withCache.size,
            // The category total stays the whole cache: that is what "Clear all cache" frees.
            totalBytes = withCache.sumOf { it.size!!.cacheBytes },
            items = items,
        )
    }

    /**
     * Every package the system knows, including ones uninstalled for this user but kept and
     * hidden overlays, so their folders are never mistaken for leftovers.
     */
    private fun allKnownPackages(): Set<String> {
        val pm = context.packageManager
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(flags)
        }
        return packages.mapTo(mutableSetOf()) { it.packageName }
    }

    private fun installedVersions(): Map<String, Long> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }
        return packages.associate { it.packageName to it.longVersionCodeCompat() }
    }

    private fun inspectApk(file: File): ApkMeta? {
        val pm = context.packageManager
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.path, 0)
            }
        }.getOrNull() ?: return null
        return ApkMeta(info.packageName, info.longVersionCodeCompat())
    }
}

private fun List<JunkItem>.keeperSize(): Long = (firstOrNull { it.isOriginal } ?: maxBy { it.sizeBytes }).sizeBytes

/** A set with a single survivor is no longer a set of duplicates. */
private fun List<JunkItem>.dropLonelyGroups(): List<JunkItem> {
    val sizes = groupingBy { it.groupId }.eachCount()
    return filter { it.groupId == null || (sizes[it.groupId] ?: 0) > 1 }
}

private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ScannerModule {
    @Binds
    abstract fun bindsJunkScanner(impl: DefaultJunkScanner): JunkScanner
}
