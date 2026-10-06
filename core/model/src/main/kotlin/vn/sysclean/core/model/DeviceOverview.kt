package vn.sysclean.core.model

data class DeviceOverview(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val hardware: String,
    val board: String,
    val socManufacturer: String?,
    val socModel: String?,
    val supportedAbis: List<String>,
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String?,
    val buildId: String,
    val fingerprint: String,
    val kernelVersion: String?,
    val bootloader: String,
    val radioVersion: String?,
    val uptimeMillis: Long,
)

data class DisplayInfo(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshRateHz: Float,
    val supportedRefreshRatesHz: List<Float>,
    val hdrTypes: List<String>,
    /** Physical diagonal derived from xdpi/ydpi; OEMs sometimes report bogus dpi, so it may be null. */
    val diagonalInches: Double?,
)

data class SensorItem(
    val name: String,
    val vendor: String,
    val type: String,
    val powerMilliAmp: Float,
    val resolution: Float,
    val maxRange: Float,
)

enum class LensFacing { FRONT, BACK, EXTERNAL, UNKNOWN }

data class CameraItem(
    val id: String,
    val facing: LensFacing,
    val megapixels: Double?,
    val hasFlash: Boolean,
)
