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
            // Fallback for older versions or if type fails
            startForeground(1, createNotification())
        }
        
        checkHighRiskZones()
    }

    private fun checkHighRiskZones() {
        serviceScope.launch {
            try {
                val response = api.getAccidentZones()
                val zones = response.body()
                Log.d("DualShield", "Loaded ${zones?.size ?: 0} high risk zones.")
            } catch (e: Exception) {
                Log.e("DualShield", "Failed to fetch geofences: ${e.message}")
            }
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

                // Stream into the TFLite 1D CNN pipeline
                val isCrash = accidentDetector.processSensorData(
                    accX, accY, accZ,
                    currentGyroX, currentGyroY, currentGyroZ,
                    currentSpeedKmh
                )

                if (isCrash) {
                    triggerEmergencyProtocol()
                }
            }
        }
    }

    private fun triggerEmergencyProtocol() {
        val intent = Intent(this, com.team404.dualshield.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("trigger_sos", true)
        }
        startActivity(intent)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
