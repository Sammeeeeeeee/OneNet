package io.github.sammeeeeeeee.onenet.ui

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.sammeeeeeeee.onenet.BuildConfig
import io.github.sammeeeeeeee.onenet.Prefs
import io.github.sammeeeeeeee.onenet.R
import io.github.sammeeeeeeee.onenet.net.NetworkMonitor
import io.github.sammeeeeeeee.onenet.shell.Shell
import io.github.sammeeeeeeee.onenet.tile.InternetTileService
import io.github.sammeeeeeeee.onenet.tile.TileContent
import io.github.sammeeeeeeee.onenet.tile.drawable
import io.github.sammeeeeeeee.onenet.tile.tileContent
import io.github.sammeeeeeeee.onenet.tile.tileLabels
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class SetupActivity : Activity() {

    private val scope = MainScope()
    private var session: Job? = null

    private lateinit var previewCircle: View
    private lateinit var previewIcon: ImageView
    private lateinit var previewTitle: TextView
    private lateinit var previewSubtitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        previewCircle = findViewById(R.id.preview_circle)
        previewIcon = findViewById(R.id.preview_icon)
        previewTitle = findViewById(R.id.preview_title)
        previewSubtitle = findViewById(R.id.preview_subtitle)

        layOutAppBar()

        val status = findViewById<TextView>(R.id.shizuku_status)
        val action = findViewById<Button>(R.id.shizuku_action)
        val accent = obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent)).run {
            getColorStateList(0).also { recycle() }
        }
        val secondary = status.textColors
        scope.launch {
            Shell.status.collect {
                status.setText(
                    when (it) {
                        Shell.Status.READY -> R.string.setup_shizuku_ready
                        Shell.Status.NO_PERMISSION -> R.string.setup_shizuku_permission
                        Shell.Status.UNAVAILABLE -> R.string.setup_shizuku_unavailable
                    }
                )
                status.setTextColor(if (it == Shell.Status.READY) accent else secondary)
                action.visibility = if (it == Shell.Status.READY) View.GONE else View.VISIBLE
                action.setText(if (it == Shell.Status.NO_PERMISSION) R.string.setup_shizuku_allow else R.string.setup_shizuku_open)
                action.setOnClickListener { _ ->
                    if (it == Shell.Status.NO_PERMISSION) Shell.requestPermission() else openShizuku()
                }
            }
        }

        findViewById<View>(R.id.add_tile).setOnClickListener { requestAddTile() }

        val nameAsTitle = findViewById<Switch>(R.id.name_as_title).apply {
            isChecked = Prefs.nameAsTitle(context)
            setOnCheckedChangeListener { _, checked -> Prefs.setNameAsTitle(context, checked) }
        }
        findViewById<View>(R.id.name_as_title_row).setOnClickListener { nameAsTitle.toggle() }

        findViewById<TextView>(R.id.about).apply {
            text = getString(R.string.setup_about, BuildConfig.VERSION_NAME)
            setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) }
        }
    }

    override fun onStart() {
        super.onStart()
        session = scope.launch {
            val labels = tileLabels(this@SetupActivity)
            combine(
                NetworkMonitor(this@SetupActivity, this).state,
                Prefs.nameAsTitleFlow(this@SetupActivity),
            ) { state, nameAsTitle -> tileContent(state, nameAsTitle, labels) }
                .distinctUntilChanged()
                .collect(::showPreview)
        }
    }

    override fun onResume() {
        super.onResume()
        Shell.refresh()
    }

    override fun onStop() {
        session?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // One UI's app bar: the large title takes the top of the screen and gives way to a small one
    // as the page scrolls.
    private fun layOutAppBar() {
        val root = findViewById<View>(R.id.root)
        val scroll = findViewById<ScrollView>(R.id.scroll)
        val content = findViewById<View>(R.id.content)
        val header = findViewById<View>(R.id.header)
        val toolbar = findViewById<View>(R.id.toolbar)
        val toolbarTitle = findViewById<View>(R.id.toolbar_title)
        val padding = content.paddingStart
        root.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            toolbar.setPadding(bars.left, bars.top, bars.right, 0)
            content.setPadding(bars.left + padding, 0, bars.right + padding, bars.bottom)
            header.layoutParams.height = maxOf(
                header.minimumHeight,
                (resources.displayMetrics.heightPixels * APP_BAR_SHARE).toInt(),
            )
            header.requestLayout()
            insets
        }
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val fade = header.height * 0.4f
            header.alpha = (1f - y / fade).coerceIn(0f, 1f)
            toolbarTitle.alpha = ((y - header.height * 0.6f) / fade).coerceIn(0f, 1f)
        }
    }

    private fun showPreview(content: TileContent) {
        val colors = if (content.active) {
            R.color.preview_circle_on to R.color.preview_glyph_on
        } else {
            R.color.preview_circle_off to R.color.preview_glyph_off
        }
        previewCircle.backgroundTintList = ColorStateList.valueOf(getColor(colors.first))
        previewIcon.imageTintList = ColorStateList.valueOf(getColor(colors.second))
        previewIcon.fadeTo(content.icon.drawable)
        previewTitle.fadeTo(content.title)
        previewSubtitle.fadeTo(content.subtitle)
        previewSubtitle.visibility = if (content.subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
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

    private companion object {
        /** Share of the screen height the expanded app bar takes. */
        const val APP_BAR_SHARE = 0.36f
        const val SOURCE_URL = "https://github.com/Sammeeeeeeee/OneNet"
    }
}
