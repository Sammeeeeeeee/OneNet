package io.github.sammeeeeeeee.onenet.panel

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.window.BackEvent
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedDispatcher
import io.github.sammeeeeeeee.onenet.Icons
import io.github.sammeeeeeeee.onenet.Prefs
import io.github.sammeeeeeeee.onenet.R
import io.github.sammeeeeeeee.onenet.net.DefaultNetwork
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Pixel's internet panel, in One UI's styling. Opens on long-press of the tile. */
class PanelActivity : Activity() {

    private val scope = MainScope()
    private var session: Job? = null
    private var connectingTimeout: Job? = null

    private var network = NetworkState()
    private var wifiList: WifiList? = null
    private var shellStatus = Shell.Status.UNAVAILABLE
    private var scanStartedAt = 0L
    /** The network being joined; it takes the top row until it connects. */
    private var connecting: WifiNetwork? = null
    private val pollNow = Channel<Unit>(Channel.CONFLATED)

    /** Wi-Fi rows by SSID, reused so a network keeps its row as the list reorders. */
    private val rows = HashMap<String, View>()
    private var shape: Shape? = null

    /** A switch the user just flipped keeps its new position until the change shows up. */
    private var wifiSwitchHeld: Boolean? = null
    private var mobileSwitchHeld: Boolean? = null
    private var closing = false

    // Rows glide to their new places and fade in or out while the sheet resizes.
    private val transition = TransitionSet()
        .addTransition(Fade(Fade.OUT).setDuration(150))
        .addTransition(ChangeBounds().setDuration(380))
        .addTransition(Fade(Fade.IN).setStartDelay(120).setDuration(260))
        .setOrdering(TransitionSet.ORDERING_TOGETHER)
        .setInterpolator(PathInterpolator(0.33f, 0f, 0.2f, 1f))

