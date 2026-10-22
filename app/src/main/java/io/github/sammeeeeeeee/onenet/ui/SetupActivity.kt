package io.github.sammeeeeeeee.onenet.ui

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.sammeeeeeeee.onenet.Prefs
import io.github.sammeeeeeeee.onenet.R
import io.github.sammeeeeeeee.onenet.shell.Shell
import io.github.sammeeeeeeee.onenet.tile.InternetTileService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SetupActivity : Activity() {

    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val status = findViewById<TextView>(R.id.shizuku_status)
        val action = findViewById<Button>(R.id.shizuku_action)
        scope.launch {
            Shell.status.collect {
                when (it) {
                    Shell.Status.READY -> {
                        status.setText(R.string.setup_shizuku_ready)
                        action.visibility = View.GONE
                    }
                    Shell.Status.NO_PERMISSION -> {
                        status.setText(R.string.setup_shizuku_permission)
                        action.setText(R.string.setup_shizuku_allow)
                        action.setOnClickListener { Shell.requestPermission() }
                        action.visibility = View.VISIBLE
                    }
                    Shell.Status.UNAVAILABLE -> {
                        status.setText(R.string.setup_shizuku_unavailable)
                        action.setText(R.string.setup_shizuku_open)
                        action.setOnClickListener { openShizuku() }
                        action.visibility = View.VISIBLE
                    }
                }
            }
        }

        findViewById<Button>(R.id.add_tile).setOnClickListener { requestAddTile() }

        findViewById<Switch>(R.id.name_as_title).apply {
            isChecked = Prefs.nameAsTitle(context)
            setOnCheckedChangeListener { _, checked -> Prefs.setNameAsTitle(context, checked) }
        }
    }

    override fun onResume() {
        super.onResume()
        Shell.refresh()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun openShizuku() {
        startActivity(
            Shell.shizukuLaunchIntent(this)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
        )
    }

    private fun requestAddTile() {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, InternetTileService::class.java),
            getString(R.string.tile_label),
            Icon.createWithResource(this, R.drawable.ic_qs_wifi_4),
            mainExecutor,
        ) { result ->
            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.setup_tile_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.setup_tile_already_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> return@requestAddTileService
                else -> R.string.setup_tile_failed
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
