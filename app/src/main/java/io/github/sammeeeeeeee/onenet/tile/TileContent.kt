package io.github.sammeeeeeeee.onenet.tile

import io.github.sammeeeeeeee.onenet.net.DefaultNetwork
import io.github.sammeeeeeeee.onenet.net.NetworkState
import io.github.sammeeeeeeee.onenet.net.Transport
import io.github.sammeeeeeeee.onenet.net.WifiToggle

sealed interface TileIcon {
    data class Wifi(val level: Int, val noInternet: Boolean = false) : TileIcon
    data class MobileData(val noInternet: Boolean = false) : TileIcon
    /** Dim globe with "?" (networks in range) or "×" (none, Wi-Fi off, flight mode). */
    data class NoInternet(val networksAvailable: Boolean) : TileIcon
    data object Ethernet : TileIcon
    data object Globe : TileIcon
    /** Pixel's searching animation: Wi-Fi arcs lighting up outwards, on a loop. */
    data object WifiSearching : TileIcon
    /** Wi-Fi arcs fading out, then [to]: played once as a tap hands traffic to mobile data. */
    data class Handover(val to: TileIcon) : TileIcon
}

data class TileContent(
    val title: String,
    val subtitle: String?,
    val icon: TileIcon,
    val active: Boolean,
)

data class Labels(
    val internet: String,
    val wifi: String,
    val mobileData: String,
    val ethernet: String,
    val connected: String,
    val noInternet: String,
    val notConnected: String,
    val networksAvailable: String,
    val networksUnavailable: String,
    val airplaneMode: String,
    val roaming: String,
    val searching: String,
    val connecting: String,
    val off: String,
)

/**
 * The combined Internet tile Pixels shipped on Android 12–16 (code in AOSP's
 * InternetTileViewModel; Android 17 split it into Wi-Fi and mobile data tiles), in One UI's
 * words: the name of One UI's own tile for whichever network carries traffic ("Wi-Fi",
 * "Mobile data"), over its details. [nameAsTitle] puts the network name on top instead, for
 * quick panels that show only one line.
 */
fun tileContent(state: NetworkState, nameAsTitle: Boolean, labels: Labels): TileContent {
    // Mid-toggle, show where the tap is heading, as Pixel's tile does. Bringing Wi-Fi back it
    // searches, then names the network while it connects, then shows it carrying traffic.
    when (state.toggle) {
        WifiToggle.State.SCANNING -> if (state.default?.transport != Transport.WIFI) {
            val ssid = state.ssid.takeIf { state.wifiConnected } ?: state.joining
            val status = if (state.wifiConnected || state.joining != null) labels.connecting else labels.searching
            return TileContent(
                title = if (nameAsTitle) ssid ?: labels.wifi else labels.wifi,
                subtitle = if (nameAsTitle || ssid == null) status else ssid,
                icon = TileIcon.WifiSearching,
                active = true,
            )
        }
        WifiToggle.State.PAUSING -> if (state.default?.transport == Transport.WIFI) {
            val mobile = state.mobile?.takeIf { it.dataEnabled && it.inService }
            val next = mobile?.let { DefaultNetwork(handle = 0, transport = Transport.CELLULAR, validated = true) }
            val content = tileContent(state.copy(default = next, toggle = WifiToggle.State.NORMAL), nameAsTitle, labels)
            return content.copy(icon = TileIcon.Handover(content.icon))
        }
        WifiToggle.State.NORMAL -> Unit
    }
    val default = state.default ?: return disconnected(state, labels)
    val noInternet = !default.validated
    return when (default.transport) {
        Transport.WIFI -> {
            val ssid = state.ssid
            TileContent(
                title = if (nameAsTitle) ssid ?: labels.wifi else labels.wifi,
                subtitle = when {
                    !nameAsTitle -> ssid ?: if (noInternet) labels.noInternet else labels.connected
                    noInternet -> labels.noInternet
                    ssid != null -> labels.wifi
                    else -> labels.connected
                },
                icon = TileIcon.Wifi(default.wifiLevel, noInternet),
                active = true,
            )
        }

        Transport.CELLULAR -> {
            val mobile = state.mobile
            val roaming = labels.roaming.takeIf { mobile?.roaming == true }
            val subtitle = if (nameAsTitle) {
                listOfNotNull(mobile?.networkType, roaming, labels.noInternet.takeIf { noInternet })
                    .joinToString(" · ")
            } else {
                val type = listOfNotNull(roaming, mobile?.networkType).joinToString(" — ")
                listOfNotNull(mobile?.carrier, type.ifEmpty { null }).joinToString(", ")
            }
            TileContent(
                title = if (nameAsTitle) mobile?.carrier ?: labels.mobileData else labels.mobileData,
                subtitle = subtitle.ifEmpty { if (nameAsTitle) labels.mobileData else labels.connected },
                icon = TileIcon.MobileData(noInternet),
                active = true,
            )
        }

        Transport.ETHERNET -> TileContent(labels.ethernet, labels.connected, TileIcon.Ethernet, active = true)

        Transport.OTHER -> TileContent(labels.internet, labels.connected, TileIcon.Globe, active = true)
    }
}

// Titled "Wi-Fi": a tap acts on Wi-Fi when nothing carries traffic.
private fun disconnected(state: NetworkState, labels: Labels): TileContent {
    val (subtitle, icon) = when {
        state.airplaneMode -> labels.airplaneMode to TileIcon.NoInternet(networksAvailable = false)
        !state.wifiEnabled -> labels.off to TileIcon.NoInternet(networksAvailable = false)
        state.networksAvailable == true -> labels.networksAvailable to TileIcon.NoInternet(networksAvailable = true)
        // Without Shizuku there's no scan list to tell available from unavailable.
        state.networksAvailable == null -> labels.notConnected to TileIcon.NoInternet(networksAvailable = true)
        else -> labels.networksUnavailable to TileIcon.NoInternet(networksAvailable = false)
    }
    return TileContent(labels.wifi, subtitle, icon, active = false)
}
