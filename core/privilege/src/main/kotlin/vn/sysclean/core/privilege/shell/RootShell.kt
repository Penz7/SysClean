package vn.sysclean.core.privilege.shell

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import vn.sysclean.core.common.di.Dispatcher
import vn.sysclean.core.common.di.SysCleanDispatcher
import vn.sysclean.core.privilege.AccessRepository
import javax.inject.Inject
import javax.inject.Singleton

/** [PrivilegedShell] over `su`. Only used after the user explicitly turned root mode on. */
@Singleton
internal class RootShell @Inject constructor(
    private val runner: ProcessRunner,
    private val accessRepository: AccessRepository,
    @Dispatcher(SysCleanDispatcher.IO) private val io: CoroutineDispatcher,
) : PrivilegedShell {

    override val isReady: Boolean get() = accessRepository.state.value.rootMode

    override val canClearInternalCache: Boolean get() = isReady

    override suspend fun run(vararg command: String, timeoutMillis: Long): ShellResult =
        su(RootCommands.command(command.toList()), timeoutMillis)

    override suspend fun list(path: String): List<String> =
        su(RootCommands.list(path)).output.lines().filter { it.isNotBlank() }

    override suspend fun sizeOf(path: String): Long = RootCommands.parseKib(su(RootCommands.sizeOf(path)).output)

    override suspend fun deleteContents(path: String): Long =
        su(RootCommands.deleteContents(path)).let { if (it.isSuccess) RootCommands.parseKib(it.output) else 0 }

    override suspend fun delete(path: String): Boolean = su(RootCommands.delete(path)).isSuccess

    private suspend fun su(script: String, timeoutMillis: Long = PrivilegedShell.DEFAULT_TIMEOUT): ShellResult {
        if (!isReady) throw PrivilegeUnavailableException("Root mode is off")
        return withContext(io) { runner.run(listOf("su", "-c", script), timeoutMillis) }
    }
}
