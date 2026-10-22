package io.github.sammeeeeeeee.onenet.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class WifiScanTest {

    // Layout of `cmd wifi list-scan-results` rows: "  %17s  %9d  %18s   %7s    %-32s  %s".
    private fun row(bssid: String, rssi: String, ssid: String, flags: String) =
        "  %17s  %9d  %18s   %7s    %-32s  %s".format(bssid, 5180, rssi, "1.204", ssid, flags)

    private val scan = listOf(
        "    BSSID              Frequency      RSSI           Age(sec)     SSID                                 Flags",
        row("aa:bb:cc:dd:ee:01", "-48(0:-50/1:-52)", "Home", "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]"),
        row("aa:bb:cc:dd:ee:02", "      -71         ", "Home", "[WPA2-PSK-CCMP][ESS]"),
        row("aa:bb:cc:dd:ee:03", "      -60         ", "Cafe Guest", "[ESS]"),
        row("aa:bb:cc:dd:ee:04", "      -80         ", "Office", "[RSN-EAP/SHA256-CCMP][ESS]"),
        row("aa:bb:cc:dd:ee:05", "      -55         ", "", "[WPA2-PSK-CCMP][ESS]"),
        row("aa:bb:cc:dd:ee:06", "      -66         ", "Neighbour 5G", "[RSN-SAE-CCMP][ESS]"),
    ).joinToString("\n")

    private val saved = """
        Network Id      SSID                         Security type
        3            Home                             PSK
        7            Neighbour 5G                     SAE
    """.trimIndent()

    private val status = """
        Wifi is enabled
        Wifi scanning is always available
        ==== Primary ClientModeManager instance ====
        Wifi is connected to "Home"
        WifiInfo: SSID: "Home", BSSID: aa:bb:cc:dd:ee:01, RSSI: -49, Link speed: 866Mbps
    """.trimIndent()

    private fun output(status: String) = "$status\n--onenet--\n$scan\n--onenet--\n$saved\n"

    @Test fun connectedNetwork() = assertEquals(
        WifiNetwork("Home", rssi = -49, security = Security.PSK, savedId = 3),
        WifiScan.parse(output(status)).connected,
    )

    @Test fun othersSavedFirstThenStrongest() = assertEquals(
        listOf(
            WifiNetwork("Neighbour 5G", -66, Security.SAE, savedId = 7),
            WifiNetwork("Cafe Guest", -60, Security.OPEN),
            WifiNetwork("Office", -80, Security.EAP),
        ),
        WifiScan.parse(output(status)).others,
    )

    @Test fun similarSignalsSortByName() {
        val close = listOf(
            row("aa:bb:cc:dd:ee:07", "      -61         ", "Zeta", "[ESS]"),
            row("aa:bb:cc:dd:ee:08", "      -63         ", "Alpha", "[ESS]"),
        ).joinToString("\n")
        assertEquals(
            listOf("Alpha", "Zeta"),
            WifiScan.parse("Wifi is enabled\n--onenet--\n$close\n--onenet--\n").others.map { it.ssid },
        )
    }

    @Test fun strongestAccessPointWins() =
        assertEquals(-48, WifiScan.scanResults(scan).filter { it.ssid == "Home" }.maxOf { it.rssi })

    @Test fun hiddenNetworksSkipped() = assertFalse(WifiScan.scanResults(scan).any { it.ssid.isEmpty() })

    @Test fun disabled() {
        val list = WifiScan.parse(output("Wifi is disabled\nWifi scanning is always available"))
        assertFalse(list.enabled)
        assertNull(list.connected)
    }

    @Test fun security() {
        assertEquals(Security.PSK, WifiScan.security("[WPA2-PSK-CCMP][RSN-PSK+SAE-CCMP][ESS]"))
        assertEquals(Security.SAE, WifiScan.security("[RSN-SAE-CCMP][ESS]"))
        assertEquals(Security.EAP, WifiScan.security("[RSN-EAP-CCMP][ESS]"))
        assertEquals(Security.OWE, WifiScan.security("[RSN-OWE-CCMP][ESS]"))
        assertEquals(Security.WEP, WifiScan.security("[WEP][ESS]"))
        assertEquals(Security.OPEN, WifiScan.security("[ESS]"))
    }
}
