package vn.sysclean.core.privilege.shell

import javax.inject.Inject
import javax.inject.Singleton

/** Prefers Shizuku (no prompt, no root needed) and falls back to root mode. */
@Singleton
internal class CompositePrivilegedShell @Inject constructor(
    private val shizuku: ShizukuPrivilegedShell,
    private val root: RootShell,
) : PrivilegedShell {

    private val active: PrivilegedShell
        get() = when {
            shizuku.isReady -> shizuku
            root.isReady -> root
            else -> throw PrivilegeUnavailableException("Neither Shizuku nor root mode is available")
        }

    override val isReady: Boolean get() = shizuku.isReady || root.isReady

    override val canClearInternalCache: Boolean get() = !shizuku.isReady && root.isReady

    override suspend fun run(vararg command: String, timeoutMillis: Long) = active.run(*command, timeoutMillis = timeoutMillis)

    override suspend fun list(path: String) = active.list(path)

    override suspend fun sizeOf(path: String) = active.sizeOf(path)

    override suspend fun deleteContents(path: String) = active.deleteContents(path)

    override suspend fun delete(path: String) = active.delete(path)
}
