package vn.sysclean.core.data.source

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import vn.sysclean.core.common.di.Dispatcher
import vn.sysclean.core.common.di.SysCleanDispatcher
import vn.sysclean.core.data.repository.StorageRepository
import vn.sysclean.core.model.StorageBreakdown
import vn.sysclean.core.model.StorageInfo
import vn.sysclean.core.model.StorageVolumeInfo
import javax.inject.Inject

internal class StorageDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(SysCleanDispatcher.IO) private val io: CoroutineDispatcher,
) : StorageRepository {

    private val statsManager = context.getSystemService(StorageStatsManager::class.java)
    private val storageManager = context.getSystemService(StorageManager::class.java)

    override suspend fun storageInfo(): StorageInfo = withContext(io) {
        // StorageStatsManager reports the real partition size (e.g. 128 GB), unlike StatFs on /data.
        val primary = StorageVolumeInfo(
            label = storageManager.primaryStorageVolume.getDescription(context),
            isPrimary = true,
            isRemovable = false,
            totalBytes = statsManager.getTotalBytes(StorageManager.UUID_DEFAULT),
            freeBytes = statsManager.getFreeBytes(StorageManager.UUID_DEFAULT),
        )
        StorageInfo(
            volumes = listOf(primary) + removableVolumes(),
            breakdown = breakdown(primary),
        )
    }

    /** App-specific dirs exist on every mounted volume, which makes them a portable handle on SD cards. */
    private fun removableVolumes(): List<StorageVolumeInfo> =
        context.getExternalFilesDirs(null).drop(1).filterNotNull().mapNotNull { dir ->
            runCatching {
                val stat = StatFs(dir.path)
                StorageVolumeInfo(
                    label = storageManager.getStorageVolume(dir)?.getDescription(context) ?: "SD",
                    isPrimary = false,
                    isRemovable = true,
                    totalBytes = stat.totalBytes,
                    freeBytes = stat.availableBytes,
                )
            }.getOrNull()
        }

    /**
     * Mirrors the categories of Settings > Storage. Requires usage access; without it the
     * system throws and we return null so the UI can ask for the permission.
     */
    private fun breakdown(primary: StorageVolumeInfo): StorageBreakdown? = try {
        val user = Process.myUserHandle()
        val external = statsManager.queryExternalStatsForUser(StorageManager.UUID_DEFAULT, user)
        val userStats = statsManager.queryStatsForUser(StorageManager.UUID_DEFAULT, user)

        val media = external.imageBytes + external.videoBytes + external.audioBytes
        // User dataBytes counts shared storage too; subtract it to keep only private app data.
        val privateAppData = (userStats.dataBytes - external.totalBytes).coerceAtLeast(0)
        val apps = userStats.appBytes + privateAppData
        val other = (external.totalBytes - media).coerceAtLeast(0)
        val system = (primary.usedBytes - apps - media - other).coerceAtLeast(0)

        StorageBreakdown(
            appsBytes = apps,
            imagesBytes = external.imageBytes,
            videosBytes = external.videoBytes,
            audioBytes = external.audioBytes,
            otherBytes = other,
            systemBytes = system,
        )
    } catch (_: SecurityException) {
        null
    }
}
