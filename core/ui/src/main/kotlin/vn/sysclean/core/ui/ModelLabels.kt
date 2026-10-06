package vn.sysclean.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import vn.sysclean.core.model.AccessLevel
import vn.sysclean.core.model.BatteryHealth
import vn.sysclean.core.model.BatteryStatus
import vn.sysclean.core.model.JunkCategory
import vn.sysclean.core.model.JunkNote
import vn.sysclean.core.model.PlugType
import vn.sysclean.core.model.StorageVolumeInfo
import vn.sysclean.core.model.ThermalStatus
import java.util.concurrent.TimeUnit

@Composable
fun JunkCategory.label(): String = stringResource(
    when (this) {
        JunkCategory.APP_CACHE -> R.string.ui_junk_app_cache
        JunkCategory.TEMP_FILES -> R.string.ui_junk_temp
        JunkCategory.THUMBNAILS -> R.string.ui_junk_thumbnails
        JunkCategory.APK_FILES -> R.string.ui_junk_apk
        JunkCategory.EMPTY_FOLDERS -> R.string.ui_junk_empty
        JunkCategory.LEFTOVER_FOLDERS -> R.string.ui_junk_leftover
        JunkCategory.DUPLICATE_FILES -> R.string.ui_junk_duplicates
        JunkCategory.SIMILAR_PHOTOS -> R.string.ui_junk_similar
        JunkCategory.BLURRY_PHOTOS -> R.string.ui_junk_blurry
        JunkCategory.LARGE_FILES -> R.string.ui_junk_large
        JunkCategory.SCREENSHOTS -> R.string.ui_junk_screenshots
        JunkCategory.OLD_DOWNLOADS -> R.string.ui_junk_old_downloads
    },
)

@Composable
fun JunkCategory.description(): String = stringResource(
    when (this) {
        JunkCategory.APP_CACHE -> R.string.ui_junk_app_cache_desc
        JunkCategory.TEMP_FILES -> R.string.ui_junk_temp_desc
        JunkCategory.THUMBNAILS -> R.string.ui_junk_thumbnails_desc
        JunkCategory.APK_FILES -> R.string.ui_junk_apk_desc
        JunkCategory.EMPTY_FOLDERS -> R.string.ui_junk_empty_desc
        JunkCategory.LEFTOVER_FOLDERS -> R.string.ui_junk_leftover_desc
        JunkCategory.DUPLICATE_FILES -> R.string.ui_junk_duplicates_desc
        JunkCategory.SIMILAR_PHOTOS -> R.string.ui_junk_similar_desc
        JunkCategory.BLURRY_PHOTOS -> R.string.ui_junk_blurry_desc
        JunkCategory.LARGE_FILES -> R.string.ui_junk_large_desc
        JunkCategory.SCREENSHOTS -> R.string.ui_junk_screenshots_desc
        JunkCategory.OLD_DOWNLOADS -> R.string.ui_junk_old_downloads_desc
    },
)

val JunkCategory.icon: ImageVector
    get() = when (this) {
        JunkCategory.APP_CACHE -> Icons.Outlined.Storage
        JunkCategory.TEMP_FILES -> Icons.Outlined.Description
        JunkCategory.THUMBNAILS -> Icons.Outlined.Image
        JunkCategory.APK_FILES -> Icons.Outlined.Android
        JunkCategory.EMPTY_FOLDERS -> Icons.Outlined.FolderOff
        JunkCategory.LEFTOVER_FOLDERS -> Icons.Outlined.FolderOpen
        JunkCategory.DUPLICATE_FILES -> Icons.Outlined.ContentCopy
        JunkCategory.SIMILAR_PHOTOS -> Icons.Outlined.Collections
        JunkCategory.BLURRY_PHOTOS -> Icons.Outlined.BlurOn
        JunkCategory.LARGE_FILES -> Icons.Outlined.SdStorage
        JunkCategory.SCREENSHOTS -> Icons.Outlined.Screenshot
        JunkCategory.OLD_DOWNLOADS -> Icons.Outlined.Download
    }


@Composable
fun JunkNote.label(): String = stringResource(
    when (this) {
        JunkNote.APK_INSTALLED -> R.string.ui_note_apk_installed
        JunkNote.APK_NEWER_THAN_INSTALLED -> R.string.ui_note_apk_newer
        JunkNote.APK_NOT_INSTALLED -> R.string.ui_note_apk_not_installed
        JunkNote.APK_INVALID -> R.string.ui_note_apk_invalid
    },
)

