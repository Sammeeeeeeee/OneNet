package io.github.sammeeeeeeee.onenet.net

enum class Transport { WIFI, CELLULAR, ETHERNET, OTHER }

/** The network Android currently routes traffic over. */
data class DefaultNetwork(
    /** [android.net.Network.getNetworkHandle]; stable while the connection lasts. */
    val handle: Long,
    val transport: Transport,
    val validated: Boolean,
    /** 0..4 */
    val wifiLevel: Int = 0,
)

/** The default data SIM. */
data class MobileState(
    val carrier: String? = null,
    /** "5G", "4G", "LTE+", ... */
    val networkType: String? = null,
    /** 0..4 */
    val level: Int = 0,
    val inService: Boolean = true,
    val roaming: Boolean = false,
    val dataEnabled: Boolean = false,
)

data class NetworkState(
    val airplaneMode: Boolean = false,
    val wifiEnabled: Boolean = false,
    val default: DefaultNetwork? = null,
    val ssid: String? = null,
    val mobile: MobileState? = null,
    /** Wi-Fi networks in range while disconnected; null when unknown (no Shizuku). */
    val networksAvailable: Boolean? = null,
)
