package io.github.sammeeeeeeee.onenet.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import io.github.sammeeeeeeee.onenet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/** Runs shell commands through Shizuku. */
object Shell {

    enum class Status { READY, NO_PERMISSION, UNAVAILABLE }

    class Result(val code: Int, val out: String, val err: String)

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val PERMISSION_REQUEST = 1

    private val _status = MutableStateFlow(Status.UNAVAILABLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val service = MutableStateFlow<IShellService?>(null)
    private var binding = false

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name))
            .tag("shell")
            .processNameSuffix("shell")
            .version(BuildConfig.VERSION_CODE)
            .debuggable(BuildConfig.DEBUG)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (binder.pingBinder()) service.value = IShellService.Stub.asInterface(binder)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service.value = null
            binding = false
        }
    }

    fun init() {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            service.value = null
            binding = false
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
    }

    fun refresh() {
        _status.value = runCatching {
            when {
                !Shizuku.pingBinder() || Shizuku.isPreV11() -> Status.UNAVAILABLE
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Status.NO_PERMISSION
                else -> Status.READY
            }
        }.getOrDefault(Status.UNAVAILABLE)
    }

    /** Shows Shizuku's permission dialog. Call from a visible activity. */
    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST) }
    }

    fun shizukuLaunchIntent(context: Context) =
        context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)

    /** Null when Shizuku isn't ready within [timeoutMs] or the call fails. */
    suspend fun exec(command: String, timeoutMs: Long = 3000): Result? =
        call(timeoutMs) { it.exec(command) }?.let {
            Result(
                it.getInt(ShellService.KEY_CODE, -1),
                it.getString(ShellService.KEY_OUT).orEmpty(),
                it.getString(ShellService.KEY_ERR).orEmpty(),
            )
        }

    suspend fun pauseWifi(subId: Int) = call { it.pauseWifi(subId) } == true

    suspend fun resumeWifi() = call { it.resumeWifi() } == true

    suspend fun connectSavedNetwork(networkId: Int) = call { it.connectSavedNetwork(networkId) } == true

    private suspend fun <T> call(timeoutMs: Long = 3000, block: (IShellService) -> T): T? =
        withTimeoutOrNull(timeoutMs) {
            if (status.value != Status.READY) return@withTimeoutOrNull null
            val shell = connect() ?: return@withTimeoutOrNull null
            withContext(Dispatchers.IO) { runCatching { block(shell) }.getOrNull() }
        }

    private suspend fun connect(): IShellService? {
        val bound = withContext(Dispatchers.Main) {
            service.value?.let {
                if (it.asBinder().isBinderAlive) return@withContext true
                service.value = null
                binding = false
            }
            if (!binding) {
                binding = runCatching { Shizuku.bindUserService(serviceArgs, connection) }.isSuccess
            }
            binding
        }
        return if (bound) service.filterNotNull().first() else null
    }
}
