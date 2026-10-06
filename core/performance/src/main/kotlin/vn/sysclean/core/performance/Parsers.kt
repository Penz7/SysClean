package vn.sysclean.core.performance

/** How Android currently treats a process, coarsened to what the user can act on. */
enum class MemoryGroup {
    /** Kernel-side and core system processes. */
    SYSTEM,

    /** On screen, perceptible (keyboard, music) or bound to something visible. */
    ACTIVE,

    /** Services and recently used apps kept around in the background. */
    BACKGROUND,

    /** Kept only as a cache; Android frees it instantly when memory is needed. */
    CACHED,
}

data class ProcessMemory(val name: String, val pid: Int, val pssKb: Long, val group: MemoryGroup) {
    /** "com.app:remote" -> "com.app"; native daemons have no package. */
    val packageName: String? get() = name.substringBefore(':').takeIf { '.' in it && !it.startsWith("/") }
}

data class MemoryMap(
    val totalKb: Long,
    val freeKb: Long,
    val usedKb: Long,
    val zramPhysicalKb: Long,
    val swapUsedKb: Long,
    val processes: List<ProcessMemory>,
)

data class FrameStats(
    val totalFrames: Long,
    val jankyFrames: Long,
    val p50Ms: Int?,
    val p90Ms: Int?,
    val p99Ms: Int?,
) {
    val jankyPercent: Double get() = if (totalFrames > 0) jankyFrames * 100.0 / totalFrames else 0.0
}

/** Share of the last minute some task stalled waiting for memory or I/O, in percent. */
data class Pressure(val someAvg60: Double, val fullAvg60: Double)

/** Parsers for the text dumped by Android system services. Pure, so tested on real dumps. */
object Parsers {

    private fun kb(text: String): Long = text.replace(",", "").toLongOrNull() ?: 0

    fun meminfo(text: String): MemoryMap? {
        fun total(label: String) = Regex("^\\s*$label:\\s+([\\d,]+)K", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.let(::kb)
        val total = total("Total RAM") ?: return null
        val zram = Regex("ZRAM:\\s+([\\d,]+)K physical used for\\s+([\\d,]+)K in swap").find(text)

        val section = text.substringAfter("Total PSS by OOM adjustment:", "").substringBefore("Total PSS by category:")
        val heading = Regex("^ {4}[\\d,]+K: (.+)$")
        val process = Regex("^ {6,}([\\d,]+)K: (\\S+) \\(pid (\\d+)")
        var group = MemoryGroup.SYSTEM
        val processes = section.lineSequence().mapNotNull { line ->
            heading.find(line)?.let {
                group = groupOf(it.groupValues[1])
                return@mapNotNull null
            }
            process.find(line)?.let {
                ProcessMemory(it.groupValues[2], it.groupValues[3].toInt(), kb(it.groupValues[1]), group)
            }
        }.toList()

        return MemoryMap(
            totalKb = total,
            freeKb = total("Free RAM") ?: 0,
            usedKb = total("Used RAM") ?: 0,
            zramPhysicalKb = zram?.groupValues?.get(1)?.let(::kb) ?: 0,
            swapUsedKb = zram?.groupValues?.get(2)?.let(::kb) ?: 0,
            processes = processes,
        )
    }

    private fun groupOf(category: String): MemoryGroup = when (category.trim()) {
        "Native", "System", "Persistent", "Persistent Service" -> MemoryGroup.SYSTEM
        "Cached" -> MemoryGroup.CACHED
        "A Services", "B Services", "Previous", "Picked", "Backup", "Heavy Weight" -> MemoryGroup.BACKGROUND
        // Foreground, Visible, Perceptible*, Home and anything new: treat as in use, the safe side.
        else -> MemoryGroup.ACTIVE
    }

    /**
     * Packages whose main APK is only verified (interpreted/JIT), i.e. not ahead-of-time
     * compiled. Split APKs are ignored: "verify" is their normal state.
     */
    fun unoptimizedPackages(dexopt: String): Set<String> {
        val unoptimized = mutableSetOf<String>()
        var pkg: String? = null
        var primarySeen = false
        var inPrimary = false
        for (line in dexopt.lineSequence()) {
            when {
                line.startsWith("  [") -> {
                    pkg = line.trim().removePrefix("[").removeSuffix("]")
                    primarySeen = false
                    inPrimary = false
                }
                line.startsWith("    path: ") -> {
                    inPrimary = !primarySeen && !line.substringAfterLast('/').startsWith("split_")
                    if (inPrimary) primarySeen = true
                }
                inPrimary && "status=" in line -> {
                    val status = line.substringAfter("status=").substringBefore(']')
                    if (status in UNOPTIMIZED && pkg != null) unoptimized += pkg
                    inPrimary = false
                }
            }
        }
        return unoptimized
    }

    private val UNOPTIMIZED = setOf("verify", "quicken", "extract", "assume-verified")

    fun gfxinfo(text: String): FrameStats? {
        fun long(label: String) = Regex("^$label: (\\d+)", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.toLongOrNull()
        fun percentile(p: Int) = Regex("^${p}th percentile: (\\d+)ms", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.toIntOrNull()
        val total = long("Total frames rendered") ?: return null
        return FrameStats(total, long("Janky frames") ?: 0, percentile(50), percentile(90), percentile(99))
    }

    fun pressure(text: String): Pressure? {
        fun avg60(kind: String) = Regex("^$kind .*avg60=([\\d.]+)", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        return Pressure(avg60("some") ?: return null, avg60("full") ?: 0.0)
    }

}
