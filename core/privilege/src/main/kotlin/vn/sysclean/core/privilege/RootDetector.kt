package vn.sysclean.core.privilege

import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * Looks for traces of root without ever invoking `su`, so detection never pops a
 * superuser prompt. Hidden-root setups (Zygisk DenyList etc.) can still slip past.
 */
internal object RootDetector {
    private val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/system/sd/xbin/su", "/data/local/su", "/data/local/bin/su", "/data/local/xbin/su",
        "/debug_ramdisk/su", "/data/adb/magisk", "/data/adb/ksu", "/data/adb/ap",
    )

    private val managerPackages = mapOf(
        "com.topjohnwu.magisk" to "Magisk",
        "io.github.vvb2060.magisk" to "Magisk Alpha",
        "io.github.huskydg.magisk" to "Kitsune Mask",
        "me.weishu.kernelsu" to "KernelSU",
        "com.rifsxd.ksunext" to "KernelSU Next",
        "me.bmax.apatch" to "APatch",
        "eu.chainfire.supersu" to "SuperSU",
    )

    fun indicators(packageManager: PackageManager): List<String> = buildList {
        suPaths.filter { File(it).exists() }.forEach { add(it) }
        pathEntries().map { File(it, "su") }.filter { it.exists() }.forEach { add(it.path) }
        managerPackages.forEach { (pkg, name) -> if (packageManager.isInstalled(pkg)) add(name) }
        if (Build.TAGS?.contains("test-keys") == true) add("Build.TAGS=test-keys")
    }.distinct()

    private fun pathEntries(): List<String> =
        System.getenv("PATH")?.split(':')?.filter { it.isNotBlank() }.orEmpty()
}

internal fun PackageManager.isInstalled(packageName: String): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(packageName, 0)
    }
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}
