package vn.sysclean.feature.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import vn.sysclean.core.common.di.ApplicationScope
import vn.sysclean.core.data.repository.HealthRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Makes the widget reactive: while the app process is alive and a widget is on the home
 * screen, it follows the same health flow as the dashboard and redraws whenever a visible
 * number changes. RAM is sampled every 15 s here (vs 2 s on screen) to keep it cheap, only
 * while the screen is on, and right away when it turns on, so the widget is current when seen.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val healthRepository: HealthRepository,
) {
    private var job: Job? = null

    /**
     * Last model observed. The widget collects it inside its composition: Glance keeps a
     * session alive between updates and recomposes it instead of calling provideGlance
     * again, so a value captured once would freeze the numbers on screen.
     */
    private val _latest = MutableStateFlow<WidgetModel?>(null)
    internal val latest: StateFlow<WidgetModel?> = _latest

    /** Starts following the score if at least one widget is placed. Safe to call repeatedly. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Synchronized
    fun start() {
        if (job?.isActive == true || !hasWidgets()) return
        job = scope.launch {
            screenOn()
                .flatMapLatest { on -> if (on) healthRepository.observe(memoryIntervalMillis = WIDGET_RAM_INTERVAL) else emptyFlow() }
                .map(WidgetModel::from)
                .distinctUntilChanged()
                .collect { model ->
                    _latest.value = model
                    // Starts a session if none is running; a running one picks up [latest] itself.
                    HealthWidget().updateAll(context)
                }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        _latest.value = null
    }

    /**
     * Asks the launcher to place the widget. Returns false when the launcher cannot pin
     * widgets, so the UI can explain the manual way.
     */
    fun requestPin(): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(ComponentName(context, HealthWidgetReceiver::class.java), null, null)
    }

    /** Screen on/off as a flow. Screen broadcasts can only be received by a running app, as here. */
    private fun screenOn(): Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(intent.action == Intent.ACTION_SCREEN_ON)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(context.getSystemService(PowerManager::class.java).isInteractive)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private fun hasWidgets(): Boolean =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, HealthWidgetReceiver::class.java))
            .isNotEmpty()

    private companion object {
        const val WIDGET_RAM_INTERVAL = 15_000L
    }
}