@Composable
fun BatteryStatus.label(): String = stringResource(
    when (this) {
        BatteryStatus.CHARGING -> R.string.ui_battery_charging
        BatteryStatus.DISCHARGING -> R.string.ui_battery_discharging
        BatteryStatus.NOT_CHARGING -> R.string.ui_battery_not_charging
        BatteryStatus.FULL -> R.string.ui_battery_full
        BatteryStatus.UNKNOWN -> R.string.ui_unknown
    },
)

@Composable
fun BatteryHealth.label(): String = stringResource(
    when (this) {
        BatteryHealth.GOOD -> R.string.ui_health_good
        BatteryHealth.OVERHEAT -> R.string.ui_health_overheat
        BatteryHealth.DEAD -> R.string.ui_health_dead
        BatteryHealth.OVER_VOLTAGE -> R.string.ui_health_over_voltage
        BatteryHealth.FAILURE -> R.string.ui_health_failure
        BatteryHealth.COLD -> R.string.ui_health_cold
        BatteryHealth.UNKNOWN -> R.string.ui_unknown
    },
)

@Composable
fun PlugType.label(): String = stringResource(
    when (this) {
        PlugType.AC -> R.string.ui_plug_ac
        PlugType.USB -> R.string.ui_plug_usb
        PlugType.WIRELESS -> R.string.ui_plug_wireless
        PlugType.DOCK -> R.string.ui_plug_dock
        PlugType.NONE -> R.string.ui_plug_none
    },
)

@Composable
fun ThermalStatus.label(): String = stringResource(
    when (this) {
        ThermalStatus.NONE -> R.string.ui_thermal_none
        ThermalStatus.LIGHT -> R.string.ui_thermal_light
        ThermalStatus.MODERATE -> R.string.ui_thermal_moderate
        ThermalStatus.SEVERE -> R.string.ui_thermal_severe
        ThermalStatus.CRITICAL -> R.string.ui_thermal_critical
        ThermalStatus.EMERGENCY -> R.string.ui_thermal_emergency
        ThermalStatus.SHUTDOWN -> R.string.ui_thermal_shutdown
        ThermalStatus.UNKNOWN -> R.string.ui_unknown
    },
)

@Composable
fun AccessLevel.label(): String = stringResource(
    when (this) {
        AccessLevel.NORMAL -> R.string.ui_access_normal
        AccessLevel.SHIZUKU -> R.string.ui_access_shizuku
        AccessLevel.ROOT -> R.string.ui_access_root
    },
)

@Composable
fun yesNo(value: Boolean?): String = stringResource(
    when (value) {
        true -> R.string.ui_yes
        false -> R.string.ui_no
        null -> R.string.ui_unknown
    },
)

@Composable
fun grantedLabel(granted: Boolean): String =
    stringResource(if (granted) R.string.ui_granted else R.string.ui_not_granted)

@Composable
fun usedOfTotal(used: String, total: String): String = stringResource(R.string.ui_used_of_total, used, total)

/** "Today", "3 days ago", or the never-used text when there is no timestamp. */
@Composable
fun lastUsedLabel(timestamp: Long?, now: Long = System.currentTimeMillis()): String {
    if (timestamp == null) return stringResource(R.string.ui_never_used)
    val days = TimeUnit.MILLISECONDS.toDays(now - timestamp).toInt()
    return if (days <= 0) stringResource(R.string.ui_today) else pluralStringResource(R.plurals.ui_days_ago, days, days)
}

/** "Deleted for good in 5 days" for an item in the recycle bin. */
@Composable
fun expiresLabel(expiresAt: Long, now: Long = System.currentTimeMillis()): String {
    // Round up: an item binned a minute ago with 14-day retention should read "14 days", not 13.
    val dayMillis = TimeUnit.DAYS.toMillis(1)
    val days = ((expiresAt - now + dayMillis - 1) / dayMillis).toInt()
    return if (days <= 0) stringResource(R.string.ui_expires_today) else pluralStringResource(R.plurals.ui_expires_in, days, days)
}

/**
 * The phone's own storage in the app's language. Android names volumes in the system
 * language, which differs from the app's when the user picked another one in SysClean.
 * SD cards keep the name Android gives them (often the card's brand).
 */
@Composable
fun StorageVolumeInfo.displayLabel(): String =
    if (isPrimary) stringResource(R.string.storage_internal) else label
