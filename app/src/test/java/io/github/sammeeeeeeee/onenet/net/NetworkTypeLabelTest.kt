package io.github.sammeeeeeeee.onenet.net

import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkTypeLabelTest {

    private val none = TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE

    @Test fun nsa5g() = assertEquals(
        "5G",
        NetworkTypeLabel.of(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA, true),
    )

    @Test fun sa5g() = assertEquals("5G", NetworkTypeLabel.of(TelephonyManager.NETWORK_TYPE_NR, none, true))

    @Test fun advanced5g() = assertEquals(
        "5G+",
        NetworkTypeLabel.of(TelephonyManager.NETWORK_TYPE_NR, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED, true),
    )

    @Test fun lteBranding() {
        assertEquals("4G", NetworkTypeLabel.of(TelephonyManager.NETWORK_TYPE_LTE, none, lteAs4g = true))
        assertEquals("LTE", NetworkTypeLabel.of(TelephonyManager.NETWORK_TYPE_LTE, none, lteAs4g = false))
    }

    @Test fun carrierAggregation() = assertEquals(
        "4G+",
        NetworkTypeLabel.of(TelephonyManager.NETWORK_TYPE_LTE, TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA, true),
    )

    @Test fun legacy() {
        assertEquals("H+", NetworkTypeLabel.of(TelephonyManager.NETWORK_TYPE_HSPAP, none, true))
        assertEquals("3G", NetworkTypeLabel.of(TelephonyManager.NETWORK_TYPE_UMTS, none, true))
        assertEquals("E", NetworkTypeLabel.of(TelephonyManager.NETWORK_TYPE_EDGE, none, true))
    }

    @Test fun unknown() = assertNull(NetworkTypeLabel.of(TelephonyManager.NETWORK_TYPE_UNKNOWN, none, true))

    @Test fun lteCountries() {
        assertTrue(NetworkTypeLabel.lteAs4g("gb"))
        assertTrue(NetworkTypeLabel.lteAs4g(null))
        assertFalse(NetworkTypeLabel.lteAs4g("US"))
    }
}
