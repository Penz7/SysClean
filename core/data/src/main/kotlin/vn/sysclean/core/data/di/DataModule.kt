package vn.sysclean.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import vn.sysclean.core.data.repository.AppsRepository
import vn.sysclean.core.data.repository.BatteryRepository
import vn.sysclean.core.data.repository.DeviceInfoRepository
import vn.sysclean.core.data.repository.HealthRepository
import vn.sysclean.core.data.repository.StorageRepository
import vn.sysclean.core.data.repository.TrashRepository
import vn.sysclean.core.data.repository.UserPreferencesRepository
import vn.sysclean.core.data.repository.WhitelistRepository
import vn.sysclean.core.data.source.AppsDataSource
import vn.sysclean.core.data.source.BatteryDataSource
import vn.sysclean.core.data.source.DeviceInfoDataSource
import vn.sysclean.core.data.source.HealthDataSource
import vn.sysclean.core.data.source.StorageDataSource
import vn.sysclean.core.data.source.TrashDataSource
import vn.sysclean.core.data.source.UserPreferencesDataSource
import vn.sysclean.core.data.source.WhitelistDataSource

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun bindsDeviceInfoRepository(impl: DeviceInfoDataSource): DeviceInfoRepository

    @Binds
    abstract fun bindsBatteryRepository(impl: BatteryDataSource): BatteryRepository

    @Binds
    abstract fun bindsStorageRepository(impl: StorageDataSource): StorageRepository

    @Binds
    abstract fun bindsAppsRepository(impl: AppsDataSource): AppsRepository

    @Binds
    abstract fun bindsTrashRepository(impl: TrashDataSource): TrashRepository

    @Binds
    abstract fun bindsWhitelistRepository(impl: WhitelistDataSource): WhitelistRepository

    @Binds
    abstract fun bindsHealthRepository(impl: HealthDataSource): HealthRepository

    @Binds
    abstract fun bindsUserPreferencesRepository(impl: UserPreferencesDataSource): UserPreferencesRepository
}
