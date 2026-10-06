package vn.sysclean.core.performance

/** What turning off an optional always-running system service costs the user. */
enum class ServiceImpact {
    GOOGLE_ASSISTANT,
    CUSTOMIZATION,
    ROUTINES,
    VOICE_WAKE,
    NEARBY_ACCESSORIES,
    VENDOR_ANALYTICS,
}

/**
 * Pre-installed services that stay in RAM all the time but that many people never use.
 * Each one is only ever disabled for the current user, with a one-tap way back.
 */
object ResidentServices {
    val optional: Map<String, ServiceImpact> = mapOf(
        "com.google.android.googlequicksearchbox" to ServiceImpact.GOOGLE_ASSISTANT,
        "com.samsung.android.rubin.app" to ServiceImpact.CUSTOMIZATION,
        "com.samsung.android.app.routines" to ServiceImpact.ROUTINES,
        "com.samsung.android.intellivoiceservice" to ServiceImpact.VOICE_WAKE,
        "com.samsung.android.bixby.agent" to ServiceImpact.VOICE_WAKE,
        "com.samsung.android.beaconmanager" to ServiceImpact.NEARBY_ACCESSORIES,
        "com.miui.analytics" to ServiceImpact.VENDOR_ANALYTICS,
        "com.miui.msa.global" to ServiceImpact.VENDOR_ANALYTICS,
    )
}
