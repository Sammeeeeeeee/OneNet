package io.github.sammeeeeeeee.onenet.panel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.sammeeeeeeee.onenet.net.WifiToggle
import io.github.sammeeeeeeee.onenet.shell.Shell
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** Taps and switches inside One UI's tile panel. */
class DetailActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                if (Shell.awaitReady()) handle(app, intent)
            } finally {
                _changes.tryEmit(Unit)
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val checked = intent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false)
        when (intent.action) {
            ACTION_SET_MOBILE_DATA -> Shell.exec(if (checked) "cmd phone data enable" else "cmd phone data disable")
            ACTION_SET_WIFI ->
                if (checked) WifiToggle.enable(context) else Shell.exec("cmd wifi set-wifi-enabled disabled")
            ACTION_USE_MOBILE -> WifiToggle.pause(context)
            ACTION_CONNECT -> {
                Shell.resumeWifi()
                val networkId = intent.getIntExtra(EXTRA_NETWORK_ID, -1)
                val ssid = intent.getStringExtra(EXTRA_SSID)
                when {
                    networkId >= 0 -> Shell.connectSavedNetwork(networkId)
                    ssid != null -> {
                        val type = if (intent.getBooleanExtra(EXTRA_OWE, false)) "owe" else "open"
                        Shell.exec("cmd wifi connect-network ${quote(ssid)} $type")
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SET_MOBILE_DATA = "io.github.sammeeeeeeee.onenet.SET_MOBILE_DATA"
        const val ACTION_SET_WIFI = "io.github.sammeeeeeeee.onenet.SET_WIFI"
        const val ACTION_USE_MOBILE = "io.github.sammeeeeeeee.onenet.USE_MOBILE"
        const val ACTION_CONNECT = "io.github.sammeeeeeeee.onenet.CONNECT"
        const val EXTRA_NETWORK_ID = "network_id"
        const val EXTRA_SSID = "ssid"
        const val EXTRA_OWE = "owe"

        private val scope = MainScope()
        private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Fires after each action, so the open panel can refresh. */
        val changes: SharedFlow<Unit> = _changes

        /** Single-quotes [value] for `sh -c`. */
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    }
}
