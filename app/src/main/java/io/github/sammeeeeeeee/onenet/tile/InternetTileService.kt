package io.github.sammeeeeeeee.onenet.tile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import io.github.sammeeeeeeee.onenet.Prefs
import io.github.sammeeeeeeee.onenet.R
import io.github.sammeeeeeeee.onenet.net.NetworkMonitor
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class InternetTileService : TileService() {

    private val scope = MainScope() + CoroutineExceptionHandler { _, e ->
        Log.w("InternetTileService", "Tile update failed", e)
    }
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            val labels = labels(this@InternetTileService)
            combine(
                NetworkMonitor(this@InternetTileService, this).state,
                Prefs.nameAsTitleFlow(this@InternetTileService),
            ) { state, nameAsTitle -> tileContent(state, nameAsTitle, labels) }
                .distinctUntilChanged()
                .collect(::render)
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // Interim: until OneNet's own panel lands, open the system internet panel if anything
    // handles it, else Wi-Fi settings.
    override fun onClick() {
        val panel = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        val intent = if (panel.resolveActivity(packageManager) != null) panel else Intent(Settings.ACTION_WIFI_SETTINGS)
        startActivityAndCollapse(
            PendingIntent.getActivity(
                this,
                0,
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
        )
    }

    private fun render(content: TileContent) {
        val tile = qsTile ?: return
        tile.label = content.title
        tile.subtitle = content.subtitle
        tile.icon = Icon.createWithResource(this, content.icon.drawable)
        tile.state = if (content.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.contentDescription = listOfNotNull(content.title, content.subtitle).joinToString(", ")
        tile.stateDescription = content.subtitle
        tile.updateTile()
    }
}

private val TileIcon.drawable: Int
    get() = when (this) {
        is TileIcon.Wifi -> (if (noInternet) WIFI_NO_INTERNET else WIFI)[level.coerceIn(0, 4)]
        is TileIcon.Cellular -> (if (noInternet) CELL_NO_INTERNET else CELL)[level.coerceIn(0, 4)]
        is TileIcon.NoInternet ->
            if (networksAvailable) R.drawable.ic_qs_no_internet_available else R.drawable.ic_qs_no_internet_unavailable
        TileIcon.Ethernet -> R.drawable.ic_qs_ethernet
        TileIcon.Globe -> R.drawable.ic_qs_globe
    }

private val WIFI = intArrayOf(
    R.drawable.ic_qs_wifi_0, R.drawable.ic_qs_wifi_1, R.drawable.ic_qs_wifi_2,
    R.drawable.ic_qs_wifi_3, R.drawable.ic_qs_wifi_4,
)

private val WIFI_NO_INTERNET = intArrayOf(
    R.drawable.ic_qs_wifi_no_internet_0, R.drawable.ic_qs_wifi_no_internet_1, R.drawable.ic_qs_wifi_no_internet_2,
    R.drawable.ic_qs_wifi_no_internet_3, R.drawable.ic_qs_wifi_no_internet_4,
)

private val CELL = intArrayOf(
    R.drawable.ic_qs_cell_0, R.drawable.ic_qs_cell_1, R.drawable.ic_qs_cell_2,
    R.drawable.ic_qs_cell_3, R.drawable.ic_qs_cell_4,
)

private val CELL_NO_INTERNET = intArrayOf(
    R.drawable.ic_qs_cell_no_internet_0, R.drawable.ic_qs_cell_no_internet_1, R.drawable.ic_qs_cell_no_internet_2,
    R.drawable.ic_qs_cell_no_internet_3, R.drawable.ic_qs_cell_no_internet_4,
)

private fun labels(context: Context) = with(context) {
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
    )
}
