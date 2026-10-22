package io.github.sammeeeeeeee.onenet.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.SubscriptionManager
import io.github.sammeeeeeeee.onenet.shell.Shell
import io.github.sammeeeeeeee.onenet.shell.WifiStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pixel's Wi-Fi tile toggle (Android 17, WifiTileUserActionInteractor): pausing Wi-Fi hands
 * traffic to mobile data while the radio stays on; tapping again brings Wi-Fi back.
 */
object WifiToggle {

    enum class State { NORMAL, PAUSING, SCANNING }

    private const val SETTLE_TIMEOUT_MS = 10_000L

    private val scope = MainScope()
    private var settling: Job? = null

    private val _state = MutableStateFlow(State.NORMAL)
    val state: StateFlow<State> = _state.asStateFlow()

    /** False when Shizuku can't act; callers fall back to the panel. */
    suspend fun toggle(context: Context): Boolean {
        val status = Shell.exec(WifiStatus.STATUS_COMMAND)?.out ?: return false
        when {
            _state.value == State.PAUSING -> resume(context)
            _state.value == State.SCANNING -> pause(context)
            WifiStatus.isDisabled(status) -> enable(context)
            WifiStatus.ssidFromStatus(status) != null -> pause(context)
            else -> resume(context)
        }
        return true
    }

    /** Off Wi-Fi and onto mobile data; Wi-Fi won't auto-connect until resumed. */
    suspend fun pause(context: Context) {
        _state.value = State.PAUSING
        if (!Shell.pauseWifi(SubscriptionManager.getDefaultDataSubscriptionId())) {
            Shell.exec("cmd wifi set-wifi-enabled disabled")
        }
        settle(context) { !wifiIsDefault(it) }
    }

    suspend fun resume(context: Context) {
        _state.value = State.SCANNING
        Shell.resumeWifi()
        Shell.exec("cmd wifi start-scan")
        settle(context) { wifiIsDefault(it) }
    }

    suspend fun enable(context: Context) {
        Shell.exec("cmd wifi set-wifi-enabled enabled")
        resume(context)
    }

    private fun settle(context: Context, done: (Context) -> Boolean) {
        val app = context.applicationContext
        settling?.cancel()
        settling = scope.launch {
            withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
                while (!done(app)) delay(250)
            }
            _state.value = State.NORMAL
        }
    }

    private fun wifiIsDefault(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}
