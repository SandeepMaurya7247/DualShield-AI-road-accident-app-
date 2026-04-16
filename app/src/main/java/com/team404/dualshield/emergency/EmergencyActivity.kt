package com.team404.dualshield.emergency

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team404.dualshield.api.ContactItem
import com.team404.dualshield.api.UserSession
import com.team404.dualshield.ui.theme.DualShieldTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class EmergencyActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ALARM-STYLE LOCK SCREEN BYPASS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // START LOUD ALARM SOUND (Disabled by User Request)
        /*
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) 
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("EmergencyActivity", "Failed to play alarm: ${e.message}")
        }
        */

        // START VIBRATION
        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 200, 500), 0)
            }
        } catch (e: Exception) {
            Log.e("EmergencyActivity", "Failed to vibrate: ${e.message}")
        }

        // INITIALIZE TTS (Voice Assistant)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setLanguage(Locale.US)
            }
        }

        // INITIALIZE SPEECH RECOGNIZER
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        setupSpeechRecognizer()

        val userPhone = UserSession.getPhone(this)
        val userId = UserSession.getUserId(this)

        setContent {
            DualShieldTheme {
                EmergencyCountdownScreen(
                    userPhone = userPhone,
                    userId = userId,
                    onFinish = { 
                        stopAllService()
                        finish() 
                    }
                )
            }
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (isListening) startListening()
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    for (result in matches) {
                        val word = result.lowercase()
                        if (word.contains("yes") || word.contains("safe") || word.contains("haan") || word.contains("thik")) {
                            Log.d("EmergencyActivity", "Voice Cancel Detected: $word")
                            handleVoiceCancel()
                            return
                        }
                    }
                }
                if (isListening) startListening()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    for (result in matches) {
                        val word = result.lowercase()
                        if (word.contains("yes") || word.contains("safe") || word.contains("haan") || word.contains("thik")) {
                            Log.d("EmergencyActivity", "Voice Partial Cancel Detected: $word")
                            handleVoiceCancel()
                            return
                        }
                    }
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // SPEED & SENSITIVITY OPTIMIZATIONS
                putExtra("android.speech.extra.DICTATION_MODE", true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }
            isListening = true
            // mediaPlayer?.setVolume(0.1f, 0.1f) // Duck volume while listening
            speechRecognizer?.startListening(intent)
        }
    }

    fun speakPrompt() {
        // mediaPlayer?.setVolume(0.1f, 0.1f) // Duck alarm volume
        tts?.speak("Emergency detected. Are you safe? Please say Yes to cancel the alert.", TextToSpeech.QUEUE_FLUSH, null, "prompt")
        activityScope.launch {
            delay(2500) // Wait for TTS to finish speaking
            startListening()
        }
    }

    private fun handleVoiceCancel() {
        val intent = Intent("com.team404.dualshield.ACTION_SOS_CANCELLED")
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        stopAllService()
        finish()
    }

    private fun stopAllService() {
        isListening = false
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }

    override fun onDestroy() {
        stopAllService()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, EmergencyActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun EmergencyCountdownScreen(
    userPhone: String,
    userId: String,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    var timeLeft by remember { mutableStateOf(30) }
    var sosDispatched by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var statusText by remember { mutableStateOf("CRASH DETECTED") }
    var isCancelled by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    LaunchedEffect(Unit) {
        try {
            val api = com.team404.dualshield.api.BackendApi.create()
            val resp = api.getContacts(userPhone)
            if (resp.isSuccessful) {
                contacts = resp.body() ?: emptyList()
            } else {
                contacts = loadLocalContacts(context)
            }
        } catch (e: Exception) {
            contacts = loadLocalContacts(context)
        }
    }

    LaunchedEffect(Unit) {
        while (!isCancelled && timeLeft > 0) {
            if (timeLeft % 5 == 0 && timeLeft > 0) {
                (context as? EmergencyActivity)?.speakPrompt()
            }
            delay(1000L)
            timeLeft -= 1
        }
        if (!isCancelled && !sosDispatched) {
            sosDispatched = true
            statusText = "Sending SOS..."
            val phones = contacts.map { it.contact_phone.trim() }.filter { it.isNotBlank() }.distinct()
            val em = com.team404.dualshield.emergency.EmergencyManager(context)
            em.dispatchSOS(phones, userId)
            delay(3000L)
            onFinish()
        }
    }

    fun cancelSos() {
        isCancelled = true
        com.team404.dualshield.emergency.EmergencyManager.resetSosStatus()
        val intent = Intent("com.team404.dualshield.ACTION_SOS_CANCELLED")
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A0000), Color(0xFF2D0000), Color(0xFF1A0000)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.background(Color(0xFFFF1744).copy(alpha = 0.2f), RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFFF1744), RoundedCornerShape(8.dp)).padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("⚠ DUAL SHIELD EMERGENCY", color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(statusText, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            }
            Box(
                modifier = Modifier.size(220.dp).scale(if (!sosDispatched) pulseScale else 1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(progress = timeLeft / 30f, modifier = Modifier.fillMaxSize(), color = Color(0xFFFF1744), trackColor = Color(0xFFFF1744).copy(alpha = 0.15f), strokeWidth = 10.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$timeLeft", color = Color.White, fontSize = 72.sp, fontWeight = FontWeight.Light)
                    Text("seconds", color = Color(0xFFFF6B6B), fontSize = 14.sp)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("SMS + Call will be sent to your emergency contacts", color = Color(0xFFFF6B6B).copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center)
                Button(
                    onClick = { cancelSos() },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E), contentColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4CAF50))
                ) {
                    Text("✓  I'M SAFE — CANCEL SOS", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun loadLocalContacts(context: Context): List<ContactItem> {
    return try {
        val json = UserSession.getContactsLocal(context)
        val type = object : com.google.gson.reflect.TypeToken<List<ContactItem>>() {}.type
        com.google.gson.Gson().fromJson(json, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}
