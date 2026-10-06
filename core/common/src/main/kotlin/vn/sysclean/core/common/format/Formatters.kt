package vn.sysclean.core.common.format

import java.util.Locale
import kotlin.math.abs

private val sizeUnits = arrayOf("B", "KB", "MB", "GB", "TB")

/**
 * Formats with 1000-based units, matching what Android's own Settings > Storage shows,
 * so numbers in the app line up with the system's.
 */
fun formatBytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
    if (abs(bytes) < 1000) return "$bytes B"
    var value = bytes.toDouble()
    var unit = 0
    while (abs(value) >= 1000 && unit < sizeUnits.lastIndex) {
        value /= 1000
        unit++
    }
    val pattern = if (value >= 100) "%.0f %s" else "%.1f %s"
    return String.format(locale, pattern, value, sizeUnits[unit])
}

fun formatFrequency(kHz: Long, locale: Locale = Locale.getDefault()): String =
    if (kHz >= 1_000_000) String.format(locale, "%.2f GHz", kHz / 1_000_000.0)
    else String.format(locale, "%d MHz", kHz / 1000)

fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes / 60) % 24
    val minutes = totalMinutes % 60
    return buildString {
        if (days > 0) append(days).append("d ")
        if (days > 0 || hours > 0) append(hours).append("h ")
        append(minutes).append("m")
    }
}
