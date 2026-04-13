package com.team404.dualshield.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class SpeechAssistantManager(
    private val context: Context, 
    private val onReady: () -> Unit,
    private val onCommandDetected: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false

    init {
        tts = TextToSpeech(context, this)
        // Ensure SpeechRecognizer is initialized on Main Thread
        Handler(Looper.getMainLooper()).post {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                setupSpeechRecognizer()
            } else {
                Log.e("AI_Assistant", "Speech Recognition not available")
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("AI_Assistant", "TTS Language not supported")
            } else {
                isTtsReady = true
                Log.d("AI_Assistant", "TTS Ready")
                onReady() // Notify UI that we are ready to speak
            }
        } else {
            Log.e("AI_Assistant", "TTS Init Failed")
        }
    }

    fun askSafetyQuery() {
        if (isTtsReady) {
            Log.d("AI_Assistant", "Speaking query...")
            tts?.speak("Emergency detected. Are you safe? Please say Yes to cancel the alert.", TextToSpeech.QUEUE_FLUSH, null, "safety_query")
        } else {
            Log.w("AI_Assistant", "askSafetyQuery called before TTS was ready")
        }
    }

    fun speakText(text: String) {
        if (isTtsReady) {
            Log.d("AI_Assistant", "Speaking warning: $text")
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "warning_alert")
        } else {
            Log.w("AI_Assistant", "speakText called before TTS was ready")
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { Log.d("AI_Assistant", "STT: Ready for speech") }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.let {
                    Log.d("AI_Assistant", "STT Heard: $it")
                    if (it.any { s -> 
                        val text = s.lowercase()
                        text.contains("yes") || text.contains("safe") || text.contains("fine") || 
                        text.contains("ok") || text.contains("haan") || text.contains("thik")
                    }) {
                        onCommandDetected("CANCEL")
                    }
                }
            }
            override fun onBeginningOfSpeech() { Log.d("AI_Assistant", "STT: User started speaking") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { Log.d("AI_Assistant", "STT: User stopped speaking") }
            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions missing"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    else -> "Unknown error: $error"
                }
                Log.e("AI_Assistant", "STT Error: $message")
                // Restart listening if it was just a timeout
                if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH) {
                    startListening()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.let {
                    if (it.any { s -> 
                        val text = s.lowercase()
                        text.contains("yes") || text.contains("safe") || text.contains("haan") || text.contains("thik")
                    }) {
                        Log.d("AI_Assistant", "STT Partial Match Detected: $it")
                        onCommandDetected("CANCEL")
                    }
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        Handler(Looper.getMainLooper()).post {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // SPEED OPTIMIZATIONS
                putExtra("android.speech.extra.DICTATION_MODE", true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }
            try {
                speechRecognizer?.startListening(intent)
                Log.d("AI_Assistant", "STT: startListening() called")
            } catch (e: Exception) {
                Log.e("AI_Assistant", "STT: startListening failed: ${e.message}")
            }
        }
    }

    fun stop() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
    }
}
