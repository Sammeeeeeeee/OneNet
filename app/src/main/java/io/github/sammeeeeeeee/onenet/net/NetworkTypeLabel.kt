package io.github.sammeeeeeeee.onenet.net

import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager

/** Status-bar style label for a mobile connection, e.g. "5G" or "4G+". */
object NetworkTypeLabel {

    /** Countries whose carriers brand LTE as "LTE" rather than "4G". */
    private val LTE_COUNTRIES = setOf("us", "ca", "kr")

    fun lteAs4g(simCountryIso: String?) = simCountryIso?.lowercase() !in LTE_COUNTRIES

    fun of(networkType: Int, overrideType: Int, lteAs4g: Boolean): String? {
        val lte = if (lteAs4g) "4G" else "LTE"
        return when (overrideType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G+"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "$lte+"
            else -> when (networkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> lte
                TelephonyManager.NETWORK_TYPE_HSPAP -> "H+"
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA -> "H"
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE -> "E"
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_GSM -> "G"
                else -> null
            }
        }
    }
}