    private lateinit var labels: PanelLabels
    private lateinit var wifiManager: WifiManager
    private lateinit var accent: ColorStateList
    private lateinit var primary: ColorStateList
    private lateinit var card: ViewGroup
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
    private lateinit var networks: LinearLayout
    private lateinit var seeAll: View
    private lateinit var airplaneButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The sheet animates itself (slideIn, finish); the system fades the dim in and out.
        overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
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
        obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent, android.R.attr.textColorPrimary)).apply {
            accent = getColorStateList(0)!!
            primary = getColorStateList(1)!!
            recycle()
        }

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
        seeAll = findViewById(R.id.see_all)
        airplaneButton = findViewById(R.id.airplane_button)

        card = findViewById(R.id.card)
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
        seeAll.setOnClickListener { openWifiSettings() }
        airplaneButton.setOnClickListener { run("cmd connectivity airplane-mode disable") }
        slideIn()
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback)
    }

    private fun slideIn() {
        card.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                card.viewTreeObserver.removeOnPreDrawListener(this)
                card.translationY = belowScreen()
                card.animate().translationY(0f).setDuration(ENTER_MS).setInterpolator(EASE_OUT)
                return true
            }
        })
    }

    private fun belowScreen() = ((card.parent as View).height - card.top).toFloat()

    // Predictive back: the sheet shrinks towards its bottom edge as the gesture goes.
    private val backCallback = object : OnBackAnimationCallback {
        override fun onBackProgressed(backEvent: BackEvent) {
            val scale = 1f - BACK_SHRINK * EASE_OUT.getInterpolation(backEvent.progress)
            card.pivotX = card.width / 2f
            card.pivotY = card.height.toFloat()
            card.scaleX = scale
            card.scaleY = scale
        }

        override fun onBackCancelled() {
            card.animate().scaleX(1f).scaleY(1f).setDuration(BACK_RESET_MS).setInterpolator(EASE_OUT)
        }

        override fun onBackInvoked() = finish()
    }

    override fun finish() {
        if (closing || !card.isLaidOut) {
            super.finish()
            return
        }
        closing = true
        card.animate().cancel()
        card.animate().translationY(belowScreen()).setDuration(EXIT_MS).setInterpolator(EASE_IN)
            .withEndAction { super.finish() }
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

    // Pixel rescans every 10 s while the panel is open; faster at first so the list fills, and
    // every second while joining a network so the list follows along.
    private suspend fun pollWifi() {
        scanStartedAt = SystemClock.elapsedRealtime()
        Shell.exec("cmd wifi start-scan")
        var round = 0
        while (true) {
            Shell.exec(WifiScan.COMMAND)?.let {
                wifiList = WifiScan.parse(it.out)
                render()
            }
            val wait = when {
                connecting != null -> 1_000L
                round < 4 -> 2_500L
                else -> 10_000L
            }
            withTimeoutOrNull(wait) { pollNow.receive() }
            if (connecting != null) continue
            round++
            if (round % 4 == 0) Shell.exec("cmd wifi start-scan")
        }
    }

    /** What appears, in order: changes to it animate, text-only changes don't. */
    private data class Shape(
        val banner: Boolean,
        val progress: Boolean,
        val mobile: Boolean,
        val mobileSummary: Boolean,
        val networks: List<String>,
        val details: List<Int>,
        val summaries: List<Boolean>,
        val seeAll: Boolean,
        val airplane: Boolean,
    )

    private data class Entry(val wifi: WifiNetwork, val top: Boolean, val summary: String?, val noInternet: Boolean)

    private fun render() {
        val ready = shellStatus == Shell.Status.READY
        val list = wifiList
        val wifiEnabled = list?.enabled ?: network.wifiEnabled
        val mobile = network.mobile.takeUnless { network.airplaneMode }
        val default = network.default
        val mobileActive = default?.transport == Transport.CELLULAR
        val connected = list?.connected?.takeIf { wifiEnabled }
        if (connected != null && connected.ssid == connecting?.ssid) connecting = null
        val entries = if (wifiEnabled) entries(list, connected, default) else emptyList()
        val hasNetworks = entries.isNotEmpty()
        val searching = ready && wifiEnabled && !hasNetworks &&
            SystemClock.elapsedRealtime() - scanStartedAt < SEARCH_MS
        val mobileSummaryText = mobile?.let { PanelText.mobileSummary(it, mobileActive, labels) }
        val hasTop = entries.firstOrNull()?.top == true
        val shown = entries.take(PanelText.networkRows(mobile != null, hasTop) + if (hasTop) 1 else 0)

        val next = Shape(
            banner = !ready,
            progress = searching || network.toggle != WifiToggle.State.NORMAL,
            mobile = mobile != null,
            mobileSummary = !mobileSummaryText.isNullOrEmpty(),
            networks = if (wifiEnabled && hasNetworks) shown.map { it.wifi.ssid } else emptyList(),
            details = shown.map { trailing(it) },
            summaries = shown.map { it.summary != null },
            seeAll = entries.size > shown.size,
            airplane = network.airplaneMode,
        )
        if (next != shape && card.isLaidOut) {
            // The network list fades in or out whole: its old rows would otherwise flash as they go.
            val listShownOrHidden = next.networks.isEmpty() != shape?.networks.isNullOrEmpty()
            TransitionManager.beginDelayedTransition(
                card,
                if (listShownOrHidden) transition.clone().excludeChildren(networks, true) else transition,
            )
        }
        shape = next

        subtitle.fadeTo(PanelText.subtitle(wifiEnabled, hasNetworks, searching, mobile, mobileActive, labels))
        progress.visibility = if (next.progress) View.VISIBLE else View.INVISIBLE

        shizukuBanner.visibility = if (ready) View.GONE else View.VISIBLE
        if (!ready) {
            val needsPermission = shellStatus == Shell.Status.NO_PERMISSION
            shizukuText.setText(if (needsPermission) R.string.panel_shizuku_permission else R.string.panel_shizuku_unavailable)
            shizukuButton.setText(if (needsPermission) R.string.setup_shizuku_allow else R.string.setup_shizuku_open)
            shizukuButton.setOnClickListener { if (needsPermission) Shell.requestPermission() else openShizuku() }
        }

        mobileRow.visibility = if (mobile != null) View.VISIBLE else View.GONE
        if (mobile != null) renderMobile(mobile, mobileSummaryText, mobileActive, ready)
        if (wifiSwitchHeld == wifiEnabled) wifiSwitchHeld = null
        wifiSwitch.isChecked = wifiSwitchHeld ?: wifiEnabled
        wifiSwitch.isEnabled = ready

        // Hidden, the list keeps its rows so it fades out with them.
        if (next.networks.isNotEmpty()) {
            renderNetworks(shown, ready)
            seeAll.visibility = if (next.seeAll) View.VISIBLE else View.GONE
        }
        networks.visibility = if (next.networks.isEmpty()) View.GONE else View.VISIBLE

        airplaneButton.visibility = if (network.airplaneMode) View.VISIBLE else View.GONE
        airplaneButton.isEnabled = ready
    }

    /** Pixel's list: the connected network (or the one being joined) on top, then the rest. */
    private fun entries(list: WifiList?, connected: WifiNetwork?, default: DefaultNetwork?): List<Entry> {
        val joining = connecting
        val top = joining ?: connected
        val online = default?.transport == Transport.WIFI && default.validated
        val first = top?.let {
            val summary = when {
                joining != null -> getString(R.string.connecting)
                online -> getString(R.string.connected)
                else -> getString(R.string.no_internet)
            }
            Entry(it, top = true, summary, noInternet = joining == null && !online)
        }
        val rest = (listOfNotNull(connected) + list?.others.orEmpty())
            .filter { it.ssid != top?.ssid }
            .distinctBy { it.ssid }
            .map { Entry(it, top = false, if (it.savedId != null) getString(R.string.saved) else null, noInternet = false) }
        return listOfNotNull(first) + rest
    }

    private fun trailing(entry: Entry) = when {
        !entry.top -> 0
        connecting?.ssid == entry.wifi.ssid -> 1
        else -> 2
    }

    private fun renderNetworks(shown: List<Entry>, ready: Boolean) {
        val wanted = shown.map { entry ->
            rows.getOrPut(entry.wifi.ssid) { layoutInflater.inflate(R.layout.panel_network_row, networks, false) }
                .also { bindRow(it, entry, ready) }
        }
        val keep = wanted.toSet()
        rows.values.removeAll { row ->
            (row !in keep).also { stale -> if (stale) networks.removeView(row) }
        }
        wanted.forEachIndexed { index, row ->
            if (networks.getChildAt(index) !== row) {
                networks.removeView(row)
                networks.addView(row, index)
            }
        }
    }

    private fun bindRow(row: View, entry: Entry, ready: Boolean) {
        val wifi = entry.wifi
        val joining = trailing(entry) == 1
        row.findViewById<ImageView>(R.id.icon).apply {
            setImageResource(Icons.wifi(level(wifi.rssi), entry.noInternet))
            imageTintList = if (entry.top) accent else primary
        }
        row.findViewById<View>(R.id.lock).visibility =
            if (entry.top || wifi.security in OPEN_SECURITY) View.GONE else View.VISIBLE
        row.findViewById<TextView>(R.id.title).apply {
            text = wifi.ssid
            setTextColor(if (entry.top) accent else primary)
        }
        row.findViewById<TextView>(R.id.summary).apply {
            fadeTo(entry.summary)
            visibility = if (entry.summary == null) View.GONE else View.VISIBLE
        }
        row.findViewById<View>(R.id.progress).visibility = if (joining) View.VISIBLE else View.GONE
        val details = if (entry.top && !joining) View.VISIBLE else View.GONE
        row.findViewById<View>(R.id.details_divider).visibility = details
        row.findViewById<View>(R.id.details).visibility = details
        row.isEnabled = ready || entry.top
        row.setOnClickListener {
            when {
                !entry.top -> connect(wifi)
                !joining -> openWifiSettings()
            }
        }
    }

    private fun renderMobile(mobile: MobileState, summary: String?, mobileActive: Boolean, ready: Boolean) {
        val noData = !mobile.dataEnabled || !mobile.inService
        mobileIcon.setImageResource(Icons.data(off = noData))
        mobileTitle.text = mobile.carrier ?: getString(R.string.mobile_data)
        mobileSummary.fadeTo(summary)
        mobileSummary.visibility = if (summary.isNullOrEmpty()) View.GONE else View.VISIBLE
        if (mobileSwitchHeld == mobile.dataEnabled) mobileSwitchHeld = null
        mobileSwitch.isChecked = mobileSwitchHeld ?: mobile.dataEnabled
        mobileSwitch.isEnabled = ready
        // Pixel: tapping the carrier switches to it, unless it's already in use or off.
        mobileRow.isEnabled = ready && mobile.dataEnabled && !mobileActive
    }

    /**
     * Cross-fades to [value]. Null keeps the old text, for a view about to be hidden to fade out
     * with; a hidden view changes at once.
     */
    private fun TextView.fadeTo(value: CharSequence?) {
        val wanted = value ?: ""
        if (TextUtils.equals(getTag(R.id.pending_text) as CharSequence? ?: text, wanted)) return
        setTag(R.id.pending_text, wanted)
        animate().cancel()
        when {
            value.isNullOrEmpty() -> alpha = 1f
            !isShown || text.isNullOrEmpty() -> {
                alpha = 1f
                text = value
            }
            else -> animate().alpha(0f).setDuration(TEXT_OUT_MS).withEndAction {
                text = value
                animate().alpha(1f).setDuration(TEXT_IN_MS)
            }
        }
    }

    private fun switchToMobile() {
        scope.launch {
            WifiToggle.pause(this@PanelActivity)
            Toast.makeText(this@PanelActivity, R.string.wifi_wont_autoconnect, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onMobileSwitch(on: Boolean) {
        if (on || Prefs.hasTurnedOffMobileData(this)) {
            setMobileData(on)
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
                setMobileData(false)
            }
            .show()
    }

    private fun setMobileData(on: Boolean) {
        mobileSwitchHeld = on
        run(if (on) "cmd phone data enable" else "cmd phone data disable")
        scope.launch {
            delay(SWITCH_HOLD_MS)
            if (mobileSwitchHeld == on) mobileSwitchHeld = null
            render()
        }
    }

    private fun onWifiSwitch(on: Boolean) {
        wifiSwitchHeld = on
        scope.launch {
            if (on) {
                WifiToggle.enable(this@PanelActivity)
                scanStartedAt = SystemClock.elapsedRealtime()
            } else {
                Shell.exec("cmd wifi set-wifi-enabled disabled")
            }
            pollNow.trySend(Unit)
            delay(SWITCH_HOLD_MS)
            if (wifiSwitchHeld == on) wifiSwitchHeld = null
            render()
        }
    }

    private fun connect(wifi: WifiNetwork) {
        val savedId = wifi.savedId
        when {
            savedId != null -> startConnecting(wifi) { Shell.connectSavedNetwork(savedId) }
            wifi.security == Security.EAP -> openWifiSettings()
            wifi.security in OPEN_SECURITY -> startConnecting(wifi) { connectCommand(wifi, null) }
            else -> askPassword(wifi)
        }
    }

    private fun startConnecting(wifi: WifiNetwork, action: suspend () -> Boolean) {
        connecting = wifi
        render()
        connectingTimeout?.cancel()
        scope.launch {
            Shell.resumeWifi()
            if (!action()) connecting = null
            render()
            pollNow.trySend(Unit)
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
                startConnecting(wifi) { connectCommand(wifi, password) }
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
        const val ENTER_MS = 420L
        const val EXIT_MS = 240L
        const val BACK_RESET_MS = 200L
        const val BACK_SHRINK = 0.1f
        val EASE_OUT = PathInterpolator(0.17f, 0.17f, 0f, 1f)
        val EASE_IN = PathInterpolator(0.4f, 0f, 1f, 1f)
        const val SWITCH_HOLD_MS = 5_000L
        const val TEXT_OUT_MS = 90L
        const val TEXT_IN_MS = 160L
        val OPEN_SECURITY = setOf(Security.OPEN, Security.OWE)

        /** Single-quotes [value] for `sh -c`. */
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    }
}
