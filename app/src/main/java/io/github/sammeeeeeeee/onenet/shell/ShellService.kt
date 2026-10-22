package io.github.sammeeeeeeee.onenet.shell

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.system.Os
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** Runs in a process started by Shizuku, with its identity (shell, or root). */
class ShellService() : IShellService.Stub() {

    // Shizuku v13+ prefers this constructor.
    @Suppress("unused")
    constructor(context: Context) : this()

    /** The device's own IWifiManager proxy, so OEM changes to the interface don't matter. */
    private val wifi: Any? by lazy {
        runCatching {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "wifi") as IBinder
            Class.forName("android.net.wifi.IWifiManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
        }.getOrNull()
    }

    private val callerPackage = if (Os.getuid() == 0) "root" else "com.android.shell"

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): Bundle {
        val process = ProcessBuilder("sh", "-c", command).start()
        var out = ""
        var err = ""
        val readers = listOf(
            thread { out = process.inputStream.bufferedReader().readText() },
            thread { err = process.errorStream.bufferedReader().readText() },
        )
        val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        readers.forEach { it.join(1000) }
        return Bundle().apply {
            putInt(KEY_CODE, if (finished) process.exitValue() else -1)
            putString(KEY_OUT, out)
            putString(KEY_ERR, err)
        }
    }

    override fun pauseWifi(subId: Int) = callWifi("startRestrictingAutoJoinToSubscriptionId", subId)

    override fun resumeWifi() = callWifi("stopRestrictingAutoJoinToSubscriptionId")

    // enableNetwork with disableOthers connects, for callers holding NETWORK_SETTINGS.
    override fun connectSavedNetwork(networkId: Int) =
        callWifi("enableNetwork", networkId, true, callerPackage)

    private fun callWifi(name: String, vararg args: Any): Boolean = runCatching {
        val service = wifi ?: return false
        val method = service.javaClass.methods.first { it.name == name && it.parameterTypes.size == args.size }
        method.invoke(service, *args) != false
    }.getOrDefault(false)

    companion object {
        const val KEY_CODE = "code"
        const val KEY_OUT = "out"
        const val KEY_ERR = "err"
        private const val TIMEOUT_SECONDS = 10L
    }
}
