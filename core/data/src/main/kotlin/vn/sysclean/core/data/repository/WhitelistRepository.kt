package vn.sysclean.core.data.repository

import kotlinx.coroutines.flow.Flow
import vn.sysclean.core.model.WhitelistEntry

interface WhitelistRepository {
    fun observe(): Flow<List<WhitelistEntry>>
    suspend fun paths(): Set<String>
    suspend fun add(path: String)
    suspend fun remove(path: String)
}

/** True when [path] is an ignored entry or lives inside an ignored folder. */
fun Set<String>.covers(path: String): Boolean = any { path == it || path.startsWith("$it/") }
