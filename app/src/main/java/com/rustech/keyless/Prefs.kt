package com.rustech.keyless

import android.content.Context
import java.util.UUID

/** Simple SharedPreferences wrapper for the beacon identity this phone broadcasts. */
object Prefs {
    private const val PREFS_NAME = "rustech_keyless_prefs"
    private const val KEY_UUID = "beacon_uuid"
    private const val KEY_MAJOR = "beacon_major"
    private const val KEY_MINOR = "beacon_minor"
    private const val KEY_LABEL = "device_label"

    // Default UUID namespace for Rustech Key Less devices.
    // Tip: keep this UUID the SAME across all authorized phones, and only change
    // Major/Minor per phone -- that's exactly how the ESP32 side can tell
    // multiple authorized devices apart while still recognizing the family.
    private const val DEFAULT_UUID = "8ec76ea3-6668-48da-9866-75be8bc86f4d"

    fun getUuid(context: Context): String =
        prefs(context).getString(KEY_UUID, DEFAULT_UUID) ?: DEFAULT_UUID

    fun setUuid(context: Context, uuid: String) {
        prefs(context).edit().putString(KEY_UUID, uuid).apply()
    }

    fun getMajor(context: Context): Int = prefs(context).getInt(KEY_MAJOR, 1)

    fun setMajor(context: Context, major: Int) {
        prefs(context).edit().putInt(KEY_MAJOR, major).apply()
    }

    fun getMinor(context: Context): Int = prefs(context).getInt(KEY_MINOR, 1)

    fun setMinor(context: Context, minor: Int) {
        prefs(context).edit().putInt(KEY_MINOR, minor).apply()
    }

    fun getLabel(context: Context): String =
        prefs(context).getString(KEY_LABEL, "Rustech Phone") ?: "Rustech Phone"

    fun setLabel(context: Context, label: String) {
        prefs(context).edit().putString(KEY_LABEL, label).apply()
    }

    fun randomUuid(): String = UUID.randomUUID().toString()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
