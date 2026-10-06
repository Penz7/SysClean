package vn.sysclean.core.data.source

import android.content.Context
import android.media.MediaScannerConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import vn.sysclean.core.common.di.Dispatcher
import vn.sysclean.core.common.di.SysCleanDispatcher
import vn.sysclean.core.data.repository.TrashRepository
import vn.sysclean.core.data.repository.UserPreferencesRepository
import vn.sysclean.core.database.dao.TrashDao
import vn.sysclean.core.database.model.TrashEntity
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.TrashItem
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TrashDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(SysCleanDispatcher.IO) private val io: CoroutineDispatcher,
    private val dao: TrashDao,
    private val preferences: UserPreferencesRepository,
) : TrashRepository {

    // Our own Android/media folder: same volume as the user's files (instant rename), invisible
    // to the gallery thanks to .nomedia, and removed by the system if the app is uninstalled.
    @Suppress("DEPRECATION")
    private val store = TrashStore(File(context.externalMediaDirs.first(), "trash"))

    override val trashRoot: String get() = store.root.absolutePath

    override fun observe(): Flow<List<TrashItem>> =
        combine(dao.observeAll(), preferences.trashRetentionDays) { entities, days ->
            entities.map { it.toModel(days) }
        }

    override suspend fun moveToTrash(path: String, category: JunkCategory): TrashItem? = withContext(io) {
        val source = File(path)
        if (!source.exists()) return@withContext null
        val entity = TrashEntity(
            id = UUID.randomUUID().toString(),
            originalPath = source.absolutePath,
            trashPath = "",
            sizeBytes = source.sizeRecursive(),
            isDirectory = source.isDirectory,
            category = category.name,
            deletedAt = System.currentTimeMillis(),
        ).let { it.copy(trashPath = store.locationFor(it.id).absolutePath) }

        // Record first: a row without a file is harmless, a file without a row would be lost.
        dao.insert(entity)
        if (store.moveIn(source, entity.id) == null) {
            dao.deleteByIds(listOf(entity.id))
            return@withContext null
        }
        notifyMediaStore(listOf(entity.originalPath))
        entity.toModel(preferences.trashRetentionDays.first())
    }

    override suspend fun restore(ids: List<String>): Int = withContext(io) {
        val restored = dao.getByIds(ids).mapNotNull { entity ->
            store.restore(File(entity.trashPath), entity.originalPath)?.let { entity.id to it.absolutePath }
        }
        dao.deleteByIds(restored.map { it.first })
        notifyMediaStore(restored.map { it.second })
        restored.size
    }

    override suspend fun deletePermanently(ids: List<String>) = withContext(io) {
        val entities = dao.getByIds(ids)
        // Forget the records first so the bin empties on screen immediately; deleting large
        // folders can take a while. A file that fails to delete becomes an orphan, which
        // purgeExpired() removes on a later launch.
        dao.deleteByIds(entities.map { it.id })
        entities.forEach { store.delete(File(it.trashPath)) }
    }

    override suspend fun empty() = withContext(io) {
        deletePermanently(dao.getAll().map { it.id })
    }

    override suspend fun purgeExpired() = withContext(io) {
        val days = preferences.trashRetentionDays.first()
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        deletePermanently(dao.getOlderThan(cutoff).map { it.id })

        // Files whose record vanished (app data cleared) can never be restored; the grace
        // period avoids racing a move that is in progress right now.
        val known = dao.getAll().map { it.id }.toSet()
        store.orphans(known, olderThan = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1))
            .forEach { store.delete(it) }
    }

    /** Tells MediaStore that files left or came back so the gallery stays in sync. */
    private fun notifyMediaStore(paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }

    private fun TrashEntity.toModel(retentionDays: Int) = TrashItem(
        id = id,
        originalPath = originalPath,
        trashPath = trashPath,
        sizeBytes = sizeBytes,
        isDirectory = isDirectory,
        category = JunkCategory.entries.firstOrNull { it.name == category },
        deletedAt = deletedAt,
        expiresAt = deletedAt + TimeUnit.DAYS.toMillis(retentionDays.toLong()),
    )
}
