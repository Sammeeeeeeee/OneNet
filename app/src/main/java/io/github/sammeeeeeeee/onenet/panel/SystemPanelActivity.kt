package io.github.sammeeeeeeee.onenet.panel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

/** Long-press fallback when One UI doesn't show the tile's own panel: the system internet panel. */
class SystemPanelActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(systemInternetPanel(this))
        finish()
    }

    companion object {
        fun systemInternetPanel(context: Context): Intent {
            val panel = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
            val intent = if (panel.resolveActivity(context.packageManager) != null) panel else Intent(Settings.ACTION_WIFI_SETTINGS)
            return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
