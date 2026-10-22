package io.github.sammeeeeeeee.onenet.panel

import io.github.sammeeeeeeee.onenet.net.MobileState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanelTextTest {

    private val labels = PanelLabels(
        wifiIsOff = "Wi-Fi is off",
        tapANetwork = "Tap a network to connect",
        searching = "Searching for networks…",
        noOtherNetworks = "No other networks available",
        noNetworks = "No networks available",
        mobileDataOff = "Mobile data won’t auto-connect",
        connectedFormat = "Connected / %1\$s",
        connected = "Connected",
        noConnection = "No connection",
    )
    private val mobile = MobileState(carrier = "Vodafone", networkType = "5G", dataEnabled = true)

    private fun subtitle(
        wifiEnabled: Boolean = true,
        hasNetworks: Boolean = false,
        searching: Boolean = false,
        mobile: MobileState? = this.mobile,
        mobileActive: Boolean = true,
    ) = PanelText.subtitle(wifiEnabled, hasNetworks, searching, mobile, mobileActive, labels)

    @Test fun subtitles() {
        assertEquals("Wi-Fi is off", subtitle(wifiEnabled = false, hasNetworks = true))
        assertEquals("Tap a network to connect", subtitle(hasNetworks = true, searching = true))
        assertEquals("Searching for networks…", subtitle(searching = true))
        assertEquals("No other networks available", subtitle())
        assertEquals("No other networks available", subtitle(mobile = mobile.copy(dataEnabled = false)))
        assertEquals("No networks available", subtitle(mobileActive = false))
        assertEquals("No networks available", subtitle(mobile = null))
        assertEquals("No networks available", subtitle(mobile = mobile.copy(inService = false)))
    }

    @Test fun mobileSummaries() {
        assertEquals("Connected / 5G", PanelText.mobileSummary(mobile, mobileActive = true, labels))
        assertEquals("5G", PanelText.mobileSummary(mobile, mobileActive = false, labels))
        assertEquals(
            "Mobile data won’t auto-connect",
            PanelText.mobileSummary(mobile.copy(dataEnabled = false), mobileActive = false, labels),
        )
        assertEquals(
            "No connection",
            PanelText.mobileSummary(mobile.copy(inService = false), mobileActive = false, labels),
        )
        assertNull(PanelText.mobileSummary(mobile.copy(networkType = null), mobileActive = false, labels))
    }
}
