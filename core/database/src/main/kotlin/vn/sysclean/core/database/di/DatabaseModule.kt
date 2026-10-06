package vn.sysclean.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import vn.sysclean.core.database.SysCleanDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): SysCleanDatabase =
        Room.databaseBuilder(context, SysCleanDatabase::class.java, "sysclean.db").build()

    @Provides
    fun providesTrashDao(db: SysCleanDatabase) = db.trashDao()

    @Provides
    fun providesWhitelistDao(db: SysCleanDatabase) = db.whitelistDao()

    @Provides
    fun providesPhotoSignatureDao(db: SysCleanDatabase) = db.photoSignatureDao()
}
