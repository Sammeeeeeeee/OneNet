package io.github.sammeeeeeeee.onenet.panel

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.sammeeeeeeee.onenet.Icons
import io.github.sammeeeeeeee.onenet.Prefs
import io.github.sammeeeeeeee.onenet.R
import io.github.sammeeeeeeee.onenet.net.MobileState
import io.github.sammeeeeeeee.onenet.net.NetworkMonitor
import io.github.sammeeeeeeee.onenet.net.NetworkState
import io.github.sammeeeeeeee.onenet.net.Transport
import io.github.sammeeeeeeee.onenet.net.WifiToggle
import io.github.sammeeeeeeee.onenet.shell.Security
import io.github.sammeeeeeeee.onenet.shell.Shell
import io.github.sammeeeeeeee.onenet.shell.WifiList
import io.github.sammeeeeeeee.onenet.shell.WifiNetwork
import io.github.sammeeeeeeee.onenet.shell.WifiScan
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Pixel's internet panel, in One UI's styling. Opens on long-press of the tile. */
class PanelActivity : Activity() {

    private val scope = MainScope()
    private var session: Job? = null
    private var connectingTimeout: Job? = null

    private var network = NetworkState()
    private var wifiList: WifiList? = null
    private var shellStatus = Shell.Status.UNAVAILABLE
    private var scanStartedAt = 0L
    private var connecting: String? = null

