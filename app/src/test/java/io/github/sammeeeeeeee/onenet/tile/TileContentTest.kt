package io.github.sammeeeeeeee.onenet.tile

import io.github.sammeeeeeeee.onenet.net.DefaultNetwork
import io.github.sammeeeeeeee.onenet.net.MobileState
import io.github.sammeeeeeeee.onenet.net.NetworkState
import io.github.sammeeeeeeee.onenet.net.Transport
import org.junit.Assert.assertEquals
import org.junit.Test

class TileContentTest {

    private val labels = Labels(
        internet = "Internet", wifi = "Wi-Fi", mobileData = "Mobile data", ethernet = "Ethernet",
        connected = "Connected", noInternet = "No internet", noService = "No service",
        notConnected = "Not connected", off = "Off", airplaneMode = "Flight mode", roaming = "Roaming",
    )

    private val wifi = DefaultNetwork(handle = 1, transport = Transport.WIFI, validated = true, wifiLevel = 3)
    private val cell = DefaultNetwork(handle = 2, transport = Transport.CELLULAR, validated = true)
    private val vodafone = MobileState(carrier = "Vodafone", networkType = "5G", level = 2, dataEnabled = true)

    private fun content(state: NetworkState, nameAsTitle: Boolean = true) = tileContent(state, nameAsTitle, labels)

    @Test fun wifiNameFirst() = assertEquals(
        TileContent("Home", "Wi-Fi", TileIcon.Wifi(3), active = true),
        content(NetworkState(wifiEnabled = true, default = wifi, ssid = "Home", mobile = vodafone)),
    )

    @Test fun wifiPixelWording() = assertEquals(
        TileContent("Internet", "Home", TileIcon.Wifi(3), active = true),
        content(NetworkState(wifiEnabled = true, default = wifi, ssid = "Home"), nameAsTitle = false),
    )

    @Test fun wifiWithoutShizuku() = assertEquals(
        TileContent("Wi-Fi", "Connected", TileIcon.Wifi(3), active = true),
        content(NetworkState(wifiEnabled = true, default = wifi)),
    )

    @Test fun wifiNoInternet() = assertEquals(
        TileContent("Home", "No internet", TileIcon.Wifi(3, noInternet = true), active = true),
        content(NetworkState(wifiEnabled = true, default = wifi.copy(validated = false), ssid = "Home")),
    )

    @Test fun mobileNameFirst() = assertEquals(
        TileContent("Vodafone", "5G", TileIcon.Cellular(2), active = true),
        content(NetworkState(default = cell, mobile = vodafone)),
    )

    @Test fun mobilePixelWording() = assertEquals(
        TileContent("Internet", "Vodafone, 5G", TileIcon.Cellular(2), active = true),
        content(NetworkState(default = cell, mobile = vodafone), nameAsTitle = false),
    )

    @Test fun mobileRoaming() {
        val state = NetworkState(default = cell, mobile = vodafone.copy(roaming = true))
        assertEquals("5G · Roaming", content(state).subtitle)
        assertEquals("Vodafone, Roaming — 5G", content(state, nameAsTitle = false).subtitle)
    }

    @Test fun mobileWithoutDetails() = assertEquals(
        TileContent("Mobile data", "Mobile data", TileIcon.Cellular(0), active = true),
        content(NetworkState(default = cell, mobile = MobileState())),
    )

    @Test fun ethernet() = assertEquals(
        TileContent("Ethernet", "Connected", TileIcon.Globe, active = true),
        content(NetworkState(default = DefaultNetwork(3, Transport.ETHERNET, validated = true))),
    )

    @Test fun allOff() = assertEquals(
        TileContent("Internet", "Off", TileIcon.Wifi(4), active = false),
        content(NetworkState(mobile = vodafone.copy(dataEnabled = false))),
    )

    @Test fun flightMode() = assertEquals(
        TileContent("Internet", "Flight mode", TileIcon.Airplane, active = false),
        content(NetworkState(airplaneMode = true, mobile = vodafone)),
    )

    @Test fun wifiOnButNotConnected() = assertEquals(
        TileContent("Internet", "Not connected", TileIcon.Wifi(0), active = false),
        content(NetworkState(wifiEnabled = true, mobile = vodafone.copy(dataEnabled = false))),
    )

    @Test fun mobileDataOnWithoutSignal() = assertEquals(
        TileContent("Internet", "No service", TileIcon.Cellular(0), active = false),
        content(NetworkState(mobile = vodafone.copy(inService = false))),
    )
}
