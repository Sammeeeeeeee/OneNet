package io.github.sammeeeeeeee.onenet.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.telephony.SubscriptionManager
import io.github.sammeeeeeeee.onenet.shell.Shell
import io.github.sammeeeeeeee.onenet.shell.WifiStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
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
        settle(context, wifiConnected = false)
    }

    suspend fun resume(context: Context) {
        _state.value = State.SCANNING
        Shell.resumeWifi()
        Shell.exec("cmd wifi start-scan")
        settle(context, wifiConnected = true)
    }

    suspend fun enable(context: Context) {
        Shell.exec("cmd wifi set-wifi-enabled enabled")
        resume(context)
    }

    /** Holds the transitional state until Wi-Fi connects (or drops), at most 10 s. */
    private fun settle(context: Context, wifiConnected: Boolean) {
        val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        settling?.cancel()
        settling = scope.launch {
            withTimeoutOrNull(SETTLE_TIMEOUT_MS) {
                wifiConnectedFlow(connectivity).first { it == wifiConnected }
            }
            _state.value = State.NORMAL
        }
    }

    // Callbacks, not activeNetwork: Android hides the active network from apps whose network
    // access is blocked in the background, which left the tile stuck on "Scanning for Wi-Fi…".
    private fun wifiConnectedFlow(connectivity: ConnectivityManager): Flow<Boolean> = callbackFlow {
        val connected = mutableSetOf<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connected += network
                trySend(true)
            }

            override fun onLost(network: Network) {
                connected -= network
                trySend(connected.isNotEmpty())
            }
        }
        @Suppress("DEPRECATION")
        trySend(connectivity.allNetworks.any {
            connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        })
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            callback,
        )
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }
}
