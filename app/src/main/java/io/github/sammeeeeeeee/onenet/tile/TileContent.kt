package io.github.sammeeeeeeee.onenet.tile

import io.github.sammeeeeeeee.onenet.net.NetworkState
import io.github.sammeeeeeeee.onenet.net.Transport

sealed interface TileIcon {
    data class Wifi(val level: Int, val noInternet: Boolean = false) : TileIcon
    data class Cellular(val level: Int, val noInternet: Boolean = false) : TileIcon
    /** Dim globe with "?" (networks in range) or "×" (none, Wi-Fi off, flight mode). */
    data class NoInternet(val networksAvailable: Boolean) : TileIcon
    data object Ethernet : TileIcon
    data object Globe : TileIcon
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
)

/**
 * The combined Internet tile Pixels shipped on Android 12–16 (code in AOSP's
 * InternetTileViewModel; Android 17 split it into Wi-Fi and mobile data tiles): "Internet"
 * over whichever network carries traffic. [nameAsTitle] puts the network name on top instead,
 * for quick panels that show only one line.
 */
fun tileContent(state: NetworkState, nameAsTitle: Boolean, labels: Labels): TileContent {
    val default = state.default ?: return disconnected(state, labels)
    val noInternet = !default.validated
    return when (default.transport) {
        Transport.WIFI -> {
            val ssid = state.ssid
            TileContent(
                title = if (nameAsTitle) ssid ?: labels.wifi else labels.internet,
                subtitle = when {
                    !nameAsTitle -> ssid ?: labels.wifi
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
                title = if (nameAsTitle) mobile?.carrier ?: labels.mobileData else labels.internet,
                subtitle = subtitle.ifEmpty { labels.mobileData },
                icon = TileIcon.Cellular(mobile?.level ?: 0, noInternet),
                active = true,
            )
        }

        Transport.ETHERNET -> TileContent(
            title = if (nameAsTitle) labels.ethernet else labels.internet,
            subtitle = if (nameAsTitle) labels.connected else labels.ethernet,
            icon = TileIcon.Ethernet,
            active = true,
        )

        Transport.OTHER -> TileContent(labels.internet, labels.connected, TileIcon.Globe, active = true)
    }
}

private fun disconnected(state: NetworkState, labels: Labels): TileContent {
    val (subtitle, icon) = when {
        state.airplaneMode -> labels.airplaneMode to TileIcon.NoInternet(networksAvailable = false)
        state.networksAvailable == true -> labels.networksAvailable to TileIcon.NoInternet(networksAvailable = true)
        // Without Shizuku there's no scan list to tell available from unavailable.
        state.networksAvailable == null && state.wifiEnabled ->
            labels.notConnected to TileIcon.NoInternet(networksAvailable = true)
        else -> labels.networksUnavailable to TileIcon.NoInternet(networksAvailable = false)
    }
    return TileContent(labels.internet, subtitle, icon, active = false)
}
