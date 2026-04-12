package com.team404.dualshield.ai

import android.content.Context
import android.util.Log
import com.google.android.gms.tflite.java.TfLite
import org.tensorflow.lite.InterpreterApi
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.LinkedList
import kotlin.math.sqrt

/**
 * Advanced Accident Detector using 1D CNN TFLite model (Play Services version).
 * Analyzes sliding windows of Accelerometer, Gyroscope, and Speed data.
 */
class AccidentDetector(private val context: Context) {

    private var interpreter: InterpreterApi? = null
    private var isInitialized = false

    // Sliding window config
    private val WINDOW_SIZE = 20
    private val NUM_FEATURES = 10
    private val windowBuffer = LinkedList<FloatArray>()

    // Tracking last acc_mag to calculate Jerk
    private var lastAccMag: Float = 0f

    // Cooldown: prevent SOS from firing multiple times within 30 seconds
    private var lastSosTriggerTime: Long = 0L
    private val SOS_COOLDOWN_MS = 30_000L

    // Python StandardScaler exact values (from Colab export)
    private val SCALER_MEANS = floatArrayOf(0.5712545f, 0.47822156f, 10.075371f, 0.01969897f, 0.01948064f, 0.02012330f, 45.072067f, 10.200145f, 0.0857426f, 0.00041608f)
    private val SCALER_SCALES = floatArrayOf(1.1308369f, 1.1277571f, 1.0527784f, 0.04971329f, 0.05021980f, 0.04986825f, 20.985805f, 1.2977516f, 0.0360691f, 0.5210295f)

    init {
        // Initialize Play Services TFLite asynchronously
        TfLite.initialize(context).addOnSuccessListener {
            try {
                val modelBuffer = loadModelFile("accident_model.tflite")
                if (modelBuffer != null) {
                    val options = InterpreterApi.Options()
                    interpreter = InterpreterApi.create(modelBuffer, options)
                    isInitialized = true
                    Log.d("AccidentDetector", "✅ TFLite CNN initialized successfully via Play Services!")
                }
            } catch (e: Exception) {
                Log.e("AccidentDetector", "❌ TFLite model loading failed: ${e.message}")
            }
        }.addOnFailureListener {
            Log.e("AccidentDetector", "❌ TFLite Play Services failed to initialize: ${it.message}")
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer? {
        return try {
            val fileDescriptor = context.assets.openFd(modelName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            Log.e("AccidentDetector", "Model file not found in assets/")
            null
        }
    }

    /**
     * Feed continuous sensor data here. Returns TRUE if an emergency is actively detected.
     */
    fun processSensorData(
        accX: Float, accY: Float, accZ: Float,
        gyroX: Float, gyroY: Float, gyroZ: Float,
        speedKmh: Float
    ): Boolean {
        // 1. Calculate derived physics features (always, regardless of model state)
        val accMag = sqrt((accX * accX + accY * accY + accZ * accZ).toDouble()).toFloat()
        val jerk = if (windowBuffer.isEmpty()) 0f else Math.abs(accMag - lastAccMag)
        lastAccMag = accMag

        // ── HARDWARE SHAKE OVERRIDE ──────────────────────────────────────────
        // This runs FIRST, before any model check, so it works even if TFLite fails to load.
        // Gravity at rest = ~9.8. A value > 20 means violent shake/impact.
        val now = System.currentTimeMillis()
        if ((accMag > 20f || jerk > 12f) && (now - lastSosTriggerTime) > SOS_COOLDOWN_MS) {
            Log.e("AccidentDetector", "🚨 SHAKE DETECTED (Mag: $accMag, Jerk: $jerk) → FORCING SOS! 🚨")
            lastSosTriggerTime = now
            windowBuffer.clear()
            return true
        }

        // If model not ready, skip AI inference (shake override above still works)
        if (!isInitialized || interpreter == null) return false

        // 2. Assemble 10 feature array
        val gyroMag = sqrt((gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ).toDouble()).toFloat()
        val rawFeatures = floatArrayOf(
            accX, accY, accZ,
            gyroX, gyroY, gyroZ,
            speedKmh,
            accMag, gyroMag, jerk
        )

        // 3. Normalize array using StandardScaler parameters
        val scaledFeatures = FloatArray(NUM_FEATURES)
        for (i in 0 until NUM_FEATURES) {
            scaledFeatures[i] = (rawFeatures[i] - SCALER_MEANS[i]) / SCALER_SCALES[i]
        }

        // 4. Slide Window
        windowBuffer.addLast(scaledFeatures)
        if (windowBuffer.size > WINDOW_SIZE) {
            windowBuffer.removeFirst()
        }

        // 5. Predict if window is full
        if (windowBuffer.size == WINDOW_SIZE) {
            return runInference()
        }

        return false
    }

    private fun runInference(): Boolean {
        // Prepare I/O buffers
        val inputBuffer = ByteBuffer.allocateDirect(WINDOW_SIZE * NUM_FEATURES * 4) // 4 bytes per float
        inputBuffer.order(ByteOrder.nativeOrder())
        
        for (frame in windowBuffer) {
            for (value in frame) {
                inputBuffer.putFloat(value)
            }
        }
        
        val outputArray = Array(1) { FloatArray(5) } // 5 Classes
        
        try {
            interpreter?.run(inputBuffer, outputArray)
            
            val probabilities = outputArray[0]
            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
            val maxConfidence = probabilities[maxIndex]
            
            val classMap = arrayOf("Normal", "Sudden Fall", "Collision", "Harsh Braking", "Rollover")
            
            // Log for debugging
            if (maxIndex != 0) { // Not normal
                Log.d("AccidentDetector", "⚠️ CNN Predicted: ${classMap[maxIndex]} ($maxConfidence)")
            }

            // CRITICAL LOGIC: If network predicts Collision (2) or Rollover (4) with high confidence!
            val now2 = System.currentTimeMillis()
            if ((maxIndex == 2 || maxIndex == 4) && maxConfidence >= 0.70f && (now2 - lastSosTriggerTime) > SOS_COOLDOWN_MS) {
                Log.e("AccidentDetector", "🚨🚨 CRITICAL CRASH DETECTED VIA CNN: ${classMap[maxIndex]} 🚨🚨")
                lastSosTriggerTime = now2
                return true
            }
            
            // Note: Harsh Braking (3) and Sudden Fall (1) are ignored for SOS to prevent false positives while driving.
        } catch (e: Exception) {
            Log.e("AccidentDetector", "Inference error: ${e.message}")
        }
        
        return false
    }
}
