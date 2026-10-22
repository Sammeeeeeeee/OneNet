package io.github.sammeeeeeeee.onenet.shell

enum class Security { OPEN, OWE, WEP, PSK, SAE, EAP }

data class WifiNetwork(
    val ssid: String,
    val rssi: Int,
    val security: Security,
    /** Saved-network ID, or null when not saved. */
    val savedId: Int? = null,
)

data class WifiList(
    val enabled: Boolean,
    val connected: WifiNetwork?,
    /** Saved first, then strongest. */
    val others: List<WifiNetwork>,
)

/** Wi-Fi status, scan results and saved networks from `cmd wifi`. */
object WifiScan {

    private const val SEPARATOR = "--onenet--"

    /** One round trip for everything the internet panel lists. */
    const val COMMAND =
        "cmd wifi status; echo $SEPARATOR; cmd wifi list-scan-results; echo $SEPARATOR; cmd wifi list-networks"

    // "  aa:bb:cc:dd:ee:ff  5180  -61(0:-63/1:-64)  1.204    <SSID padded to 32>  [flags]"
    private val scanRow =
        Regex("""^\s*(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}\s+\d+\s+(-?\d+)(?:\([^)]*\))?\s+\S+ {4}(.*)$""")
    private val savedRow = Regex("""^(\d+)\s+(.+?)\s+(\S+)\s*$""")
    private val rssi = Regex("""RSSI: (-?\d+)""")

    fun parse(output: String): WifiList {
        val (status, scan, saved) = (output.split(SEPARATOR) + listOf("", "")).take(3)
        val savedIds = saved.lineSequence()
            .mapNotNull { savedRow.find(it.trim())?.groupValues }
            .associate { it[2] to it[1].toInt() }
        val strongest = scanResults(scan).groupBy { it.ssid }.mapValues { (_, all) -> all.maxBy { it.rssi } }
        val connectedSsid = WifiStatus.ssidFromStatus(status)
        val connected = connectedSsid?.let { ssid ->
            WifiNetwork(
                ssid = ssid,
                rssi = rssi.find(status)?.groupValues?.get(1)?.toInt() ?: strongest[ssid]?.rssi ?: -100,
                security = strongest[ssid]?.security ?: Security.OPEN,
                savedId = savedIds[ssid],
            )
        }
        val others = strongest.values
            .filter { it.ssid != connectedSsid }
            .map { it.copy(savedId = savedIds[it.ssid]) }
            .sortedWith(
                compareByDescending<WifiNetwork> { it.savedId != null }
                    .thenByDescending { it.rssi }
                    .thenBy { it.ssid.lowercase() }
            )
        return WifiList(enabled = !WifiStatus.isDisabled(status), connected, others)
    }

    fun scanResults(output: String): List<WifiNetwork> = output.lineSequence().mapNotNull { line ->
        val match = scanRow.find(line) ?: return@mapNotNull null
        val rest = match.groupValues[2]
        val ssid = rest.take(32).trimEnd()
        if (ssid.isEmpty()) null else WifiNetwork(ssid, match.groupValues[1].toInt(), security(rest.drop(32)))
    }.toList()

    fun security(flags: String) = when {
        "EAP" in flags && "PSK" !in flags && "SAE" !in flags -> Security.EAP
        "PSK" in flags -> Security.PSK
        "SAE" in flags -> Security.SAE
        "WEP" in flags -> Security.WEP
        "OWE" in flags -> Security.OWE
        else -> Security.OPEN
    }
}
