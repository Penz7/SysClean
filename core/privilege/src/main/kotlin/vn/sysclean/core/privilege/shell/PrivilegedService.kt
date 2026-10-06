package vn.sysclean.core.privilege.shell

import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import vn.sysclean.core.privilege.IPrivilegedService
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Lives in a Shizuku user-service process with the shell identity, so it can reach
 * Android/data and run pm/am. Every entry point re-checks its arguments: this process
 * can do far more damage than the app itself.
 */
@Keep
class PrivilegedService() : IPrivilegedService.Stub() {

    /** Shizuku prefers a (Context) constructor when one exists. */
    @Keep
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun execute(command: Array<String>, timeoutMillis: Long): Bundle {
        val result = Bundle()
        if (!PrivilegedPaths.isCommandAllowed(command.toList())) {
            return result.apply {
                putInt(KEY_EXIT, -1)
                putString(KEY_ERR, "command not allowed: ${command.firstOrNull()}")
            }
        }
        val process = ProcessBuilder(*command).start()
        val output = StringBuilder()
        val error = StringBuilder()
        val readers = listOf(
            Thread { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { output.appendLine(it) } } },
            Thread { process.errorStream.bufferedReader().useLines { lines -> lines.forEach { error.appendLine(it) } } },
        ).onEach { it.start() }
        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        readers.forEach { it.join(1_000) }
        return result.apply {
            putInt(KEY_EXIT, if (finished) process.exitValue() else -1)
            putString(KEY_OUT, output.toString().trim())
            putString(KEY_ERR, error.toString().trim())
            putBoolean(KEY_TIMED_OUT, !finished)
        }
    }

    override fun listDirectory(path: String): Array<String> {
        if (!PrivilegedPaths.isUnderPrivilegedRoot(path)) return emptyArray()
        return File(path).list() ?: emptyArray()
    }

    override fun sizeOf(path: String): Long {
        if (!PrivilegedPaths.isUnderPrivilegedRoot(path)) return 0
        return File(path).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    override fun deleteContents(path: String): Long {
        if (!PrivilegedPaths.isDeletable(path)) return 0
        return File(path).listFiles().orEmpty().sumOf { child ->
            val size = child.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            if (child.deleteRecursively()) size else 0
        }
    }

    override fun deleteRecursively(path: String): Boolean {
        if (!PrivilegedPaths.isDeletable(path)) return false
        val target = File(path)
        return !target.exists() || target.deleteRecursively()
    }

    companion object {
        const val KEY_EXIT = "exit"
        const val KEY_OUT = "out"
        const val KEY_ERR = "err"
        const val KEY_TIMED_OUT = "timedOut"
    }
}
