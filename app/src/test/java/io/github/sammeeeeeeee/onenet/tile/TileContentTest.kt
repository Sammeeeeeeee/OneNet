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
        airplaneMode = "Flight mode", roaming = "Roaming", searching = "Searching…", connecting = "Connecting…",
        off = "Off",
    )

    private val wifi = DefaultNetwork(handle = 1, transport = Transport.WIFI, validated = true, wifiLevel = 3)
    private val cell = DefaultNetwork(handle = 2, transport = Transport.CELLULAR, validated = true)
    private val vodafone = MobileState(carrier = "Vodafone", networkType = "5G", level = 2, dataEnabled = true)

    private fun content(state: NetworkState, nameAsTitle: Boolean = false) = tileContent(state, nameAsTitle, labels)

    // One UI layout (default): the name of One UI's own tile, then the details

    @Test fun wifi() = assertEquals(
        TileContent("Wi-Fi", "Home", TileIcon.Wifi(3), active = true),
        content(NetworkState(wifiEnabled = true, default = wifi, ssid = "Home", mobile = vodafone)),
    )

    @Test fun wifiNoInternetKeepsNameAndLevel() = assertEquals(
        TileContent("Wi-Fi", "Home", TileIcon.Wifi(3, noInternet = true), active = true),
        content(NetworkState(wifiEnabled = true, default = wifi.copy(validated = false), ssid = "Home")),
    )

    @Test fun wifiWithoutShizuku() = assertEquals(
        TileContent("Wi-Fi", "Connected", TileIcon.Wifi(3), active = true),
        content(NetworkState(wifiEnabled = true, default = wifi)),
    )

    @Test fun mobile() = assertEquals(
        TileContent("Mobile data", "Vodafone, 5G", TileIcon.MobileData(), active = true),
        content(NetworkState(default = cell, mobile = vodafone)),
    )

    @Test fun mobileRoaming() = assertEquals(
        "Vodafone, Roaming — 5G",
        content(NetworkState(default = cell, mobile = vodafone.copy(roaming = true))).subtitle,
    )

    @Test fun mobileWithoutDetails() = assertEquals(
        TileContent("Mobile data", "Connected", TileIcon.MobileData(), active = true),
        content(NetworkState(default = cell, mobile = MobileState())),
    )

    @Test fun ethernet() = assertEquals(
        TileContent("Ethernet", "Connected", TileIcon.Ethernet, active = true),
        content(NetworkState(default = DefaultNetwork(3, Transport.ETHERNET, validated = true))),
    )

    @Test fun flightModeWinsWhileDisconnected() = assertEquals(
        TileContent("Wi-Fi", "Flight mode", TileIcon.NoInternet(false), active = false),
        content(NetworkState(airplaneMode = true, wifiEnabled = true, networksAvailable = true)),
    )

    @Test fun networksAvailable() = assertEquals(
        TileContent("Wi-Fi", "Networks available", TileIcon.NoInternet(true), active = false),
        content(NetworkState(wifiEnabled = true, networksAvailable = true)),
    )

    @Test fun networksUnavailable() = assertEquals(
        TileContent("Wi-Fi", "Networks unavailable", TileIcon.NoInternet(false), active = false),
        content(NetworkState(wifiEnabled = true, networksAvailable = false)),
    )

    @Test fun everythingOff() = assertEquals(
        TileContent("Wi-Fi", "Off", TileIcon.NoInternet(false), active = false),
        content(NetworkState(networksAvailable = false, mobile = vodafone.copy(dataEnabled = false))),
    )

    @Test fun scanListUnknown() = assertEquals(
        TileContent("Wi-Fi", "Not connected", TileIcon.NoInternet(true), active = false),
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
            TileContent("Vodafone", "5G · Roaming", TileIcon.MobileData(), active = true),
            content(state, nameAsTitle = true),
        )
    }

    // Tap toggle (Pixel's Wi-Fi tile)

    @Test fun resumingSearches() = assertEquals(
        TileContent("Wi-Fi", "Searching…", TileIcon.WifiSearching, active = true),
        content(NetworkState(default = cell, mobile = vodafone, toggle = WifiToggle.State.SCANNING)),
    )

    // Joined but not yet carrying traffic: name it instead of flicking back to mobile.
    @Test fun resumingNamesTheNetworkWhileConnecting() = assertEquals(
        TileContent("Wi-Fi", "Home", TileIcon.WifiSearching, active = true),
        content(
            NetworkState(
                default = cell, mobile = vodafone, ssid = "Home", wifiConnected = true,
                toggle = WifiToggle.State.SCANNING,
            )
        ),
    )

    // Associated (the status bar shows Wi-Fi) before Android has the network: not "Searching…".
    @Test fun resumingNamesTheNetworkOnceAssociated() = assertEquals(
        TileContent("Wi-Fi", "Home", TileIcon.WifiSearching, active = true),
        content(NetworkState(default = cell, mobile = vodafone, joining = "Home", toggle = WifiToggle.State.SCANNING)),
    )

    @Test fun resumingAssociatedNameOnTop() = assertEquals(
        TileContent("Home", "Connecting…", TileIcon.WifiSearching, active = true),
        content(NetworkState(default = cell, joining = "Home", toggle = WifiToggle.State.SCANNING), nameAsTitle = true),
    )

    @Test fun resumingNameOnTop() = assertEquals(
        TileContent("Home", "Connecting…", TileIcon.WifiSearching, active = true),
        content(
            NetworkState(default = cell, ssid = "Home", wifiConnected = true, toggle = WifiToggle.State.SCANNING),
            nameAsTitle = true,
        ),
    )

    @Test fun scanningEndsOnceWifiCarriesTraffic() = assertEquals(
        TileContent("Wi-Fi", "Home", TileIcon.Wifi(3), active = true),
        content(NetworkState(default = wifi, ssid = "Home", toggle = WifiToggle.State.SCANNING)),
    )

    @Test fun pausingShowsMobileStraightAway() = assertEquals(
        TileContent("Mobile data", "Vodafone, 5G", TileIcon.Handover(TileIcon.MobileData()), active = true),
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
