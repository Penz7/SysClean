package vn.sysclean.core.data.repository

import kotlinx.coroutines.flow.Flow
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.TrashItem

interface TrashRepository {
    fun observe(): Flow<List<TrashItem>>

    /** Folder that holds trashed files; scanners must skip it. */
    val trashRoot: String

    /** Moves [path] into the bin. Returns the new item, or null if the file could not be moved. */
    suspend fun moveToTrash(path: String, category: JunkCategory): TrashItem?

    /** Returns how many items were put back. */
    suspend fun restore(ids: List<String>): Int

    suspend fun deletePermanently(ids: List<String>)

    suspend fun empty()

    /** Deletes items past the retention period and files the database no longer knows about. */
    suspend fun purgeExpired()
}
