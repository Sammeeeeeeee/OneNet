package io.github.sammeeeeeeee.onenet.tile

import io.github.sammeeeeeeee.onenet.net.NetworkState
import io.github.sammeeeeeeee.onenet.net.Transport

sealed interface TileIcon {
    data class Wifi(val level: Int, val noInternet: Boolean = false) : TileIcon
    data class Cellular(val level: Int, val noInternet: Boolean = false) : TileIcon
    data object Airplane : TileIcon
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
    val noService: String,
    val notConnected: String,
    val off: String,
    val airplaneMode: String,
    val roaming: String,
)

/**
 * Pixel's Internet tile rules (AOSP InternetTileViewModel): describe whichever network carries
 * traffic. [nameAsTitle] leads with the network name; otherwise Pixel's "Internet" + name.
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
            icon = TileIcon.Globe,
            active = true,
        )

        Transport.OTHER -> TileContent(labels.internet, labels.connected, TileIcon.Globe, active = true)
    }
}

private fun disconnected(state: NetworkState, labels: Labels): TileContent {
    val mobile = state.mobile
    val (subtitle, icon) = when {
        state.airplaneMode && !state.wifiEnabled -> labels.airplaneMode to TileIcon.Airplane
        state.wifiEnabled -> labels.notConnected to TileIcon.Wifi(0)
        mobile?.dataEnabled == true ->
            (if (mobile.inService) labels.notConnected else labels.noService) to TileIcon.Cellular(0)
        else -> labels.off to TileIcon.Wifi(4)
    }
    return TileContent(labels.internet, subtitle, icon, active = false)
}
