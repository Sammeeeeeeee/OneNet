package io.github.sammeeeeeeee.onenet.tile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import io.github.sammeeeeeeee.onenet.Icons
import io.github.sammeeeeeeee.onenet.Prefs
import io.github.sammeeeeeeee.onenet.R
import io.github.sammeeeeeeee.onenet.net.NetworkMonitor
import io.github.sammeeeeeeee.onenet.net.WifiToggle
import io.github.sammeeeeeeee.onenet.panel.PanelActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class InternetTileService : TileService() {

    private val scope = MainScope() + CoroutineExceptionHandler { _, e ->
        Log.w("InternetTileService", "Tile update failed", e)
    }
    private var listening: Job? = null
    private var searching: Job? = null

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
        searching?.cancel()
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // Pixel's Wi-Fi toggle; the panel when Shizuku can't act. Long-press opens the panel too.
    override fun onClick() {
        scope.launch {
            if (!WifiToggle.toggle(applicationContext)) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this@InternetTileService,
                        0,
                        Intent(this@InternetTileService, PanelActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE,
                    )
                )
            }
        }
    }

    private fun render(content: TileContent) {
        val tile = qsTile ?: return
        tile.label = content.title
        tile.subtitle = content.subtitle
        if (content.icon == TileIcon.WifiSearching) {
            if (searching?.isActive != true) {
                tile.icon = Icon.createWithResource(this, content.icon.drawable)
                searching = scope.launch { animateSearching() }
            }
        } else {
            searching?.cancel()
            tile.icon = Icon.createWithResource(this, content.icon.drawable)
        }
        tile.state = if (content.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.contentDescription = listOfNotNull(content.title, content.subtitle).joinToString(", ")
        tile.stateDescription = content.subtitle
        tile.updateTile()
    }

    // SystemUI doesn't run animated icons for app tiles, so step through the frames here.
    private suspend fun animateSearching() {
        var frame = 0
        while (true) {
            delay(SEARCH_FRAME_MS)
            frame = (frame + 1) % SEARCH_FRAMES.size
            qsTile?.run {
                icon = Icon.createWithResource(this@InternetTileService, Icons.wifi(SEARCH_FRAMES[frame]))
                updateTile()
            }
        }
    }

    private companion object {
        val SEARCH_FRAMES = intArrayOf(SEARCH_START_LEVEL, 2, 3, 4)
        const val SEARCH_FRAME_MS = 300L
    }
}

private val TileIcon.drawable: Int
    get() = when (this) {
        is TileIcon.Wifi -> Icons.wifi(level, noInternet)
        is TileIcon.Cellular -> Icons.cell(level, noInternet)
        is TileIcon.NoInternet ->
            if (networksAvailable) R.drawable.ic_qs_no_internet_available else R.drawable.ic_qs_no_internet_unavailable
        TileIcon.Ethernet -> R.drawable.ic_qs_ethernet
        TileIcon.Globe -> R.drawable.ic_qs_globe
        TileIcon.WifiSearching -> Icons.wifi(SEARCH_START_LEVEL)
    }

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
        searching = getString(R.string.searching),
        connecting = getString(R.string.connecting),
    )
}

private const val SEARCH_START_LEVEL = 1
