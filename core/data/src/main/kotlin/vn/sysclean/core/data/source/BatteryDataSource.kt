package vn.sysclean.core.data.source

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import vn.sysclean.core.common.di.Dispatcher
import vn.sysclean.core.common.di.SysCleanDispatcher
import vn.sysclean.core.data.repository.BatteryRepository
import vn.sysclean.core.model.BatteryHealth
import vn.sysclean.core.model.BatteryInfo
import vn.sysclean.core.model.BatteryStatus
import vn.sysclean.core.model.PlugType
import vn.sysclean.core.model.ThermalStatus
import javax.inject.Inject
import kotlin.math.abs

internal class BatteryDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(SysCleanDispatcher.IO) private val io: CoroutineDispatcher,
) : BatteryRepository {

    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val designCapacityMah: Double? by lazy(::readDesignCapacity)

    // Off the main thread: the first emission reads the design capacity through reflection.
    override fun battery(): Flow<BatteryInfo> =
        combine(batteryIntents(), thermalStatus()) { intent, thermal -> intent.toBatteryInfo(thermal) }
            .flowOn(io)

    private fun batteryIntents(): Flow<Intent> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(intent)
            }
        }
        // ACTION_BATTERY_CHANGED is sticky, so registering immediately yields the current state.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let { trySend(it) }
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate()

    private fun thermalStatus(): Flow<ThermalStatus> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return flowOf(ThermalStatus.UNKNOWN)
        return callbackFlow {
            val powerManager = context.getSystemService(PowerManager::class.java)
            val listener = PowerManager.OnThermalStatusChangedListener { trySend(it.toThermalStatus()) }
            trySend(powerManager.currentThermalStatus.toThermalStatus())
            powerManager.addThermalStatusListener(listener)
            awaitClose { powerManager.removeThermalStatusListener(listener) }
        }
    }

    private fun Intent.toBatteryInfo(thermal: ThermalStatus): BatteryInfo {
        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100).takeIf { it > 0 } ?: 100
        val percent = if (level >= 0) level * 100 / scale else batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val temperature = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }?.div(10f)
        // Some kernels report volts instead of millivolts.
        val voltage = getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 }
            ?.let { if (it < 100) it * 1000 else it }
        val cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 }
        } else {
            null
        }
        return BatteryInfo(
            levelPercent = percent,
            status = when (getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> BatteryStatus.CHARGING
                BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryStatus.DISCHARGING
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryStatus.NOT_CHARGING
                BatteryManager.BATTERY_STATUS_FULL -> BatteryStatus.FULL
                else -> BatteryStatus.UNKNOWN
            },
            health = when (getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
                BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.FAILURE
                BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
                else -> BatteryHealth.UNKNOWN
            },
            plugType = when (getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
                BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
                BATTERY_PLUGGED_DOCK -> PlugType.DOCK
                else -> PlugType.NONE
            },
            temperatureCelsius = temperature,
            voltageMillivolts = voltage,
            technology = getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() },
            cycleCount = cycleCount,
            currentNowMicroAmp = currentNowMicroAmp(),
            designCapacityMah = designCapacityMah,
            estimatedCapacityMah = estimatedCapacityMah(percent),
            thermalStatus = thermal,
        )
    }

    /** Normalised to µA: several OEM kernels report milliamps through the µA property. */
    private fun currentNowMicroAmp(): Long? {
        val raw = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (raw == 0L || raw == Long.MIN_VALUE) return null
        return if (abs(raw) < 10_000) raw * 1000 else raw
    }

    /** charge counter (µAh) at x% extrapolated to 100%. Unreliable below 20%, so skipped there. */
    private fun estimatedCapacityMah(percent: Int): Double? {
        if (percent < 20) return null
        val counter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        if (counter <= 0 || counter == Long.MIN_VALUE) return null
        return (counter / 1000.0 * 100 / percent).takeIf { it in 500.0..30_000.0 }
    }

    // PowerProfile is a hidden API; it still answers on most builds but may be blocked any release.
    @SuppressLint("PrivateApi")
    private fun readDesignCapacity(): Double? = try {
        val clazz = Class.forName("com.android.internal.os.PowerProfile")
        val profile = clazz.getConstructor(Context::class.java).newInstance(context)
        (clazz.getMethod("getBatteryCapacity").invoke(profile) as Double).takeIf { it in 500.0..30_000.0 }
    } catch (_: Throwable) {
        null
    }

    private companion object {
        // BatteryManager.BATTERY_PLUGGED_DOCK is API 33+; the value is stable.
        const val BATTERY_PLUGGED_DOCK = 8
    }
}

private fun Int.toThermalStatus(): ThermalStatus = when (this) {
    PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NONE
    PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
    PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
    PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
    PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
    PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalStatus.EMERGENCY
    PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.SHUTDOWN
    else -> ThermalStatus.UNKNOWN
}
