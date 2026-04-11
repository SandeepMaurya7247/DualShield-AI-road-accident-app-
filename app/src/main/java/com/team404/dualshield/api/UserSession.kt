package com.team404.dualshield.api

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user session data using SharedPreferences.
 * Stores: userId, name, phone after login/register.
 */
object UserSession {
    private const val PREF_NAME = "dualshield_session"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NAME = "name"
    private const val KEY_PHONE = "phone"
    private const val KEY_LOGGED_IN = "logged_in"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, userId: String, name: String, phone: String) {
        prefs(context).edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_NAME, name)
            .putString(KEY_PHONE, phone)
            .putBoolean(KEY_LOGGED_IN, true)
            .apply()
    }

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOGGED_IN, false)

    fun getUserId(context: Context): String =
        prefs(context).getString(KEY_USER_ID, "unknown") ?: "unknown"

    fun getName(context: Context): String =
        prefs(context).getString(KEY_NAME, "User") ?: "User"

    fun getPhone(context: Context): String =
        prefs(context).getString(KEY_PHONE, "") ?: ""

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
