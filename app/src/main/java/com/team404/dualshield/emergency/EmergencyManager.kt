package com.team404.dualshield.emergency

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
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
    
    companion object {
        @Volatile
        private var isSosInProgress = false
        
        fun resetSosStatus() {
            isSosInProgress = false
            Log.d("EmergencyManager", "SOS Progress lock reset.")
        }
    }
    
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val api = BackendApi.create()

    suspend fun dispatchSOS(
        contactPhones: List<String>, 
        userId: String,
        ax: Float = 0f,
        ay: Float = 0f,
        az: Float = 0f,
        gx: Float = 0f,
        gy: Float = 0f,
        gz: Float = 0f
    ) {
        if (!com.team404.dualshield.api.UserSession.isEmergencyAlertsEnabled(context)) {
            Log.d("EmergencyManager", "SOS Dispatch aborted: Emergency Alerts are DISABLED in settings.")
            return
        }

        if (isSosInProgress) {
            Log.w("EmergencyManager", "SOS Dispatch already in progress. Skipping duplicate.")
            return
        }
        isSosInProgress = true
        
        // Deduplicate phone numbers to prevent "bar bar" messages to same person
        val uniquePhones = contactPhones.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        
        Log.d("EmergencyManager", "Dispatching SOS to ${uniquePhones.size} unique contacts")
        try {
            val location = getLastKnownLocation()
            val (lat, lng) = if (location != null) Pair(location.latitude, location.longitude) else Pair(28.6139, 77.2090)
            
            // 1. Report to Backend (Mandatory for History, ignores Sync setting)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val reportUserId = if (userId.isNullOrBlank() || userId == "unknown") {
                        com.team404.dualshield.api.UserSession.getPhone(context)
                    } else {
                        userId
                    }
                    
                    Log.d("EmergencyManager", "Reporting incident for user: $reportUserId")
                    val response = api.reportIncident(
                        IncidentReport(
                            userId = reportUserId,
                            latitude = lat,
                            longitude = lng,
                            severityLevel = 3,
                            accelX = ax,
                            accelY = ay,
                            accelZ = az,
                            gyroX = gx,
                            gyroY = gy,
                            gyroZ = gz,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    if (response.isSuccessful) {
                        Log.d("EmergencyManager", "Backend report SUCCESS: ${response.body()?.incident_id}")
                    } else {
                        Log.e("EmergencyManager", "Backend report FAILED: ${response.errorBody()?.string()}")
                    }
                } catch (e: Exception) {
                    Log.e("EmergencyManager", "Backend report exception: ${e.message}")
                }
            }

            // 2. Send SMS
            val message = "🚨 EMERGENCY! DualShield AI detected a crash. Location: https://www.google.com/maps?q=$lat,$lng"
            uniquePhones.forEach { phone ->
                sendSms(phone, message)
            }
            
            // 3. Initiate Call to PRIMARY contact only (no police fallback)
            val primaryPhone = uniquePhones.firstOrNull()
            if (primaryPhone != null) {
                makeCall(primaryPhone)
            } else {
                Log.w("EmergencyManager", "No primary contact found. Skipping call.")
            }
            
            Log.d("EmergencyManager", "SOS Dispatch completed.")
            
        } catch (e: Exception) {
            Log.e("EmergencyManager", "Error in dispatchSOS: ${e.message}")
        } finally {
            // Allow re-triggering after a cooldown (60 seconds)
            CoroutineScope(Dispatchers.Main).launch {
                kotlinx.coroutines.delay(60_000L)
                resetSosStatus()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? {
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            Log.e("EmergencyManager", "Location retrieval failed: ${e.message}")
            null
        }
    }

    private fun sendSms(phoneNumber: String, message: String) {
        try {
            Log.d("EmergencyManager", "Sending SMS to $phoneNumber")
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            Log.d("EmergencyManager", "SMS sent successfully to $phoneNumber")
        } catch (e: Exception) {
            Log.e("EmergencyManager", "SMS failed to $phoneNumber: ${e.message}")
        }
    }

    fun makeCall(phoneNumber: String) {
        try {
            Log.d("EmergencyManager", "Initiating CALL to $phoneNumber")
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
            Log.d("EmergencyManager", "Call intent started")
        } catch (e: Exception) {
            Log.e("EmergencyManager", "Call failed: ${e.message}")
            // Fallback to Dial intent if CALL_PHONE fails for some reason
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            } catch (e2: Exception) {
                Log.e("EmergencyManager", "Dial fallback also failed: ${e2.message}")
            }
        }
    }
}
