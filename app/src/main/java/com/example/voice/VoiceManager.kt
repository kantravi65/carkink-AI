package com.example.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceManager(private val context: Context) : TextToSpeech.OnInitListener {
    private val TAG = "VoiceManager"

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false
    private var audioManager: AudioManager? = null

    init {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private fun ensureTtsInitialized() {
        if (tts == null) {
            try {
                tts = TextToSpeech(context, this)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize TextToSpeech", e)
            }
        }
    }

    private fun ensureSpeechRecognizerInitialized(): SpeechRecognizer? {
        if (speechRecognizer == null) {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    // Query package manager to find an active speech recognition service.
                    // This is extremely important for custom car Android devices (like Ambrane/MTK boxes),
                    // which often don't register a standard default recognizer component.
                    val pm = context.packageManager
                    val serviceIntent = Intent("android.speech.RecognitionService")
                    val resolveInfos = pm.queryIntentServices(serviceIntent, 0)
                    
                    var targetComponent: ComponentName? = null
                    if (!resolveInfos.isNullOrEmpty()) {
                        for (info in resolveInfos) {
                            val serviceInfo = info.serviceInfo
                            if (serviceInfo != null) {
                                val packageName = serviceInfo.packageName
                                val className = serviceInfo.name
                                Log.i(TAG, "Found Speech Recognition service on device: $packageName/$className")
                                if (packageName.contains("google", ignoreCase = true)) {
                                    targetComponent = ComponentName(packageName, className)
                                    break
                                }
                            }
                        }
                        // Fallback to the first available service if Google is not present
                        if (targetComponent == null) {
                            val first = resolveInfos[0].serviceInfo
                            targetComponent = ComponentName(first.packageName, first.name)
                        }
                    }

                    if (targetComponent != null) {
                        Log.i(TAG, "Creating SpeechRecognizer with explicit component: ${targetComponent.flattenToString()}")
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context, targetComponent)
                    } else {
                        Log.i(TAG, "Creating SpeechRecognizer with default platform component")
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    }
                } else {
                    Log.w(TAG, "Speech recognition is not available on this device.")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize SpeechRecognizer", e)
                speechRecognizer = null
            }
        }
        return speechRecognizer
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                val result = tts?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language is not supported for TTS")
                } else {
                    isTtsReady = true
                    Log.i(TAG, "TTS initialized successfully")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "TTS setLanguage exception", e)
            }
        } else {
            Log.e(TAG, "TTS Initialization failed with status $status")
        }
    }

    fun speak(text: String) {
        try {
            ensureTtsInitialized()
            if (isTtsReady && tts != null) {
                Log.d(TAG, "Speaking: $text")
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CarLinkTTS")
            } else {
                Log.w(TAG, "TTS is not ready yet")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error speaking text", e)
        }
    }

    private fun requestAudioFocus() {
        try {
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
            Log.d(TAG, "Audio focus requested (ducking active)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        try {
            audioManager?.abandonAudioFocus(null)
            Log.d(TAG, "Audio focus abandoned")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to abandon audio focus", e)
        }
    }

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onPartialResult: (String) -> Unit = {}
    ) {
        // Enforce execution on the main UI looper
        Handler(Looper.getMainLooper()).post {
            // 1. Force release any existing state to prevent ERROR_CLIENT (5)
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (t: Throwable) {
                Log.w(TAG, "Error destroying previous recognizer instance", t)
            }

            val recognizer = ensureSpeechRecognizerInitialized()
            if (recognizer == null) {
                onError("Voice input service is unavailable on this device.")
                return@post
            }

            // 2. Request audio focus to duck/pause system media so that cabin/music noise is silenced
            requestAudioFocus()

            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    // Customize timeouts to be more generous for vehicle cabin environments using raw string keys to be safe on all API levels
                    putExtra("android.speech.extras.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 2500L)
                    putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 2500L)
                    putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLE_COMPLETE_SILENCE_LENGTH_MILLIS", 2500L)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Ready for speech")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Speech beginning")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "Speech end")
                    }

                    override fun onError(error: Int) {
                        abandonAudioFocus()
                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions missing"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy"
                            SpeechRecognizer.ERROR_SERVER -> "Server-side error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                            else -> "Speech recognizer notice ($error)"
                        }
                        Log.e(TAG, "Recognizer error: $message ($error)")
                        onError(message)
                    }

                    override fun onResults(results: Bundle?) {
                        abandonAudioFocus()
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val spokenText = matches[0]
                            Log.d(TAG, "Speech results: $spokenText")
                            onResult(spokenText)
                        } else {
                            onError("No matching speech found")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            onPartialResult(matches[0])
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)
            } catch (e: Throwable) {
                abandonAudioFocus()
                Log.e(TAG, "startListening exception", e)
                onError("Microphone error: ${e.localizedMessage}")
            }
        }
    }

    fun stopListening() {
        Handler(Looper.getMainLooper()).post {
            abandonAudioFocus()
            try {
                speechRecognizer?.stopListening()
            } catch (e: Throwable) {
                Log.e(TAG, "stopListening exception", e)
            }
        }
    }

    fun destroy() {
        Handler(Looper.getMainLooper()).post {
            abandonAudioFocus()
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (e: Throwable) {
                Log.e(TAG, "TTS shutdown exception", e)
            }
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Throwable) {
                Log.e(TAG, "SpeechRecognizer destroy exception", e)
            }
        }
    }
}
