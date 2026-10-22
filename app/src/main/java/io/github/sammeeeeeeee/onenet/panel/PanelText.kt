package io.github.sammeeeeeeee.onenet.panel

import io.github.sammeeeeeeee.onenet.net.MobileState

data class PanelLabels(
    val wifiIsOff: String,
    val tapANetwork: String,
    val searching: String,
    val noOtherNetworks: String,
    val noNetworks: String,
    val mobileDataOff: String,
    /** "Connected / %1$s" */
    val connectedFormat: String,
    val connected: String,
    val noConnection: String,
)

/** Text rules of Pixel's internet panel (AOSP InternetDetailsContentController, Android 17). */
object PanelText {

    fun subtitle(
        wifiEnabled: Boolean,
        hasNetworks: Boolean,
        searching: Boolean,
        mobile: MobileState?,
        mobileActive: Boolean,
        labels: PanelLabels,
    ): String = when {
        !wifiEnabled -> labels.wifiIsOff
        hasNetworks -> labels.tapANetwork
        searching -> labels.searching
        mobile == null || !mobile.inService -> labels.noNetworks
        !mobile.dataEnabled -> labels.noOtherNetworks
        !mobileActive -> labels.noNetworks
        else -> labels.noOtherNetworks
    }

    fun mobileSummary(mobile: MobileState, mobileActive: Boolean, labels: PanelLabels): String? = when {
        !mobile.dataEnabled -> labels.mobileDataOff
        mobileActive -> mobile.networkType?.let { labels.connectedFormat.format(it) } ?: labels.connected
        !mobile.inService -> labels.noConnection
        else -> mobile.networkType
    }

    /** Other networks listed: four rows in total, at most three Wi-Fi (getWifiListMaxCount). */
    fun networkRows(mobileRowVisible: Boolean, connectedVisible: Boolean): Int {
        val rows = minOf(if (mobileRowVisible) 3 else 4, 3)
        return if (connectedVisible) rows - 1 else rows
    }
}
