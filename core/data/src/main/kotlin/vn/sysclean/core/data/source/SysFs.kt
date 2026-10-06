package vn.sysclean.core.data.source

import java.io.File

/**
 * Reads kernel pseudo-files. Vendors lock these down unpredictably via SELinux,
 * so every read is optional rather than an error.
 */
internal object SysFs {
    fun readText(path: String): String? = try {
        File(path).takeIf { it.canRead() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    fun readLong(path: String): Long? = readText(path)?.toLongOrNull()

    /** Parses ranges such as "0-7" or "0-3,6" from /sys/devices/system/cpu/possible. */
    fun parseCpuRange(spec: String): List<Int> = spec.split(',').flatMap { part ->
        val bounds = part.trim().split('-').mapNotNull { it.trim().toIntOrNull() }
        when (bounds.size) {
            1 -> listOf(bounds[0])
            2 -> (bounds[0]..bounds[1]).toList()
            else -> emptyList()
        }
    }

    /** Values from /proc/meminfo are in kB; returns bytes keyed by field name. */
    fun parseMeminfo(text: String): Map<String, Long> = text.lineSequence().mapNotNull { line ->
        val name = line.substringBefore(':', "").trim()
        val kb = line.substringAfter(':', "").trim().substringBefore(' ').toLongOrNull()
        if (name.isEmpty() || kb == null) null else name to kb * 1024
    }.toMap()

    fun cpuinfoField(text: String, field: String): String? = text.lineSequence()
        .firstOrNull { it.substringBefore(':').trim().equals(field, ignoreCase = true) }
        ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }

    /** Reads a system property through the getprop binary; hidden-API reflection is blocked on modern targets. */
    fun getprop(name: String): String? = try {
        val process = ProcessBuilder("getprop", name).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }.trim().also { process.waitFor() }
            .takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}
