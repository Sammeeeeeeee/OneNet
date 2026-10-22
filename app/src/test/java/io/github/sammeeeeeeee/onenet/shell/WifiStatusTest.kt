package io.github.sammeeeeeeee.onenet.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiStatusTest {

    private fun status(connectedLine: String?) = buildString {
        appendLine("Wifi is enabled")
        appendLine("Wifi scanning is always available")
        appendLine("==== Primary ClientModeManager instance ====")
        appendLine(connectedLine ?: "Wifi is not connected")
        appendLine("WifiInfo: SSID: \"Ignored\", BSSID: 02:00:00:00:00:00")
    }

    @Test fun quotedName() =
        assertEquals("Home Wi-Fi", WifiStatus.ssidFromStatus(status("Wifi is connected to \"Home Wi-Fi\"")))

    @Test fun quotesInsideNameKept() =
        assertEquals("Sam's \"5G\"", WifiStatus.ssidFromStatus(status("Wifi is connected to \"Sam's \"5G\"\"")))

    @Test fun hexNameReturnedRaw() =
        assertEquals("c0ffee", WifiStatus.ssidFromStatus(status("Wifi is connected to c0ffee")))

    @Test fun notConnected() = assertNull(WifiStatus.ssidFromStatus(status(null)))

    @Test fun redactedName() =
        assertNull(WifiStatus.ssidFromStatus(status("Wifi is connected to <unknown ssid>")))

    @Test fun disabled() = assertNull(WifiStatus.ssidFromStatus("Wifi is disabled\n"))

    @Test fun netstatsWifiNetworkKey() = assertEquals(
        "Home Wi-Fi",
        WifiStatus.ssidFromNetstats(
            "  ident=[{type=1, ratType=COMBINED, wifiNetworkKey=\"Home Wi-Fi\"WPA_PSK, metered=false}] iface=wlan0\n"
        ),
    )

    @Test fun netstatsNetworkId() = assertEquals(
        "Cafe",
        WifiStatus.ssidFromNetstats("ident=[{type=WIFI, subType=COMBINED, networkId=\"Cafe\"}] iface=wlan0"),
    )

    @Test fun netstatsEmpty() = assertNull(WifiStatus.ssidFromNetstats(""))

    @Test fun scanResults() = assertTrue(
        WifiStatus.hasScanResults(
            "    BSSID              Frequency      RSSI           Age(sec)     SSID                                 Flags\n" +
                "  aa:bb:cc:dd:ee:ff       5180     -61(0:-63/1:-64)     1.204    Home                              [WPA2-PSK-CCMP][ESS]\n"
        )
    )

    @Test fun noScanResults() = assertFalse(WifiStatus.hasScanResults("No scan results\n"))
}
