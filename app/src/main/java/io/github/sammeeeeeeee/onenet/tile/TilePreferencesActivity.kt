package io.github.sammeeeeeeee.onenet.tile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

/** Long-press on the tile: Wi-Fi settings, as on Pixel. */
class TilePreferencesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
