package vn.sysclean.core.privilege.shell

import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Runs a local process. An interface so root command building can be unit-tested. */
interface ProcessRunner {
    fun run(command: List<String>, timeoutMillis: Long): ShellResult
}

internal class SystemProcessRunner @Inject constructor() : ProcessRunner {
    override fun run(command: List<String>, timeoutMillis: Long): ShellResult = try {
        val process = ProcessBuilder(command).start()
        val output = StringBuilder()
        val error = StringBuilder()
        val readers = listOf(
            Thread { process.inputStream.bufferedReader().useLines { it.forEach(output::appendLine) } },
            Thread { process.errorStream.bufferedReader().useLines { it.forEach(error::appendLine) } },
        ).onEach { it.start() }
        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) process.destroyForcibly()
        readers.forEach { it.join(1_000) }
        ShellResult(
            exitCode = if (finished) process.exitValue() else -1,
            output = output.toString().trim(),
            error = error.toString().trim(),
            timedOut = !finished,
        )
    } catch (e: java.io.IOException) {
        // No su binary on the device.
        ShellResult(exitCode = -1, output = "", error = e.message.orEmpty(), timedOut = false)
    }
}
