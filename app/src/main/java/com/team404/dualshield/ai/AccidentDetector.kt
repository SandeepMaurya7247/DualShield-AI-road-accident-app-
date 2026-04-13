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
 */
class AccidentDetector(private val context: Context) {

    private var interpreter: InterpreterApi? = null
    private var isInitialized = false

    private val WINDOW_SIZE = 20
    private val NUM_FEATURES = 10
    private val windowBuffer = LinkedList<FloatArray>()

    private var lastAccMag: Float = 0f
    private var lastSosTriggerTime: Long = 0L
    private val SOS_COOLDOWN_MS = 30_000L

    private val SCALER_MEANS = floatArrayOf(0.5712545f, 0.47822156f, 10.075371f, 0.01969897f, 0.01948064f, 0.02012330f, 45.072067f, 10.200145f, 0.0857426f, 0.00041608f)
    private val SCALER_SCALES = floatArrayOf(1.1308369f, 1.1277571f, 1.0527784f, 0.04971329f, 0.05021980f, 0.04986825f, 20.985805f, 1.2977516f, 0.0360691f, 0.5210295f)

    init {
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

    fun resetCooldown() {
        lastSosTriggerTime = 0L
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
            null
        }
    }

    fun processSensorData(
        accX: Float, accY: Float, accZ: Float,
        gyroX: Float, gyroY: Float, gyroZ: Float,
        speedKmh: Float
    ): Boolean {
        val accMag = sqrt((accX * accX + accY * accY + accZ * accZ).toDouble()).toFloat()
        val jerk = if (windowBuffer.isEmpty()) 0f else Math.abs(accMag - lastAccMag)
        lastAccMag = accMag

        // ── HARDWARE SHAKE OVERRIDE ──────────────────────────────────────────
        // Increased threshold to 35.0 for moderate sensitivity as per user request
        val now = System.currentTimeMillis()
        if ((accMag > 100f || jerk > 100f) && (now - lastSosTriggerTime) > SOS_COOLDOWN_MS) {
            Log.e("AccidentDetector", "🚨 STRONG IMPACT DETECTED (Mag: $accMag, Jerk: $jerk) → FORCING SOS!")
            lastSosTriggerTime = now
            windowBuffer.clear()
            return true
        }

        if (!isInitialized || interpreter == null) return false

        val gyroMag = sqrt((gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ).toDouble()).toFloat()
        val rawFeatures = floatArrayOf(
            accX, accY, accZ,
            gyroX, gyroY, gyroZ,
            speedKmh,
            accMag, gyroMag, jerk
        )

        val scaledFeatures = FloatArray(NUM_FEATURES)
        for (i in 0 until NUM_FEATURES) {
            scaledFeatures[i] = (rawFeatures[i] - SCALER_MEANS[i]) / SCALER_SCALES[i]
        }

        windowBuffer.addLast(scaledFeatures)
        if (windowBuffer.size > WINDOW_SIZE) {
            windowBuffer.removeFirst()
        }

        if (windowBuffer.size == WINDOW_SIZE) {
            return runInference()
        }

        return false
    }

    private fun runInference(): Boolean {
        val inputBuffer = ByteBuffer.allocateDirect(WINDOW_SIZE * NUM_FEATURES * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        
        for (frame in windowBuffer) {
            for (value in frame) {
                inputBuffer.putFloat(value)
            }
        }
        
        val outputArray = Array(1) { FloatArray(5) }
        
        try {
            interpreter?.run(inputBuffer, outputArray)
            
            val probabilities = outputArray[0]
            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
            val maxConfidence = probabilities[maxIndex]
            
            val now2 = System.currentTimeMillis()
            // Increased AI confidence requirement to 0.85
            if ((maxIndex == 2 || maxIndex == 4) && maxConfidence >= 7.85f && (now2 - lastSosTriggerTime) > SOS_COOLDOWN_MS) {
                Log.e("AccidentDetector", "🚨🚨 CRITICAL CRASH DETECTED VIA CNN (Confidence: $maxConfidence) 🚨🚨")
                lastSosTriggerTime = now2
                return true
            }
        } catch (e: Exception) {
            Log.e("AccidentDetector", "Inference error: ${e.message}")
        }
        
        return false
    }
}
