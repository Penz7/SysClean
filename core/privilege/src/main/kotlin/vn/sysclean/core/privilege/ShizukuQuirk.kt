package vn.sysclean.core.privilege

/** Manufacturer settings that must be changed before Shizuku works. */
enum class ShizukuQuirk {
    /** MIUI / HyperOS strip permissions from adb unless "USB debugging (Security settings)" is on. */
    XIAOMI,

    /** ColorOS / realme UI / OxygenOS 12+ revoke adb permissions unless "Disable permission monitoring" is on. */
    COLOROS,

    /** Flyme blocks adb while "Flyme payment protection" is on. */
    FLYME,
    ;

    companion object {
        fun of(manufacturer: String, brand: String): ShizukuQuirk? {
            val names = setOf(manufacturer.lowercase(), brand.lowercase())
            return when {
                names.any { it in setOf("xiaomi", "redmi", "poco") } -> XIAOMI
                names.any { it in setOf("oppo", "realme", "oneplus") } -> COLOROS
                "meizu" in names -> FLYME
                else -> null
            }
        }
    }
}
