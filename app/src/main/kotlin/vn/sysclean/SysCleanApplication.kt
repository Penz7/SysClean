package vn.sysclean

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import vn.sysclean.core.common.di.ApplicationScope
import vn.sysclean.core.data.repository.TrashRepository
import vn.sysclean.feature.widget.WidgetRefresher
import javax.inject.Inject

@HiltAndroidApp
class SysCleanApplication : Application() {

    @Inject
    lateinit var trashRepository: TrashRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var widgetRefresher: WidgetRefresher

    override fun onCreate() {
        super.onCreate()
        // Retention is enforced on every launch; the bin only grows while the app is in use.
        applicationScope.launch { runCatching { trashRepository.purgeExpired() } }
        widgetRefresher.start()
    }
}
