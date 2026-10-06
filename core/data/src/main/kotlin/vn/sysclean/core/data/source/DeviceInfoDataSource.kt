package vn.sysclean.core.data.source

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import vn.sysclean.core.common.di.Dispatcher
import vn.sysclean.core.common.di.SysCleanDispatcher
import vn.sysclean.core.data.repository.DeviceInfoRepository
import vn.sysclean.core.model.CameraItem
import vn.sysclean.core.model.CpuCore
import vn.sysclean.core.model.CpuInfo
import vn.sysclean.core.model.DeviceOverview
import vn.sysclean.core.model.DisplayInfo
import vn.sysclean.core.model.EncryptionStatus
import vn.sysclean.core.model.LensFacing
import vn.sysclean.core.model.MemoryInfo
import vn.sysclean.core.model.SecurityInfo
import vn.sysclean.core.model.SensorItem
import vn.sysclean.core.privilege.AccessRepository
import javax.inject.Inject
import kotlin.math.sqrt

internal class DeviceInfoDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(SysCleanDispatcher.IO) private val io: CoroutineDispatcher,
    private val accessRepository: AccessRepository,
) : DeviceInfoRepository {

    override suspend fun overview(): DeviceOverview = withContext(io) {
        DeviceOverview(
            manufacturer = Build.MANUFACTURER.capitalizeFirst(),
            brand = Build.BRAND.capitalizeFirst(),
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            hardware = Build.HARDWARE,
            board = Build.BOARD,
            socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.known() else null,
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.known() else null,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            buildId = Build.DISPLAY,
            fingerprint = Build.FINGERPRINT,
            kernelVersion = System.getProperty("os.version"),
            bootloader = Build.BOOTLOADER,
            radioVersion = Build.getRadioVersion()?.known(),
            uptimeMillis = SystemClock.elapsedRealtime(),
        )
    }

    override suspend fun display(): DisplayInfo = withContext(io) {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display.mode
        val metrics = DisplayMetrics().also {
            @Suppress("DEPRECATION")
            display.getRealMetrics(it)
        }
        val width = mode.physicalWidth
        val height = mode.physicalHeight
        val diagonal = if (metrics.xdpi > 0 && metrics.ydpi > 0) {
            sqrt((width / metrics.xdpi).let { it * it } + (height / metrics.ydpi).let { it * it }.toDouble())
        } else {
            null
        }
        val hdrTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mode.supportedHdrTypes
        } else {
            @Suppress("DEPRECATION")
            display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
        }
        DisplayInfo(
            widthPx = minOf(width, height),
            heightPx = maxOf(width, height),
            densityDpi = metrics.densityDpi,
            refreshRateHz = display.refreshRate,
            supportedRefreshRatesHz = display.supportedModes
                .filter { it.physicalWidth == width && it.physicalHeight == height }
                .map { it.refreshRate }
                .distinctBy { Math.round(it) }
                .sorted(),
            hdrTypes = hdrTypes.map(::hdrName).distinct(),
            // xdpi/ydpi are frequently placeholder values; reject sizes no phone or tablet has.
            diagonalInches = diagonal?.takeIf { it in 2.5..20.0 },
        )
    }

    override suspend fun sensors(): List<SensorItem> = withContext(io) {
        context.getSystemService(SensorManager::class.java)
            .getSensorList(Sensor.TYPE_ALL)
            .map {
                SensorItem(
                    name = it.name,
                    vendor = it.vendor,
                    type = prettySensorType(it.stringType),
                    powerMilliAmp = it.power,
                    resolution = it.resolution,
                    maxRange = it.maximumRange,
                )
            }
            .sortedBy { it.type }
    }

    override suspend fun cameras(): List<CameraItem> = withContext(io) {
        val manager = context.getSystemService(CameraManager::class.java)
        runCatching { manager.cameraIdList.toList() }.getOrDefault(emptyList()).mapNotNull { id ->
            runCatching {
                val chars = manager.getCameraCharacteristics(id)
                val pixels = chars.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                CameraItem(
                    id = id,
                    facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                        CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
                        CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
                        CameraCharacteristics.LENS_FACING_EXTERNAL -> LensFacing.EXTERNAL
                        else -> LensFacing.UNKNOWN
                    },
                    megapixels = pixels?.let { it.width.toLong() * it.height / 1_000_000.0 },
                    hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                )
            }.getOrNull()
        }
    }

    override suspend fun security(): SecurityInfo = withContext(io) {
        val resolver = context.contentResolver
        val rootIndicators = accessRepository.rootIndicators()
        SecurityInfo(
            rootDetected = rootIndicators.isNotEmpty(),
            rootIndicators = rootIndicators,
            isDeviceSecure = context.getSystemService(KeyguardManager::class.java).isDeviceSecure,
            developerOptionsEnabled =
                Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
            usbDebuggingEnabled = Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1,
            encryption = when (context.getSystemService(DevicePolicyManager::class.java).storageEncryptionStatus) {
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE,
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER,
                DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY,
                -> EncryptionStatus.ENCRYPTED
                DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE,
                DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED,
                -> EncryptionStatus.NOT_ENCRYPTED
                else -> EncryptionStatus.UNKNOWN
            },
            verifiedBootState = SysFs.getprop("ro.boot.verifiedbootstate"),
            selinuxEnforcing = SysFs.readText("/sys/fs/selinux/enforce")?.let { it == "1" },
        )
    }

    override fun cpu(intervalMillis: Long): Flow<CpuInfo> = flow {
        val indices = SysFs.readText("$CPU_DIR/possible")?.let(SysFs::parseCpuRange)?.takeIf { it.isNotEmpty() }
            ?: (0 until Runtime.getRuntime().availableProcessors()).toList()
        val hardwareName = SysFs.readText("/proc/cpuinfo")?.let { SysFs.cpuinfoField(it, "Hardware") }
        while (true) {
            emit(CpuInfo(hardwareName = hardwareName, coreCount = indices.size, cores = indices.map(::readCore)))
            delay(intervalMillis)
        }
    }.flowOn(io)

    override fun memory(intervalMillis: Long): Flow<MemoryInfo> = flow {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        while (true) {
            val info = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
            val meminfo = SysFs.readText("/proc/meminfo")?.let(SysFs::parseMeminfo).orEmpty()
            emit(
                MemoryInfo(
                    totalBytes = info.totalMem,
                    availableBytes = info.availMem,
                    lowMemoryThresholdBytes = info.threshold,
                    isLowMemory = info.lowMemory,
                    swapTotalBytes = meminfo["SwapTotal"],
                    swapFreeBytes = meminfo["SwapFree"],
                ),
            )
            delay(intervalMillis)
        }
    }.flowOn(io)

    private fun readCore(index: Int): CpuCore {
        val base = "$CPU_DIR/cpu$index"
        // cpu0 has no "online" file because it can never be hot-unplugged.
        val online = SysFs.readText("$base/online")?.let { it == "1" } ?: true
        return CpuCore(
            index = index,
            online = online,
            minFreqKHz = SysFs.readLong("$base/cpufreq/cpuinfo_min_freq"),
            maxFreqKHz = SysFs.readLong("$base/cpufreq/cpuinfo_max_freq"),
            currentFreqKHz = if (online) SysFs.readLong("$base/cpufreq/scaling_cur_freq") else null,
            governor = SysFs.readText("$base/cpufreq/scaling_governor"),
        )
    }

    private companion object {
        const val CPU_DIR = "/sys/devices/system/cpu"
    }
}

private fun String.capitalizeFirst(): String = replaceFirstChar { it.uppercase() }

private fun String.known(): String? = takeUnless { it.isBlank() || it.equals(Build.UNKNOWN, ignoreCase = true) }

private fun hdrName(type: Int): String = when (type) {
    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
    Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
    Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
    Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
    else -> "HDR ($type)"
}

/** "android.sensor.magnetic_field_uncalibrated" -> "Magnetic field uncalibrated" */
private fun prettySensorType(stringType: String): String =
    stringType.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() }
