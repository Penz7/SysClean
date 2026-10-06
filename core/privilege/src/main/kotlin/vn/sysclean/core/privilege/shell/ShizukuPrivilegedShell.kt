package vn.sysclean.core.privilege.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import vn.sysclean.core.common.di.Dispatcher
import vn.sysclean.core.common.di.SysCleanDispatcher
import vn.sysclean.core.model.ShizukuState
import vn.sysclean.core.privilege.AccessRepository
import vn.sysclean.core.privilege.IPrivilegedService
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
internal class ShizukuPrivilegedShell @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(SysCleanDispatcher.IO) private val io: CoroutineDispatcher,
    private val accessRepository: AccessRepository,
) : PrivilegedShell {

    private val mutex = Mutex()

    @Volatile
    private var service: IPrivilegedService? = null

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context.packageName, PrivilegedService::class.java.name))
            .daemon(false)
            .processNameSuffix("privileged")
            .debuggable(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            .version(SERVICE_VERSION)
    }

    override val isReady: Boolean
        get() = accessRepository.state.value.shizuku == ShizukuState.READY

    override suspend fun run(vararg command: String, timeoutMillis: Long): ShellResult = withContext(io) {
        val bundle = service().execute(arrayOf(*command), timeoutMillis)
        ShellResult(
            exitCode = bundle.getInt(PrivilegedService.KEY_EXIT, -1),
            output = bundle.getString(PrivilegedService.KEY_OUT).orEmpty(),
            error = bundle.getString(PrivilegedService.KEY_ERR).orEmpty(),
            timedOut = bundle.getBoolean(PrivilegedService.KEY_TIMED_OUT),
        )
    }

    override suspend fun list(path: String): List<String> = withContext(io) { service().listDirectory(path).toList() }

    override suspend fun sizeOf(path: String): Long = withContext(io) { service().sizeOf(path) }

    override suspend fun deleteContents(path: String): Long = withContext(io) { service().deleteContents(path) }

    override suspend fun delete(path: String): Boolean = withContext(io) { service().deleteRecursively(path) }

    /** Binds lazily and rebinds if the shell process died (e.g. Shizuku restarted). */
    private suspend fun service(): IPrivilegedService = mutex.withLock {
        if (!isReady) throw PrivilegeUnavailableException("Shizuku is not ready")
        service?.takeIf { it.asBinder().pingBinder() } ?: bind()
    }

    private suspend fun bind(): IPrivilegedService = withTimeout(BIND_TIMEOUT) {
        suspendCancellableCoroutine { continuation ->
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val bound = binder?.takeIf { it.pingBinder() }?.let(IPrivilegedService.Stub::asInterface) ?: return
                    service = bound
                    // Shizuku calls this again after a reconnect; only the first call resumes.
                    if (continuation.isActive) continuation.resume(bound)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                }
            }
            Shizuku.bindUserService(serviceArgs, connection)
        }
    }

    private companion object {
        /** Bump whenever PrivilegedService changes so Shizuku replaces a running old copy. */
        const val SERVICE_VERSION = 3
        const val BIND_TIMEOUT = 10_000L
    }
}
