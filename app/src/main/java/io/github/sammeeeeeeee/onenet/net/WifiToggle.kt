package io.github.sammeeeeeeee.onenet.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.telephony.SubscriptionManager
import io.github.sammeeeeeeee.onenet.shell.Shell
import io.github.sammeeeeeeee.onenet.shell.WifiStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pixel's Wi-Fi tile toggle (Android 17, WifiTileUserActionInteractor): pausing Wi-Fi hands
 * traffic to mobile data while the radio stays on; tapping again brings Wi-Fi back.
 */
object WifiToggle {

    enum class State { NORMAL, PAUSING, SCANNING }

    private const val SETTLE_TIMEOUT_MS = 10_000L
    private const val JOIN_POLL_MS = 700L

    private val scope = MainScope()
    private var settling: Job? = null

    private val _state = MutableStateFlow(State.NORMAL)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _joining = MutableStateFlow<String?>(null)

    /** While [State.SCANNING]: the network Wi-Fi has associated with, before Android routes to it. */
    val joining: StateFlow<String?> = _joining.asStateFlow()

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
        settle(context, State.PAUSING)
        if (!Shell.pauseWifi(SubscriptionManager.getDefaultDataSubscriptionId())) {
            Shell.exec("cmd wifi set-wifi-enabled disabled")
        }
    }

    suspend fun resume(context: Context) {
        settle(context, State.SCANNING)
        Shell.resumeWifi()
        Shell.exec("cmd wifi start-scan")
    }

    suspend fun enable(context: Context) {
        Shell.exec("cmd wifi set-wifi-enabled enabled")
        resume(context)
    }

    /**
     * Holds [state] until Wi-Fi carries traffic (or stops), at most 10 s, as Pixel does. Starts
     * before the shell calls, so a caller cancelled during them (the panel closing) can't leave
     * the tile stuck mid-toggle.
     */
    private fun settle(context: Context, state: State) {
        val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        val wifiDefault = state == State.SCANNING
        settling?.cancel()
        _joining.value = null
        _state.value = state
        settling = scope.launch {
            val polling = if (wifiDefault) launch { pollJoining() } else null
            withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
                wifiIsDefaultFlow(connectivity).first { it == wifiDefault }
            }
            polling?.cancel()
            _joining.value = null
            _state.value = State.NORMAL
        }
    }

    // The status bar shows Wi-Fi as soon as it associates, a second or two before Android has
    // an IP for it and network callbacks fire. The shell sees that moment.
    private suspend fun pollJoining() {
        while (true) {
            Shell.exec(WifiStatus.STATUS_COMMAND)?.out?.let { _joining.value = WifiStatus.ssidFromStatus(it) }
            delay(JOIN_POLL_MS)
        }
    }

    // Callbacks, not activeNetwork: Android hides the active network from apps whose network
    // access is blocked in the background, which left the tile stuck mid-toggle.
    private fun wifiIsDefaultFlow(connectivity: ConnectivityManager): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }
}
