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
    private const val KEY_THEME_MODE = "theme_mode" // SYSTEM, LIGHT, DARK
    private const val KEY_EMERGENCY_ALERTS = "emergency_alerts"
    private const val KEY_BACKEND_SYNC = "backend_sync"
    private const val KEY_DATA_PRIVACY = "data_privacy"
    private const val KEY_INCIDENTS = "local_incidents_json"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, userId: String, name: String, phone: String, initialEmergencyName: String? = null, initialEmergencyPhone: String? = null) {
        val editor = prefs(context).edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_NAME, name)
            .putString(KEY_PHONE, phone)
            .putBoolean(KEY_LOGGED_IN, true)

        if (!initialEmergencyName.isNullOrBlank() && !initialEmergencyPhone.isNullOrBlank()) {
            val contact = ContactItem(
                contact_name = initialEmergencyName,
                contact_phone = initialEmergencyPhone,
                relation = "Emergency"
            )
            val json = com.google.gson.Gson().toJson(listOf(contact))
            editor.putString(KEY_CONTACTS, json)
        }

        editor.apply()
    }

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOGGED_IN, false)

    fun getUserId(context: Context): String =
        prefs(context).getString(KEY_USER_ID, "unknown") ?: "unknown"

    fun getName(context: Context): String =
        prefs(context).getString(KEY_NAME, "User") ?: "User"

    fun getPhone(context: Context): String =
        prefs(context).getString(KEY_PHONE, "") ?: ""

    private const val KEY_CONTACTS = "contacts_json"

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun saveContactsLocal(context: Context, json: String) {
        prefs(context).edit().putString(KEY_CONTACTS, json).apply()
    }

    fun getContactsLocal(context: Context): String {
        return prefs(context).getString(KEY_CONTACTS, "[]") ?: "[]"
    }

    fun getContactsList(context: Context): List<ContactItem> {
        val json = getContactsLocal(context)
        return try {
            val listType = object : com.google.gson.reflect.TypeToken<List<ContactItem>>() {}.type
            com.google.gson.Gson().fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveThemeMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun updateName(context: Context, newName: String) {
        prefs(context).edit().putString(KEY_NAME, newName).apply()
    }

    fun updateProfile(context: Context, newName: String, emergencyName: String, emergencyPhone: String) {
        val editor = prefs(context).edit()
            .putString(KEY_NAME, newName)
        
        if (emergencyName.isNotBlank() && emergencyPhone.isNotBlank()) {
            val contact = ContactItem(
                contact_name = emergencyName,
                contact_phone = emergencyPhone,
                relation = "Emergency"
            )
            // Get current contacts and update/replace the first one
            val currentList = getContactsList(context).toMutableList()
            if (currentList.isNotEmpty()) {
                currentList[0] = contact
            } else {
                currentList.add(contact)
            }
            val json = com.google.gson.Gson().toJson(currentList)
            editor.putString(KEY_CONTACTS, json)
        }
        editor.apply()
    }

    fun getThemeMode(context: Context): String {
        return prefs(context).getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
    }

    // --- Persistence for Preferences ---
    fun isEmergencyAlertsEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_EMERGENCY_ALERTS, true)
    fun setEmergencyAlerts(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_EMERGENCY_ALERTS, enabled).apply()

    fun isBackendSyncEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_BACKEND_SYNC, true)
    fun setBackendSync(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_BACKEND_SYNC, enabled).apply()

    fun isDataPrivacyEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_DATA_PRIVACY, true)
    fun setDataPrivacy(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_DATA_PRIVACY, enabled).apply()

    // --- Local Incident History ---
    fun saveIncidentLocal(context: Context, item: IncidentItem) {
        val current = getLocalIncidents(context).toMutableList()
        current.add(0, item) // Add to top
        val limited = current.take(50) // Keep last 50 only
        val json = com.google.gson.Gson().toJson(limited)
        prefs(context).edit().putString(KEY_INCIDENTS, json).apply()
    }

    fun getLocalIncidents(context: Context): List<IncidentItem> {
        val json = prefs(context).getString(KEY_INCIDENTS, "[]") ?: "[]"
        return try {
            val listType = object : com.google.gson.reflect.TypeToken<List<IncidentItem>>() {}.type
            com.google.gson.Gson().fromJson(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
