package io.github.sammeeeeeeee.onenet.shell

/** Parses the Wi-Fi name out of shell command output. */
object WifiStatus {

    /** Primary source: `cmd wifi status`. */
    const val STATUS_COMMAND = "cmd wifi status"

    /** Fallback for builds where `cmd wifi status` hides the name. */
    const val NETSTATS_COMMAND = "dumpsys netstats | grep -E 'iface=wlan.*(networkId|wifiNetworkKey)'"

    const val SCAN_RESULTS_COMMAND = "cmd wifi list-scan-results"

    private const val CONNECTED_PREFIX = "Wifi is connected to "
    private const val UNKNOWN_SSID = "<unknown ssid>"
    private val netstatsName = Regex("""(?:wifiNetworkKey|networkId)="([^"]*)"""")
    private val bssid = Regex("""^\s*(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}\s""", RegexOption.MULTILINE)

    fun ssidFromStatus(output: String): String? {
        val raw = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(CONNECTED_PREFIX) }
            ?.removePrefix(CONNECTED_PREFIX)
            ?: return null
        if (raw.isEmpty() || raw == UNKNOWN_SSID) return null
        return if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
            raw.substring(1, raw.length - 1)
        } else {
            raw
        }
    }

    fun isDisabled(statusOutput: String) = statusOutput.lineSequence().any { it.trim() == "Wifi is disabled" }

    fun ssidFromNetstats(output: String): String? =
        netstatsName.find(output)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }

    /** True when `cmd wifi list-scan-results` lists at least one access point. */
    fun hasScanResults(output: String) = bssid.containsMatchIn(output)
}
