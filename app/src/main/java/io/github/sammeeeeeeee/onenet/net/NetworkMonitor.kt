package io.github.sammeeeeeeee.onenet.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.provider.Settings
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import io.github.sammeeeeeeee.onenet.Prefs
import io.github.sammeeeeeeee.onenet.shell.Shell
import io.github.sammeeeeeeee.onenet.shell.WifiStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withTimeoutOrNull

/** Live [NetworkState] while collected. Sharing stops with [scope]. */
class NetworkMonitor(context: Context, scope: CoroutineScope) {

    private val context = context.applicationContext
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.getSystemService(WifiManager::class.java)
    private val telephony = context.getSystemService(TelephonyManager::class.java)

    private val defaultNetwork = defaultNetworkFlow()
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    val state: Flow<NetworkState> = combine(
        defaultNetwork,
        ssidFlow(),
        broadcastFlow(WifiManager.WIFI_STATE_CHANGED_ACTION) { wifi.isWifiEnabled },
        broadcastFlow(Intent.ACTION_AIRPLANE_MODE_CHANGED) { airplaneModeOn() },
        mobileFlow(),
    ) { default, ssid, wifiEnabled, airplaneMode, mobile ->
        NetworkState(airplaneMode, wifiEnabled, default, ssid, mobile)
    }.distinctUntilChanged()

    private fun defaultNetworkFlow(): Flow<DefaultNetwork?> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            private var current: Network? = null

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                current = network
                trySend(defaultNetwork(network, caps))
            }

            override fun onLost(network: Network) {
                if (network == current) {
                    current = null
                    trySend(null)
                }
            }
        }
        trySend(connectivity.activeNetwork?.let { network ->
            connectivity.getNetworkCapabilities(network)?.let { defaultNetwork(network, it) }
        })
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun defaultNetwork(network: Network, caps: NetworkCapabilities): DefaultNetwork {
        // VPNs report the transports of the networks underneath them.
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
            else -> Transport.OTHER
        }
        return DefaultNetwork(
            handle = network.networkHandle,
            transport = transport,
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            wifiLevel = if (transport == Transport.WIFI) wifiLevel(caps) else 0,
        )
    }

    private fun wifiLevel(caps: NetworkCapabilities): Int {
        val rssi = (caps.transportInfo as? WifiInfo)?.rssi?.takeIf { it > INVALID_RSSI }
            ?: caps.signalStrength.takeIf { it > INVALID_RSSI }
            ?: return 0
        val max = wifi.maxSignalLevel
        if (max <= 0) return 0
        return (wifi.calculateSignalLevel(rssi) * 4 + max / 2) / max
    }

    /** Wi-Fi name via Shizuku; apps can't read it from a tile without background location. */
    private fun ssidFlow(): Flow<String?> = combine(
        defaultNetwork.map { it?.takeIf { network -> network.transport == Transport.WIFI }?.handle }
            .distinctUntilChanged(),
        Shell.status,
    ) { handle, status -> handle to status }
        .distinctUntilChanged()
        .mapLatest { (handle, status) -> handle?.let { resolveSsid(it, status) } }

    private suspend fun resolveSsid(handle: Long, status: Shell.Status): String? {
        Prefs.cachedSsid(context, handle)?.let { return it }
        if (status != Shell.Status.READY) return null
        val ssid = withTimeoutOrNull(SSID_TIMEOUT_MS) {
            Shell.exec(WifiStatus.STATUS_COMMAND)?.out?.let(WifiStatus::ssidFromStatus)
                ?: Shell.exec(WifiStatus.NETSTATS_COMMAND)?.out?.let(WifiStatus::ssidFromNetstats)
        } ?: return null
        Prefs.cacheSsid(context, handle, ssid)
        return ssid
    }

    /** The SIM carrying mobile data now; read once per listening session. */
    private fun mobileFlow(): Flow<MobileState?> {
        val subId = SubscriptionManager.getActiveDataSubscriptionId()
            .takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
            ?: SubscriptionManager.getDefaultDataSubscriptionId()
        return if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) flowOf(null) else mobileFlow(subId)
    }

    private fun mobileFlow(subId: Int): Flow<MobileState> = callbackFlow {
        val tm = telephony.createForSubscriptionId(subId)
        val lteAs4g = NetworkTypeLabel.lteAs4g(tm.simCountryIso)
        var state = MobileState(
            carrier = carrierName(tm, tm.isNetworkRoaming),
            roaming = tm.isNetworkRoaming,
            dataEnabled = runCatching { tm.isDataEnabled }.getOrDefault(false),
        )
        fun update(next: MobileState) {
            state = next
            trySend(next)
        }

        val callback = object : TelephonyCallback(),
            TelephonyCallback.ServiceStateListener,
            TelephonyCallback.SignalStrengthsListener,
            TelephonyCallback.DisplayInfoListener,
            TelephonyCallback.UserMobileDataStateListener {

            override fun onServiceStateChanged(serviceState: ServiceState) = update(
                state.copy(
                    inService = serviceState.state == ServiceState.STATE_IN_SERVICE,
                    roaming = serviceState.roaming,
                    carrier = carrierName(tm, serviceState.roaming),
                )
            )

            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) =
                update(state.copy(level = signalStrength.level))

            override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) = update(
                state.copy(
                    networkType = NetworkTypeLabel.of(
                        telephonyDisplayInfo.networkType,
                        telephonyDisplayInfo.overrideNetworkType,
                        lteAs4g,
                    )
                )
            )

            override fun onUserMobileDataStateChanged(enabled: Boolean) =
                update(state.copy(dataEnabled = enabled))
        }
        trySend(state)
        // Without callbacks the tile still works from the initial reads above.
        val registered = runCatching { tm.registerTelephonyCallback(context.mainExecutor, callback) }.isSuccess
        awaitClose { if (registered) tm.unregisterTelephonyCallback(callback) }
    }.distinctUntilChanged()

    /** Brand name from the SIM; the visited network's name while roaming. */
    private fun carrierName(tm: TelephonyManager, roaming: Boolean): String? {
        val sim = tm.simOperatorName.takeIf { it.isNotBlank() }
        val network = tm.networkOperatorName.takeIf { it.isNotBlank() }
        return if (roaming) network ?: sim else sim ?: network
    }

    private fun airplaneModeOn() =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    private fun <T> broadcastFlow(action: String, read: () -> T): Flow<T> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(read())
            }
        }
        trySend(read())
        context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private companion object {
        const val INVALID_RSSI = -127
        const val SSID_TIMEOUT_MS = 2500L
    }
}
