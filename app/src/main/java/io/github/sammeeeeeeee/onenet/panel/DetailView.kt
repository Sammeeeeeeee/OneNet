package io.github.sammeeeeeeee.onenet.panel

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import io.github.sammeeeeeeee.onenet.Icons
import io.github.sammeeeeeeee.onenet.R
import io.github.sammeeeeeeee.onenet.net.NetworkState
import io.github.sammeeeeeeee.onenet.net.Transport
import io.github.sammeeeeeeee.onenet.shell.Security
import io.github.sammeeeeeeee.onenet.shell.WifiList
import io.github.sammeeeeeeee.onenet.shell.WifiNetwork

/** Pixel's internet panel rows, as RemoteViews for One UI's own tile panel. */
object DetailView {

    private const val MAX_NETWORKS = 8

    fun build(context: Context, state: NetworkState, wifi: WifiList?, ready: Boolean): RemoteViews {
        val labels = labels(context)
        val views = RemoteViews(context.packageName, R.layout.detail_panel)
        val wifiEnabled = wifi?.enabled ?: state.wifiEnabled
        val mobile = state.mobile.takeUnless { state.airplaneMode }
        val mobileActive = state.default?.transport == Transport.CELLULAR
        val connected = wifi?.connected?.takeIf { wifiEnabled }
        val others = wifi?.others.orEmpty().takeIf { wifiEnabled }.orEmpty()
        val hasNetworks = connected != null || others.isNotEmpty()
        val searching = ready && wifiEnabled && wifi == null

        views.setViewVisibility(R.id.notice, if (ready) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.notice, context.getString(R.string.detail_shizuku))

        if (mobile == null) {
            views.setViewVisibility(R.id.mobile_row, View.GONE)
        } else {
            views.setImageViewResource(R.id.mobile_icon, Icons.cell(mobile.level, !mobile.dataEnabled || !mobile.inService))
            views.setTextViewText(R.id.mobile_title, mobile.carrier ?: context.getString(R.string.mobile_data))
            val summary = PanelText.mobileSummary(mobile, mobileActive, labels)
            views.setTextViewText(R.id.mobile_summary, summary)
            views.setViewVisibility(R.id.mobile_summary, if (summary.isNullOrEmpty()) View.GONE else View.VISIBLE)
            views.setCompoundButtonChecked(R.id.mobile_switch, mobile.dataEnabled)
            views.setBoolean(R.id.mobile_switch, "setEnabled", ready)
            views.setOnCheckedChangeResponse(
                R.id.mobile_switch,
                RemoteViews.RemoteResponse.fromPendingIntent(checkedAction(context, DetailActionReceiver.ACTION_SET_MOBILE_DATA)),
            )
            // Pixel: tapping the carrier moves traffic to it, unless it's already in use or off.
            if (ready && mobile.dataEnabled && !mobileActive) {
                views.setOnClickPendingIntent(R.id.mobile_row, action(context, Intent(DetailActionReceiver.ACTION_USE_MOBILE)))
            }
        }

        views.setCompoundButtonChecked(R.id.wifi_switch, wifiEnabled)
        views.setBoolean(R.id.wifi_switch, "setEnabled", ready)
        views.setOnCheckedChangeResponse(
            R.id.wifi_switch,
            RemoteViews.RemoteResponse.fromPendingIntent(checkedAction(context, DetailActionReceiver.ACTION_SET_WIFI)),
        )

        views.setTextViewText(R.id.subtitle, PanelText.subtitle(wifiEnabled, hasNetworks, searching, mobile, mobileActive, labels))
        views.removeAllViews(R.id.network_list)
        if (connected != null) {
            val online = state.default?.transport == Transport.WIFI && state.default.validated
            views.addView(R.id.network_list, row(context, connected, connected = true, online = online, ready = ready))
        }
        others.take(MAX_NETWORKS).forEach {
            views.addView(R.id.network_list, row(context, it, connected = false, online = false, ready = ready))
        }
        return views
    }

    private fun row(context: Context, wifi: WifiNetwork, connected: Boolean, online: Boolean, ready: Boolean): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.detail_network_row)
        row.setImageViewResource(R.id.icon, Icons.wifi(level(wifi.rssi), noInternet = connected && !online))
        row.setViewVisibility(R.id.lock, if (wifi.security in OPEN) View.GONE else View.VISIBLE)
        row.setTextViewText(R.id.title, wifi.ssid)
        val summary = when {
            connected -> context.getString(if (online) R.string.connected else R.string.no_internet)
            wifi.savedId != null -> context.getString(R.string.saved)
            else -> null
        }
        row.setTextViewText(R.id.summary, summary)
        row.setViewVisibility(R.id.summary, if (summary == null) View.GONE else View.VISIBLE)
        row.setViewVisibility(R.id.settings, if (connected) View.VISIBLE else View.GONE)
        val savedId = wifi.savedId
        val click = when {
            connected || !ready -> wifiSettings(context)
            savedId != null -> action(
                context,
                Intent(DetailActionReceiver.ACTION_CONNECT).putExtra(DetailActionReceiver.EXTRA_NETWORK_ID, savedId),
                wifi.ssid.hashCode(),
            )
            wifi.security in OPEN -> action(
                context,
                Intent(DetailActionReceiver.ACTION_CONNECT)
                    .putExtra(DetailActionReceiver.EXTRA_SSID, wifi.ssid)
                    .putExtra(DetailActionReceiver.EXTRA_OWE, wifi.security == Security.OWE),
                wifi.ssid.hashCode(),
            )
            // Needs a password: Samsung's Wi-Fi settings ask for it.
            else -> wifiSettings(context)
        }
        row.setOnClickPendingIntent(R.id.row, click)
        return row
    }

    private fun action(context: Context, intent: Intent, requestCode: Int = intent.action.hashCode()) =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            intent.setClass(context, DetailActionReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    // Mutable so the switch can add its new state (RemoteViews.EXTRA_CHECKED).
    private fun checkedAction(context: Context, action: String) =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(action).setClass(context, DetailActionReceiver::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun wifiSettings(context: Context) =
        PendingIntent.getActivity(
            context,
            0,
            Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )

    // Same five-step scale as the tile's icons (WifiManager.calculateSignalLevel bands).
    private fun level(rssi: Int) = when {
        rssi >= -55 -> 4
        rssi >= -66 -> 3
        rssi >= -77 -> 2
        rssi >= -88 -> 1
        else -> 0
    }

    private val OPEN = setOf(Security.OPEN, Security.OWE)

    private fun labels(context: Context) = with(context) {
        PanelLabels(
            wifiIsOff = getString(R.string.wifi_is_off),
            tapANetwork = getString(R.string.tap_a_network_to_connect),
            searching = getString(R.string.searching_for_networks),
            noOtherNetworks = getString(R.string.no_other_networks),
            noNetworks = getString(R.string.no_networks),
            mobileDataOff = getString(R.string.mobile_data_off_summary),
            connectedFormat = getString(R.string.mobile_connected_format),
            connected = getString(R.string.connected),
            noConnection = getString(R.string.mobile_no_connection),
        )
    }
}
