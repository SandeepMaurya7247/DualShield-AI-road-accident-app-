package com.team404.dualshield.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.team404.dualshield.MainActivity
import com.team404.dualshield.ai.AccidentDetector
import com.team404.dualshield.api.BackendApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SensorMonitoringService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    
    // ML Streaming state
    private var currentGyroX = 0f
    private var currentGyroY = 0f
    private var currentGyroZ = 0f
    
    // We mock speed to 45kmh here unless LocationService provides it, to keep the model fed.
    private var currentSpeedKmh = 45f
    
    private lateinit var accidentDetector: AccidentDetector
    private val api = BackendApi.create()
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    // Prevent multiple SOS triggers firing in rapid succession
    private var sosActive = false

    private val cancelReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.team404.dualshield.ACTION_SOS_CANCELLED") {
                Log.d("DualShield", "SOS Cancelled Broadcast received. Resetting state.")
                sosActive = false
                accidentDetector.resetCooldown()
                com.team404.dualshield.emergency.EmergencyManager.resetSosStatus()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        // Initialize Sensors
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        // Register Listeners
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }

        accidentDetector = AccidentDetector(this)
        
        // Register for cancel events
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).registerReceiver(
            cancelReceiver, android.content.IntentFilter("com.team404.dualshield.ACTION_SOS_CANCELLED")
        )

        // Android 14+ requires specific foreground types
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    1, 
                    createNotification(), 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(1, createNotification())
            }
        } catch (e: Exception) {
            Log.e("DualShield", "Foreground start failed: ${e.message}")
            startForeground(1, createNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun createNotification(): Notification {
        val channelId = "DualShieldServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Dual Shield Active", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Dual Shield is Protecting You")
            .setContentText("Monitoring for accidents in real-time...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                currentGyroX = event.values[0]
                currentGyroY = event.values[1]
                currentGyroZ = event.values[2]
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val accX = event.values[0]
                val accY = event.values[1]
                val accZ = event.values[2]

                // Stream into the TFLite pipeline
                val isCrash = accidentDetector.processSensorData(
                    accX, accY, accZ,
                    currentGyroX, currentGyroY, currentGyroZ,
                    currentSpeedKmh
                )

                if (isCrash && !sosActive) {
                    sosActive = true
                    triggerEmergencyProtocol()
                    // Reset flag after 45 seconds as a safety backup
                    serviceScope.launch {
                        kotlinx.coroutines.delay(45_000L)
                        sosActive = false
                    }
                }
            }
        }
    }

    private fun triggerEmergencyProtocol() {
        Log.w("DualShield", "🚨 CRASH DETECTED! Triggering Protocol")

        // 1. Create intent for the Emergency Activity
        val emergencyIntent = Intent(this, com.team404.dualshield.emergency.EmergencyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or 
                     Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                     Intent.FLAG_ACTIVITY_SINGLE_TOP or
                     Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) // Aggressive flags
        }

        // 2. Launch via Notification fullScreenIntent → Handles LOCKED screen
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 999, emergencyIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // 3. Attempt to start directly (Works if app is in foreground or has SYSTEM_ALERT_WINDOW permission)
        try {
            startActivity(emergencyIntent)
        } catch (e: Exception) {
            Log.e("DualShield", "Failed to start activity directly: ${e.message}")
        }

        // 4. Alternative aggressive launch via PendingIntent.send() 
        // Some OEMs react better to this for background activity starts
        try {
            pendingIntent.send()
        } catch (e: Exception) {
            Log.e("DualShield", "Failed to send pending intent: ${e.message}")
        }

        val channelId = "EmergencyAlertChannel"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(channelId, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                manager.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e("DualShield", "Failed to create NotificationChannel: ${e.message}")
            }
        }

        try {
            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🚨 Crash Detected!")
                .setContentText("Emergency protocol started. Tap to view.")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(true)
                .build()

            manager.notify(999, notification)
        } catch (e: Exception) {
            Log.e("DualShield", "Failed to show notification: ${e.message}")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).unregisterReceiver(cancelReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
