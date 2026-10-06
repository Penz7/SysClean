package vn.sysclean.core.privilege.shell

enum class BloatReason {
    FACEBOOK_PRELOAD,
    VENDOR_ADS,
    RARELY_USED_PRELOAD,

    /** Not on any list: found on this phone because the user never opens it. Any manufacturer. */
    UNUSED_PRELOAD,
}

/**
 * Which pre-installed apps the app suggests removing. Deliberately conservative: only
 * packages that common debloat guides agree are safe on stock firmware, and never anything
 * the system, the launcher, the keyboard, Google Play or this app depend on.
 *
 * The list only names well-known packages; brands it does not cover are handled by usage
 * (see AppsRepository.unusedPreinstalled), which works the same on every manufacturer.
 */
object BloatwarePolicy {

    val recommended: Map<String, BloatReason> = mapOf(
        // Facebook services shipped by OEM deals; they only update and track the Facebook app.
        "com.facebook.appmanager" to BloatReason.FACEBOOK_PRELOAD,
        "com.facebook.services" to BloatReason.FACEBOOK_PRELOAD,
        "com.facebook.system" to BloatReason.FACEBOOK_PRELOAD,
        // Xiaomi ad and analytics services.
        "com.miui.msa.global" to BloatReason.VENDOR_ADS,
        "com.miui.analytics" to BloatReason.VENDOR_ADS,
        "com.miui.daemon" to BloatReason.VENDOR_ADS,
        "com.xiaomi.mipicks" to BloatReason.VENDOR_ADS,
        "com.miui.hybrid" to BloatReason.VENDOR_ADS,
        // OPPO, realme and OnePlus (ColorOS) ad and analytics services.
        "com.opos.ads" to BloatReason.VENDOR_ADS,
        "com.nearme.statistics.rom" to BloatReason.VENDOR_ADS,
        // Pre-installed extras that nothing else depends on.
        "com.microsoft.skydrive" to BloatReason.RARELY_USED_PRELOAD,
        "com.google.android.apps.tachyon" to BloatReason.RARELY_USED_PRELOAD,
        "com.samsung.android.game.gamehome" to BloatReason.RARELY_USED_PRELOAD,
        "com.samsung.android.kidsinstaller" to BloatReason.RARELY_USED_PRELOAD,
        "com.samsung.android.app.watchmanagerstub" to BloatReason.RARELY_USED_PRELOAD,
        "com.samsung.android.service.peoplestripe" to BloatReason.RARELY_USED_PRELOAD,
        "com.samsung.android.app.tips" to BloatReason.RARELY_USED_PRELOAD,
        "com.samsung.android.tvplus" to BloatReason.RARELY_USED_PRELOAD,
        "com.samsung.android.ardrawing" to BloatReason.RARELY_USED_PRELOAD,
        "com.sec.enterprise.knox.cloudmdm.smdms" to BloatReason.RARELY_USED_PRELOAD,
        "com.miui.yellowpage" to BloatReason.RARELY_USED_PRELOAD,
        "com.miui.bugreport" to BloatReason.RARELY_USED_PRELOAD,
        "com.xiaomi.glgm" to BloatReason.RARELY_USED_PRELOAD,
    )

    private val protectedPrefixes = listOf(
        "com.android.", "com.google.android.gms", "com.google.android.gsf",
        "com.google.android.packageinstaller", "com.google.android.permissioncontroller",
        "com.google.android.webview", "com.google.android.ext.", "com.google.android.networkstack",
        "com.google.android.modulemetadata", "com.google.mainline", "com.qualcomm", "com.qti", "com.mediatek",
        "com.samsung.android.providers", "com.sec.android.app.launcher", "com.miui.home",
        "com.miui.securitycenter", "com.miui.securitycore", "com.lbe.security.miui",
        // Other manufacturers' launchers, security centres and system services.
        "com.oplus.", "com.coloros.safecenter", "com.coloros.securitypermission", "com.oppo.launcher",
        "com.vivo.permissionmanager", "com.bbk.launcher2", "com.vivo.daemonService",
        "com.huawei.android.launcher", "com.huawei.systemmanager", "com.hihonor.android.launcher",
        "com.transsion.hilauncher", "com.transsion.phonemaster",
        "com.motorola.launcher3", "com.sonymobile.home", "com.asus.launcher",
        "com.google.android.apps.nexuslauncher", "com.sec.android.app.SecSetupWizard",
    )

    /**
     * True for anything that must never be removed. [alsoProtected] adds packages known
     * only at runtime: the current launcher and keyboard, this app and Shizuku.
     */
    fun isProtected(packageName: String, alsoProtected: Set<String> = emptySet()): Boolean =
        packageName == "android" ||
            packageName in alsoProtected ||
            protectedPrefixes.any { packageName.startsWith(it) }

    fun isRecommended(packageName: String, alsoProtected: Set<String> = emptySet()): Boolean =
        packageName in recommended && !isProtected(packageName, alsoProtected)
}
