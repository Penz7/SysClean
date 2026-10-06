package vn.sysclean.core.data.repository

import vn.sysclean.core.model.StorageInfo

interface StorageRepository {
    suspend fun storageInfo(): StorageInfo
}
