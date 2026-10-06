package vn.sysclean.core.data.source

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vn.sysclean.core.data.repository.WhitelistRepository
import vn.sysclean.core.database.dao.WhitelistDao
import vn.sysclean.core.database.model.WhitelistEntity
import vn.sysclean.core.model.WhitelistEntry
import javax.inject.Inject

internal class WhitelistDataSource @Inject constructor(
    private val dao: WhitelistDao,
) : WhitelistRepository {
    override fun observe(): Flow<List<WhitelistEntry>> =
        dao.observeAll().map { list -> list.map { WhitelistEntry(it.path, it.addedAt) } }

    override suspend fun paths(): Set<String> = dao.getPaths().toSet()

    override suspend fun add(path: String) = dao.insert(WhitelistEntity(path, System.currentTimeMillis()))

    override suspend fun remove(path: String) = dao.delete(path)
}
