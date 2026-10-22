package io.github.sammeeeeeeee.onenet.tile

import android.content.Context
import io.github.sammeeeeeeee.onenet.Icons
import io.github.sammeeeeeeee.onenet.R

/** Still frame of [TileIcon]; the tile animates the searching and handover icons on top. */
internal val TileIcon.drawable: Int
    get() = when (this) {
        is TileIcon.Wifi -> Icons.wifi(level, noInternet)
        is TileIcon.MobileData -> Icons.data(noInternet)
        is TileIcon.NoInternet ->
            if (networksAvailable) R.drawable.ic_qs_no_internet_available else R.drawable.ic_qs_no_internet_unavailable
        TileIcon.Ethernet -> R.drawable.ic_qs_ethernet
        TileIcon.Globe -> R.drawable.ic_qs_globe
        TileIcon.WifiSearching -> Icons.WIFI_SEARCH[0]
        is TileIcon.Handover -> to.drawable
    }

internal fun tileLabels(context: Context) = with(context) {
    Labels(
        internet = getString(R.string.internet),
        wifi = getString(R.string.wifi),
        mobileData = getString(R.string.mobile_data),
        ethernet = getString(R.string.ethernet),
        connected = getString(R.string.connected),
        noInternet = getString(R.string.no_internet),
        notConnected = getString(R.string.not_connected),
        networksAvailable = getString(R.string.networks_available),
        networksUnavailable = getString(R.string.networks_unavailable),
        airplaneMode = getString(R.string.airplane_mode),
        roaming = getString(R.string.roaming),
        searching = getString(R.string.searching),
        connecting = getString(R.string.connecting),
        off = getString(R.string.off),
    )
}
