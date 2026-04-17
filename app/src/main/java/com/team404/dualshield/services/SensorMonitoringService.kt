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
import com.google.android.gms.location.*
import com.team404.dualshield.ai.SpeechAssistantManager
import com.team404.dualshield.api.Zone
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.location.Location
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.os.Looper

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
    
    // Risk Zone Monitoring
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var riskZones = listOf<Zone>()
    private var speechAssistant: SpeechAssistantManager? = null
    private var lastAlertTime = 0L
    private var isCurrentlyInRiskZone = false
    private var lastBroadcastedStatus = false

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

        // Initialize Risk Zone Monitoring
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        loadRiskZones()
        setupLocationMonitoring()
        
        speechAssistant = SpeechAssistantManager(this, onReady = {}, onCommandDetected = {})

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
                    triggerEmergencyProtocol(accX, accY, accZ, currentGyroX, currentGyroY, currentGyroZ)
                    // Reset flag after 45 seconds as a safety backup
                    serviceScope.launch {
                        kotlinx.coroutines.delay(45_000L)
                        sosActive = false
                    }
                }
            }
        }
    }

    private fun triggerEmergencyProtocol(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) {
        Log.w("DualShield", "🚨 CRASH DETECTED! Telemetry: Accel($ax, $ay, $az) Gyro($gx, $gy, $gz)")

        // 1. Create intent for the Emergency Activity
        val emergencyIntent = Intent(this, com.team404.dualshield.emergency.EmergencyActivity::class.java).apply {
            putExtra("accel_x", ax)
            putExtra("accel_y", ay)
            putExtra("accel_z", az)
            putExtra("gyro_x", gx)
            putExtra("gyro_y", gy)
            putExtra("gyro_z", gz)
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



    private fun loadRiskZones() {
        try {
            val jsonString = assets.open("risk_zones.json").bufferedReader().use { it.readText() }
            val listType = object : TypeToken<List<Zone>>() {}.type
            riskZones = Gson().fromJson(jsonString, listType) ?: emptyList()
            Log.d("DualShield", "Loaded ${riskZones.size} risk zones in Service")
        } catch (e: Exception) {
            Log.e("DualShield", "Error loading risk zones in Service: ${e.message}")
        }
    }

    private fun setupLocationMonitoring() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                currentSpeedKmh = (location.speed * 3.6).toInt().coerceAtLeast(0).toFloat()
                checkRiskZones(location)
            }
        }
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
        try {
            fusedLocationClient.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("DualShield", "Location permission missing for Service")
        }
    }

    private fun checkRiskZones(location: Location) {
        var inAnyZone = false
        for (zone in riskZones) {
            val dest = Location("").apply {
                latitude = zone.lat
                longitude = zone.lng
            }
            val distance = location.distanceTo(dest)
            
            if (distance <= zone.radius) {
                inAnyZone = true
                val speed = location.speed * 3.6
                if (speed > 20 && speed > 10) {
                    triggerVoiceWarning()
                }
                break // Stop at first matched zone
            }
        }
        
        if (inAnyZone != lastBroadcastedStatus) {
            lastBroadcastedStatus = inAnyZone
            broadcastRiskStatus(inAnyZone)
        }
    }

    private fun triggerVoiceWarning() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAlertTime > 15000L) { // 15 second cooldown for background
            Log.w("DualShield", "🔊 TRIGGERING VOICE WARNING: Dheere chaliye...")
            speechAssistant?.speakText("Dheere chaliye, high risk zone hai")
            lastAlertTime = currentTime
        }
    }

    private fun broadcastRiskStatus(isHighRisk: Boolean) {
        val intent = Intent("com.team404.dualshield.RISK_STATUS_CHANGED").apply {
            putExtra("isHighRisk", isHighRisk)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).unregisterReceiver(cancelReceiver)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        speechAssistant?.stop()
    }
}
