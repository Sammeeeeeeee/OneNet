package io.github.sammeeeeeeee.onenet.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import io.github.sammeeeeeeee.onenet.Icons
import io.github.sammeeeeeeee.onenet.Prefs
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
    private var iconAnimation: Job? = null
    private var animatedIcon: TileIcon? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            val labels = tileLabels(this@InternetTileService)
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
        iconAnimation?.cancel()
        animatedIcon = null
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
        setIcon(tile, content.icon)
        tile.state = if (content.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.contentDescription = listOfNotNull(content.title, content.subtitle).joinToString(", ")
        tile.stateDescription = content.subtitle
        tile.updateTile()
    }

    // SystemUI doesn't run animated icons for app tiles, so step through frames here.
    private fun setIcon(tile: Tile, icon: TileIcon) {
        if (icon == TileIcon.WifiSearching || icon is TileIcon.Handover) {
            if (icon == animatedIcon) return
            iconAnimation?.cancel()
            animatedIcon = icon
            iconAnimation = scope.launch { animate(icon) }
        } else {
            iconAnimation?.cancel()
            animatedIcon = null
            tile.icon = Icon.createWithResource(this, icon.drawable)
        }
    }

    private suspend fun animate(icon: TileIcon) {
        fun show(drawable: Int) {
            qsTile?.run {
                this.icon = Icon.createWithResource(this@InternetTileService, drawable)
                updateTile()
            }
        }
        if (icon is TileIcon.Handover) {
            for (drawable in HANDOVER_FRAMES) {
                show(drawable)
                delay(HANDOVER_FRAME_MS)
            }
            show(icon.to.drawable)
        } else {
            var frame = 0
            while (true) {
                show(Icons.WIFI_SEARCH[frame])
                frame = (frame + 1) % Icons.WIFI_SEARCH.size
                delay(SEARCH_FRAME_MS)
            }
        }
    }

    private companion object {
        val HANDOVER_FRAMES = intArrayOf(Icons.wifi(3), Icons.wifi(2), Icons.wifi(1), Icons.wifi(0))
        const val HANDOVER_FRAME_MS = 90L
        const val SEARCH_FRAME_MS = 220L
    }
}
