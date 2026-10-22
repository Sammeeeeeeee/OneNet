package io.github.sammeeeeeeee.onenet

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object Prefs {

    private const val FILE = "settings"
    private const val KEY_NAME_AS_TITLE = "network_name_as_title"
    private const val KEY_SSID_HANDLE = "ssid_handle"
    private const val KEY_SSID = "ssid"
    private const val KEY_TURNED_OFF_MOBILE_DATA = "turned_off_mobile_data"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** False (default): Pixel layout, "Internet" with the name below. True: name on top. */
    fun nameAsTitle(context: Context) = prefs(context).getBoolean(KEY_NAME_AS_TITLE, false)

    fun setNameAsTitle(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_NAME_AS_TITLE, value).apply()

    fun nameAsTitleFlow(context: Context): Flow<Boolean> = callbackFlow {
        val prefs = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_NAME_AS_TITLE) trySend(nameAsTitle(context))
        }
        trySend(nameAsTitle(context))
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    /** Last Wi-Fi name, so a cold start doesn't flash "Wi-Fi" before Shizuku answers. */
    fun cachedSsid(context: Context, handle: Long): String? = prefs(context).run {
        if (getLong(KEY_SSID_HANDLE, 0L) == handle) getString(KEY_SSID, null) else null
    }

    fun cacheSsid(context: Context, handle: Long, ssid: String) =
        prefs(context).edit().putLong(KEY_SSID_HANDLE, handle).putString(KEY_SSID, ssid).apply()

    /** Pixel asks before turning off mobile data from the panel, the first time only. */
    fun hasTurnedOffMobileData(context: Context) = prefs(context).getBoolean(KEY_TURNED_OFF_MOBILE_DATA, false)

    fun setTurnedOffMobileData(context: Context) =
        prefs(context).edit().putBoolean(KEY_TURNED_OFF_MOBILE_DATA, true).apply()
}
