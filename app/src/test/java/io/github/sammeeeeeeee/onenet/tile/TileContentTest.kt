package io.github.sammeeeeeeee.onenet.tile

import io.github.sammeeeeeeee.onenet.net.DefaultNetwork
import io.github.sammeeeeeeee.onenet.net.MobileState
import io.github.sammeeeeeeee.onenet.net.NetworkState
import io.github.sammeeeeeeee.onenet.net.Transport
import io.github.sammeeeeeeee.onenet.net.WifiToggle
import org.junit.Assert.assertEquals
import org.junit.Test

class TileContentTest {

    private val labels = Labels(
        internet = "Internet", wifi = "Wi-Fi", mobileData = "Mobile data", ethernet = "Ethernet",
        connected = "Connected", noInternet = "No internet", notConnected = "Not connected",
        networksAvailable = "Networks available", networksUnavailable = "Networks unavailable",
        airplaneMode = "Flight mode", roaming = "Roaming", scanningForWifi = "Scanning for Wi-Fi…",
    )

    private val wifi = DefaultNetwork(handle = 1, transport = Transport.WIFI, validated = true, wifiLevel = 3)
    private val cell = DefaultNetwork(handle = 2, transport = Transport.CELLULAR, validated = true)
    private val vodafone = MobileState(carrier = "Vodafone", networkType = "5G", level = 2, dataEnabled = true)

    private fun content(state: NetworkState, nameAsTitle: Boolean = false) = tileContent(state, nameAsTitle, labels)

    // Pixel layout (default)

    @Test fun wifi() = assertEquals(
        TileContent("Internet", "Home", TileIcon.Wifi(3), active = true),
        content(NetworkState(wifiEnabled = true, default = wifi, ssid = "Home", mobile = vodafone)),
    )

    @Test fun wifiNoInternetKeepsNameAndLevel() = assertEquals(
        TileContent("Internet", "Home", TileIcon.Wifi(3, noInternet = true), active = true),
        content(NetworkState(wifiEnabled = true, default = wifi.copy(validated = false), ssid = "Home")),
    )

    @Test fun wifiWithoutShizuku() = assertEquals(
        TileContent("Internet", "Wi-Fi", TileIcon.Wifi(3), active = true),
        content(NetworkState(wifiEnabled = true, default = wifi)),
    )

    @Test fun mobile() = assertEquals(
        TileContent("Internet", "Vodafone, 5G", TileIcon.Cellular(2), active = true),
        content(NetworkState(default = cell, mobile = vodafone)),
    )

    @Test fun mobileRoaming() = assertEquals(
        "Vodafone, Roaming — 5G",
        content(NetworkState(default = cell, mobile = vodafone.copy(roaming = true))).subtitle,
    )

    @Test fun mobileWithoutDetails() = assertEquals(
        TileContent("Internet", "Mobile data", TileIcon.Cellular(0), active = true),
        content(NetworkState(default = cell, mobile = MobileState())),
    )

    @Test fun ethernet() = assertEquals(
        TileContent("Internet", "Ethernet", TileIcon.Ethernet, active = true),
        content(NetworkState(default = DefaultNetwork(3, Transport.ETHERNET, validated = true))),
    )

    @Test fun flightModeWinsWhileDisconnected() = assertEquals(
        TileContent("Internet", "Flight mode", TileIcon.NoInternet(false), active = false),
        content(NetworkState(airplaneMode = true, wifiEnabled = true, networksAvailable = true)),
    )

    @Test fun networksAvailable() = assertEquals(
        TileContent("Internet", "Networks available", TileIcon.NoInternet(true), active = false),
        content(NetworkState(wifiEnabled = true, networksAvailable = true)),
    )

    @Test fun networksUnavailable() = assertEquals(
        TileContent("Internet", "Networks unavailable", TileIcon.NoInternet(false), active = false),
        content(NetworkState(wifiEnabled = true, networksAvailable = false)),
    )

    @Test fun everythingOffIsUnavailable() = assertEquals(
        TileContent("Internet", "Networks unavailable", TileIcon.NoInternet(false), active = false),
        content(NetworkState(networksAvailable = false, mobile = vodafone.copy(dataEnabled = false))),
    )

    @Test fun scanListUnknown() = assertEquals(
        TileContent("Internet", "Not connected", TileIcon.NoInternet(true), active = false),
        content(NetworkState(wifiEnabled = true)),
    )

    // Name on top

    @Test fun wifiNameOnTop() = assertEquals(
        TileContent("Home", "Wi-Fi", TileIcon.Wifi(3), active = true),
        content(NetworkState(wifiEnabled = true, default = wifi, ssid = "Home"), nameAsTitle = true),
    )

    @Test fun wifiNameOnTopNoInternet() = assertEquals(
        "No internet",
        content(NetworkState(default = wifi.copy(validated = false), ssid = "Home"), nameAsTitle = true).subtitle,
    )

    @Test fun mobileNameOnTop() {
        val state = NetworkState(default = cell, mobile = vodafone.copy(roaming = true))
        assertEquals(
            TileContent("Vodafone", "5G · Roaming", TileIcon.Cellular(2), active = true),
            content(state, nameAsTitle = true),
        )
    }

    // Tap toggle (Pixel's Wi-Fi tile)

    @Test fun resumingShowsScanning() = assertEquals(
        TileContent("Internet", "Scanning for Wi-Fi…", TileIcon.Wifi(0), active = true),
        content(NetworkState(default = cell, mobile = vodafone, toggle = WifiToggle.State.SCANNING)),
    )

    @Test fun pausingShowsMobileStraightAway() = assertEquals(
        TileContent("Internet", "Vodafone, 5G", TileIcon.Cellular(2), active = true),
        content(NetworkState(default = wifi, ssid = "Home", mobile = vodafone, toggle = WifiToggle.State.PAUSING)),
    )

    @Test fun pausingWithoutMobileData() = assertEquals(
        "Networks available",
        content(
            NetworkState(
                wifiEnabled = true, default = wifi, networksAvailable = true,
                mobile = vodafone.copy(dataEnabled = false), toggle = WifiToggle.State.PAUSING,
            )
        ).subtitle,
    )
}
