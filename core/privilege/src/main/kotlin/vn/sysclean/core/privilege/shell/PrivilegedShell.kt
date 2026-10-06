package vn.sysclean.core.privilege.shell

/** Result of a command run with shell (adb) privileges. */
data class ShellResult(
    val exitCode: Int,
    val output: String,
    val error: String,
    val timedOut: Boolean,
) {
    val isSuccess: Boolean get() = exitCode == 0 && !timedOut
}

/**
 * Shell-level access through Shizuku. Callers must check [isReady]; every method throws
 * [PrivilegeUnavailableException] when Shizuku is not running or not granted.
 */
interface PrivilegedShell {
    val isReady: Boolean

    /** Root can reach /data/data/<pkg>/cache; Shizuku's shell identity cannot. */
    val canClearInternalCache: Boolean get() = false

    suspend fun run(vararg command: String, timeoutMillis: Long = DEFAULT_TIMEOUT): ShellResult

    suspend fun list(path: String): List<String>

    suspend fun sizeOf(path: String): Long

    /** Empties a folder inside Android/data or obb (or, with root, an app's private cache); returns bytes freed. */
    suspend fun deleteContents(path: String): Long

    /** Deletes a folder inside Android/data or obb for good. */
    suspend fun delete(path: String): Boolean

    companion object {
        const val DEFAULT_TIMEOUT = 15_000L
    }
}

class PrivilegeUnavailableException(message: String) : IllegalStateException(message)