    private lateinit var labels: PanelLabels
    private lateinit var wifiManager: WifiManager
    private lateinit var subtitle: TextView
    private lateinit var progress: ProgressBar
    private lateinit var shizukuBanner: View
    private lateinit var shizukuText: TextView
    private lateinit var shizukuButton: Button
    private lateinit var mobileRow: View
    private lateinit var mobileIcon: ImageView
    private lateinit var mobileTitle: TextView
    private lateinit var mobileSummary: TextView
    private lateinit var mobileSwitch: Switch
    private lateinit var wifiSwitch: Switch
    private lateinit var networks: View
    private lateinit var connectedRow: View
    private lateinit var connectedIcon: ImageView
    private lateinit var connectedTitle: TextView
    private lateinit var connectedSummary: TextView
    private lateinit var networkList: LinearLayout
    private lateinit var seeAll: View
    private lateinit var airplaneButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.panel_enter, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, R.anim.panel_exit)
        setContentView(R.layout.activity_panel)
        labels = PanelLabels(
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
        wifiManager = getSystemService(WifiManager::class.java)

        subtitle = findViewById(R.id.subtitle)
        progress = findViewById(R.id.progress)
        shizukuBanner = findViewById(R.id.shizuku_banner)
        shizukuText = findViewById(R.id.shizuku_text)
        shizukuButton = findViewById(R.id.shizuku_button)
        mobileRow = findViewById(R.id.mobile_row)
        mobileIcon = findViewById(R.id.mobile_icon)
        mobileTitle = findViewById(R.id.mobile_title)
        mobileSummary = findViewById(R.id.mobile_summary)
        mobileSwitch = findViewById(R.id.mobile_switch)
        wifiSwitch = findViewById(R.id.wifi_switch)
        networks = findViewById(R.id.networks)
        connectedRow = findViewById(R.id.connected_row)
        connectedIcon = findViewById(R.id.connected_icon)
        connectedTitle = findViewById(R.id.connected_title)
        connectedSummary = findViewById(R.id.connected_summary)
        networkList = findViewById(R.id.network_list)
        seeAll = findViewById(R.id.see_all)
        airplaneButton = findViewById(R.id.airplane_button)

        val card = findViewById<View>(R.id.card)
        val margin = resources.getDimensionPixelSize(R.dimen.panel_margin)
        card.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            (view.layoutParams as ViewGroup.MarginLayoutParams).apply {
                topMargin = bars.top + margin
                bottomMargin = bars.bottom + margin
            }
            view.requestLayout()
            insets
        }
        findViewById<View>(R.id.root).setOnClickListener { finish() }
        findViewById<View>(R.id.done).setOnClickListener { finish() }
        mobileRow.setOnClickListener { switchToMobile() }
        mobileSwitch.setOnClickListener { onMobileSwitch(mobileSwitch.isChecked) }
        wifiSwitch.setOnClickListener { onWifiSwitch(wifiSwitch.isChecked) }
        connectedRow.setOnClickListener { openWifiSettings() }
        seeAll.setOnClickListener { openWifiSettings() }
        airplaneButton.setOnClickListener { run("cmd connectivity airplane-mode disable") }
    }

    override fun onStart() {
        super.onStart()
        session = scope.launch {
            launch { NetworkMonitor(this@PanelActivity, this).state.collect { network = it; render() } }
            launch { Shell.status.collect { shellStatus = it; render() } }
            launch {
                Shell.status.map { it == Shell.Status.READY }.distinctUntilChanged().collectLatest { ready ->
                    wifiList = null
                    if (ready) pollWifi() else render()
                }
            }
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

    // Pixel rescans every 10 s while the panel is open; faster at first so the list fills.
    private suspend fun pollWifi() {
        scanStartedAt = SystemClock.elapsedRealtime()
        Shell.exec("cmd wifi start-scan")
        var round = 0
        while (true) {
            Shell.exec(WifiScan.COMMAND)?.let {
                wifiList = WifiScan.parse(it.out)
                render()
            }
            delay(if (round < 4) 2_500 else 10_000)
            round++
            if (round % 4 == 0) Shell.exec("cmd wifi start-scan")
        }
    }

    private fun render() {
        val ready = shellStatus == Shell.Status.READY
        val list = wifiList
        val wifiEnabled = list?.enabled ?: network.wifiEnabled
        val mobile = network.mobile.takeUnless { network.airplaneMode }
        val default = network.default
        val mobileActive = default?.transport == Transport.CELLULAR
        val connected = list?.connected?.takeIf { wifiEnabled }
        if (connected != null && connected.ssid == connecting) connecting = null
        val hasNetworks = connected != null || !list?.others.isNullOrEmpty()
        val searching = ready && wifiEnabled && !hasNetworks &&
            SystemClock.elapsedRealtime() - scanStartedAt < SEARCH_MS

        subtitle.text = PanelText.subtitle(wifiEnabled, hasNetworks, searching, mobile, mobileActive, labels)
        progress.visibility = if (searching || network.toggle != WifiToggle.State.NORMAL) View.VISIBLE else View.INVISIBLE

        shizukuBanner.visibility = if (ready) View.GONE else View.VISIBLE
        if (!ready) {
            val needsPermission = shellStatus == Shell.Status.NO_PERMISSION
            shizukuText.setText(if (needsPermission) R.string.panel_shizuku_permission else R.string.panel_shizuku_unavailable)
            shizukuButton.setText(if (needsPermission) R.string.setup_shizuku_allow else R.string.setup_shizuku_open)
            shizukuButton.setOnClickListener { if (needsPermission) Shell.requestPermission() else openShizuku() }
        }

        mobileRow.visibility = if (mobile != null) View.VISIBLE else View.GONE
        if (mobile != null) renderMobile(mobile, mobileActive, ready)
        wifiSwitch.isChecked = wifiEnabled
        wifiSwitch.isEnabled = ready

        val others = list?.others.orEmpty()
        networks.visibility = if (wifiEnabled && hasNetworks) View.VISIBLE else View.GONE
        connectedRow.visibility = if (connected != null) View.VISIBLE else View.GONE
        if (connected != null) {
            val online = default?.transport == Transport.WIFI && default.validated
            connectedIcon.setImageResource(Icons.wifi(level(connected.rssi), noInternet = !online))
            connectedTitle.text = connected.ssid
            connectedSummary.setText(if (online) R.string.connected else R.string.no_internet)
        }
        val rows = PanelText.networkRows(mobile != null, connected != null)
        networkList.removeAllViews()
        others.take(rows).forEach { networkList.addView(networkRow(it, ready)) }
        networkList.visibility = if (networkList.childCount > 0) View.VISIBLE else View.GONE
        seeAll.visibility = if (others.size > rows) View.VISIBLE else View.GONE

        airplaneButton.visibility = if (network.airplaneMode) View.VISIBLE else View.GONE
        airplaneButton.isEnabled = ready
    }

    private fun renderMobile(mobile: MobileState, mobileActive: Boolean, ready: Boolean) {
        val noData = !mobile.dataEnabled || !mobile.inService
        mobileIcon.setImageResource(Icons.data(off = noData))
        mobileTitle.text = mobile.carrier ?: getString(R.string.mobile_data)
        val summary = PanelText.mobileSummary(mobile, mobileActive, labels)
        mobileSummary.text = summary
        mobileSummary.visibility = if (summary.isNullOrEmpty()) View.GONE else View.VISIBLE
        mobileSwitch.isChecked = mobile.dataEnabled
        mobileSwitch.isEnabled = ready
        // Pixel: tapping the carrier switches to it, unless it's already in use or off.
        mobileRow.isEnabled = ready && mobile.dataEnabled && !mobileActive
    }

    private fun networkRow(wifi: WifiNetwork, ready: Boolean): View {
        val row = layoutInflater.inflate(R.layout.panel_network_row, networkList, false)
        row.findViewById<ImageView>(R.id.icon).setImageResource(Icons.wifi(level(wifi.rssi)))
        row.findViewById<View>(R.id.lock).visibility =
            if (wifi.security in OPEN_SECURITY) View.GONE else View.VISIBLE
        row.findViewById<TextView>(R.id.title).text = wifi.ssid
        val summary = when {
            wifi.ssid == connecting -> getString(R.string.connecting)
            wifi.savedId != null -> getString(R.string.saved)
            else -> null
        }
        row.findViewById<TextView>(R.id.summary).apply {
            text = summary
            visibility = if (summary == null) View.GONE else View.VISIBLE
        }
        row.isEnabled = ready
        row.setOnClickListener { connect(wifi) }
        return row
    }

    private fun switchToMobile() {
        scope.launch {
            WifiToggle.pause(this@PanelActivity)
            Toast.makeText(this@PanelActivity, R.string.wifi_wont_autoconnect, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onMobileSwitch(on: Boolean) {
        if (on || Prefs.hasTurnedOffMobileData(this)) {
            run(if (on) "cmd phone data enable" else "cmd phone data disable")
            return
        }
        mobileSwitch.isChecked = true
        val carrier = network.mobile?.takeIf { it.inService }?.carrier ?: getString(R.string.your_carrier)
        AlertDialog.Builder(this)
            .setTitle(R.string.mobile_data_disable_title)
            .setMessage(getString(R.string.mobile_data_disable_message, carrier))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.turn_off) { _, _ ->
                Prefs.setTurnedOffMobileData(this)
                run("cmd phone data disable")
            }
            .show()
    }

    private fun onWifiSwitch(on: Boolean) {
        scope.launch {
            if (on) WifiToggle.enable(this@PanelActivity) else Shell.exec("cmd wifi set-wifi-enabled disabled")
        }
    }

    private fun connect(wifi: WifiNetwork) {
        val savedId = wifi.savedId
        when {
            savedId != null -> startConnecting(wifi.ssid) { Shell.connectSavedNetwork(savedId) }
            wifi.security == Security.EAP -> openWifiSettings()
            wifi.security in OPEN_SECURITY -> startConnecting(wifi.ssid) { connectCommand(wifi, null) }
            else -> askPassword(wifi)
        }
    }

    private fun startConnecting(ssid: String, action: suspend () -> Boolean) {
        connecting = ssid
        render()
        connectingTimeout?.cancel()
        scope.launch {
            Shell.resumeWifi()
            if (!action()) connecting = null
            render()
        }
        connectingTimeout = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            connecting = null
            render()
        }
    }

    private suspend fun connectCommand(wifi: WifiNetwork, password: String?): Boolean {
        val type = when (wifi.security) {
            Security.OPEN -> "open"
            Security.OWE -> "owe"
            Security.WEP -> "wep"
            Security.SAE -> "wpa3"
            else -> "wpa2"
        }
        val command = buildString {
            append("cmd wifi connect-network ").append(quote(wifi.ssid)).append(' ').append(type)
            if (password != null) append(' ').append(quote(password))
        }
        return Shell.exec(command)?.code == 0
    }

    private fun askPassword(wifi: WifiNetwork) {
        val view = layoutInflater.inflate(R.layout.dialog_password, null)
        val field = view.findViewById<EditText>(R.id.password)
        view.findViewById<CheckBox>(R.id.show_password).setOnCheckedChangeListener { _, show ->
            field.inputType = InputType.TYPE_CLASS_TEXT or
                if (show) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
            field.setSelection(field.text.length)
        }
        val minLength = if (wifi.security == Security.WEP) 5 else 8
        val dialog = AlertDialog.Builder(this)
            .setTitle(wifi.ssid)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.connect) { _, _ ->
                val password = field.text.toString()
                startConnecting(wifi.ssid) { connectCommand(wifi, password) }
            }
            .create()
        dialog.setOnShowListener {
            val connectButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            connectButton.isEnabled = false
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable) {
                    connectButton.isEnabled = s.length >= minLength
                }
            })
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    private fun openWifiSettings() {
        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun openShizuku() {
        startActivity(
            Shell.shizukuLaunchIntent(this)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/"))
        )
    }

    private fun run(command: String) {
        scope.launch { Shell.exec(command) }
    }

    private fun level(rssi: Int): Int {
        val max = wifiManager.maxSignalLevel
        return if (max <= 0) 0 else (wifiManager.calculateSignalLevel(rssi) * 4 + max / 2) / max
    }

    private companion object {
        const val SEARCH_MS = 8_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
        val OPEN_SECURITY = setOf(Security.OPEN, Security.OWE)

        /** Single-quotes [value] for `sh -c`. */
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    }
}
