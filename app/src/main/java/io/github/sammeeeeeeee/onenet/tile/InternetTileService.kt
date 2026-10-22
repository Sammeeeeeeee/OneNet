package io.github.sammeeeeeeee.onenet.tile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.SystemClock
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.RemoteViews
import io.github.sammeeeeeeee.onenet.Icons
import io.github.sammeeeeeeee.onenet.Prefs
import io.github.sammeeeeeeee.onenet.R
import io.github.sammeeeeeeee.onenet.net.NetworkMonitor
import io.github.sammeeeeeeee.onenet.net.NetworkState
import io.github.sammeeeeeeee.onenet.net.WifiToggle
import io.github.sammeeeeeeee.onenet.panel.DetailActionReceiver
import io.github.sammeeeeeeee.onenet.panel.DetailView
import io.github.sammeeeeeeee.onenet.panel.SystemPanelActivity
import io.github.sammeeeeeeee.onenet.shell.Shell
import io.github.sammeeeeeeee.onenet.shell.WifiList
import io.github.sammeeeeeeee.onenet.shell.WifiScan
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class InternetTileService : TileService() {

    private val scope = MainScope() + CoroutineExceptionHandler { _, e ->
        Log.w("InternetTileService", "Tile update failed", e)
    }
    private var listening: Job? = null
    private var wifiPoll: Job? = null

    // Read by One UI on a binder thread when it builds the tile's panel.
    @Volatile private var state = NetworkState()
    @Volatile private var wifiList: WifiList? = null
    @Volatile private var detailOpenUntil = 0L

    private val updateDetailView by lazy {
        runCatching { TileService::class.java.getMethod("semUpdateDetailView") }.getOrNull()
    }

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            val labels = labels(this@InternetTileService)
            val monitor = NetworkMonitor(this@InternetTileService, this).state.onEach { state = it }
            launch {
                monitor.map { DetailKey(it) }.distinctUntilChanged().collect { if (detailOpen()) updateDetail() }
            }
            launch {
                DetailActionReceiver.changes.collect {
                    refreshWifiList()
                    updateDetail()
                }
            }
            combine(monitor, Prefs.nameAsTitleFlow(this@InternetTileService)) { state, nameAsTitle ->
                tileContent(state, nameAsTitle, labels)
            }
                .distinctUntilChanged()
                .collect(::render)
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        wifiPoll?.cancel()
        detailOpenUntil = 0
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // Pixel's Wi-Fi toggle; the system internet panel when Shizuku can't act.
    override fun onClick() {
        scope.launch {
            if (!WifiToggle.toggle(applicationContext)) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this@InternetTileService,
                        0,
                        SystemPanelActivity.systemInternetPanel(this@InternetTileService),
                        PendingIntent.FLAG_IMMUTABLE,
                    )
                )
            }
        }
    }

    // One UI extensions to TileService: SystemUI calls these by name (they aren't in the AOSP
    // SDK, hence no `override`) to open the tile's own panel inside the quick panel, the way
    // long-pressing Bluetooth does: title, content and a Details button. On builds that don't
    // support them, long-press falls back to SystemPanelActivity.

    @Suppress("unused")
    fun semGetDetailViewTitle(): CharSequence = getString(R.string.internet)

    @Suppress("unused")
    fun semGetDetailView(): RemoteViews {
        detailOpenUntil = SystemClock.elapsedRealtime() + DETAIL_REFRESH_WINDOW_MS
        scope.launch { pollWifiList() }
        return DetailView.build(this, state, wifiList, Shell.status.value == Shell.Status.READY)
    }

    @Suppress("unused")
    fun semGetSettingsIntent(): Intent = Intent(Settings.ACTION_WIFI_SETTINGS)

    @Suppress("unused")
    fun semIsToggleButtonExists() = false

    private fun detailOpen() = SystemClock.elapsedRealtime() < detailOpenUntil

    // Pixel rescans every 10 s while its panel is open; quicker at first so the list fills.
    private suspend fun pollWifiList() {
        if (wifiPoll?.isActive == true) return
        wifiPoll = scope.launch {
            if (!Shell.awaitReady()) return@launch
            Shell.exec("cmd wifi start-scan")
            var round = 0
            while (detailOpen()) {
                refreshWifiList()
                delay(if (round < 3) 2_500 else 10_000)
                if (++round % 3 == 0) Shell.exec("cmd wifi start-scan")
            }
        }
    }

    private suspend fun refreshWifiList() {
        val list = Shell.exec(WifiScan.COMMAND)?.out?.let(WifiScan::parse) ?: return
        if (list != wifiList) {
            wifiList = list
            updateDetail()
        }
    }

    private fun updateDetail() {
        updateDetailView?.let { method -> runCatching { method.invoke(this) } }
    }

    /** What the panel shows, minus signal strength, so it isn't rebuilt on every flicker. */
    private data class DetailKey(
        val airplaneMode: Boolean,
        val wifiEnabled: Boolean,
        val defaultNetwork: Any?,
        val mobile: Any?,
    ) {
        constructor(state: NetworkState) : this(
            state.airplaneMode,
            state.wifiEnabled,
            state.default?.let { it.transport to it.validated },
            state.mobile?.let { listOf(it.carrier, it.networkType, it.inService, it.dataEnabled) },
        )
    }

    private companion object {
        const val DETAIL_REFRESH_WINDOW_MS = 60_000L
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
        is TileIcon.Wifi -> Icons.wifi(level, noInternet)
        is TileIcon.Cellular -> Icons.cell(level, noInternet)
        is TileIcon.NoInternet ->
            if (networksAvailable) R.drawable.ic_qs_no_internet_available else R.drawable.ic_qs_no_internet_unavailable
        TileIcon.Ethernet -> R.drawable.ic_qs_ethernet
        TileIcon.Globe -> R.drawable.ic_qs_globe
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
        scanningForWifi = getString(R.string.scanning_for_wifi),
    )
}
