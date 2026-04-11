package com.team404.dualshield.emergency

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.team404.dualshield.api.BackendApi
import com.team404.dualshield.api.IncidentReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EmergencyManager(private val context: Context) {
    
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val api = BackendApi.create()

    suspend fun dispatchSOS(contacts: List<String>) {
        try {
            val location = getLastKnownLocation()
            
            // Report to Backend
            location?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Using a stable Android ID instead of Build.ID
                        val deviceId = Settings.Secure.getString(
                            context.contentResolver, 
                            Settings.Secure.ANDROID_ID
                        ) ?: "unknown_device"

                        api.reportIncident(
                            IncidentReport(
                                userId = deviceId,
                                latitude = it.latitude,
                                longitude = it.longitude,
                                severityLevel = 1,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                        Log.d("DualShield", "Incident reported with ID: $deviceId")
                    } catch (e: Exception) {
                        Log.e("DualShield", "Failed to report to backend: ${e.message}")
                    }
                }
            }

            val message = if (location != null) {
                "URGENT: I may have been in an accident! My location: http://maps.google.com/maps?q=${location.latitude},${location.longitude}"
            } else {
                "URGENT: I may have been in an accident! (Location unavailable)"
            }

            contacts.forEach { phone ->
                sendSms(phone, message)
            }
            
            Log.d("DualShield", "SOS Dispatched successfully.")
            
        } catch (e: SecurityException) {
            Log.e("DualShield", "Location permission missing.")
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? {
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("NewApi")
    private fun sendSms(phoneNumber: String, message: String) {
        try {
            val smsManager: SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            smsManager?.sendTextMessage(phoneNumber, null, message, null, null)
        } catch (e: Exception) {
            Log.e("DualShield", "SMS failed: ${e.message}")
        }
    }
}
