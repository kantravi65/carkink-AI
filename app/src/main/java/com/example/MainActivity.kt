package com.example

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.media.MediaManager
import com.example.navigation.NavigationManager
import com.example.ui.DashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.voice.VoiceManager
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    private lateinit var voiceManager: VoiceManager
    private lateinit var navigationManager: NavigationManager
    private lateinit var mediaManager: MediaManager
    
    val triggerVoiceFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize core Car Link Managers
        voiceManager = VoiceManager(this)
        navigationManager = NavigationManager(this)
        mediaManager = MediaManager(this)

        enableEdgeToEdge()
        
        if (intent?.action == Intent.ACTION_VOICE_COMMAND || intent?.action == Intent.ACTION_ASSIST) {
            triggerVoiceFlow.tryEmit(Unit)
        }
        
        setContent {
            MyApplicationTheme {
                DashboardScreen(
                    voiceManager = voiceManager,
                    navigationManager = navigationManager,
                    mediaManager = mediaManager,
                    triggerVoiceFlow = triggerVoiceFlow,
                    onSteeringKeyEvent = { simulationType ->
                        val simulatedKeyCode = when (simulationType) {
                            1 -> KeyEvent.KEYCODE_MEDIA_NEXT
                            2 -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                            3 -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                            else -> -1
                        }
                        if (simulatedKeyCode != -1) {
                            mediaManager.handleMediaKeyEvent(simulatedKeyCode)
                        }
                    }
                )
            }
        }
    }

    // Intercept physical steering wheel controls sent via CAN-Bus to the Ambrane device
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
            triggerVoiceFlow.tryEmit(Unit)
            return true
        }
        if (mediaManager.handleMediaKeyEvent(keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VOICE_COMMAND || intent.action == Intent.ACTION_ASSIST) {
            triggerVoiceFlow.tryEmit(Unit)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::voiceManager.isInitialized) {
                voiceManager.destroy()
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Error destroying voiceManager", e)
        }
    }
}
