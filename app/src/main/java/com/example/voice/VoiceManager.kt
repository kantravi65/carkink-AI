package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
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

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onPartialResult: (String) -> Unit = {}
    ) {
        val recognizer = ensureSpeechRecognizerInitialized()
        if (recognizer == null) {
            onError("Voice input isn't available on this emulator. Use the text bar below to type commands!")
            return
        }

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
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
                    Log.e(TAG, "Recognizer error: $message")
                    onError(message)
                }

                override fun onResults(results: Bundle?) {
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
            Log.e(TAG, "startListening exception", e)
            onError("Microphone error: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Throwable) {
            Log.e(TAG, "stopListening exception", e)
        }
    }

    fun destroy() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Throwable) {
            Log.e(TAG, "TTS shutdown exception", e)
        }
        try {
            speechRecognizer?.destroy()
        } catch (e: Throwable) {
            Log.e(TAG, "SpeechRecognizer destroy exception", e)
        }
    }
}
