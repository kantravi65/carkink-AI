package com.example.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MediaState(
    val currentTrack: String = "No Track Selected",
    val artistName: String = "Say 'Play some music on YouTube'",
    val isPlaying: Boolean = false,
    val isYoutubeLaunched: Boolean = false,
    val steeringFeedback: String = "Awaiting steering wheel input"
)

class MediaManager(private val context: Context) {
    private val TAG = "MediaManager"

    private val _mediaState = MutableStateFlow(MediaState())
    val mediaState: StateFlow<MediaState> = _mediaState.asStateFlow()

    private val playlist = listOf(
        Pair("Dil Dil Pakistan", "Vital Signs"),
        Pair("Bohemian Rhapsody", "Queen"),
        Pair("Yellow", "Coldplay"),
        Pair("Shape of You", "Ed Sheeran"),
        Pair("Blinding Lights", "The Weeknd"),
        Pair("Hotel California", "Eagles"),
        Pair("Tum Hi Ho", "Arijit Singh")
    )
    private var currentTrackIndex = 0

    fun playTrack(track: String, artist: String = "YouTube Video") {
        _mediaState.update {
            it.copy(
                currentTrack = track,
                artistName = artist,
                isPlaying = true,
                isYoutubeLaunched = true,
                steeringFeedback = "Loaded via Gemini Voice Command"
            )
        }
        launchYouTubeSearch("$track $artist")
    }

    fun launchYouTubeSearch(query: String) {
        // Try the standard MEDIA_PLAY_FROM_SEARCH intent first (which many apps including YouTube support)
        val searchIntent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(android.provider.MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(android.app.SearchManager.QUERY, query)
            setPackage("com.google.android.youtube")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        try {
            if (searchIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(searchIntent)
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch YouTube app via MEDIA_PLAY_FROM_SEARCH", e)
        }
        
        // Build intent to search on YouTube fallback
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
            setPackage("com.google.android.youtube") // Force open in official YouTube app
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        
        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Fallback to browser YouTube search
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch YouTube app, falling back to browser", e)
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com/results?search_query=" + Uri.encode(query))).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        }
    }

    // Handles hardware media key events received from steering wheel controls or infotainment system
    fun handleMediaKeyEvent(keyCode: Int): Boolean {
        Log.d(TAG, "Received hardware key event: $keyCode")
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                _mediaState.update { it.copy(isPlaying = true, steeringFeedback = "Steering Wheel: PLAY pressed") }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                _mediaState.update { it.copy(isPlaying = false, steeringFeedback = "Steering Wheel: PAUSE pressed") }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                _mediaState.update { it.copy(isPlaying = !it.isPlaying, steeringFeedback = "Steering Wheel: PLAY/PAUSE pressed") }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                playNext()
                _mediaState.update { it.copy(steeringFeedback = "Steering Wheel: NEXT pressed") }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                playPrevious()
                _mediaState.update { it.copy(steeringFeedback = "Steering Wheel: PREVIOUS pressed") }
                return true
            }
        }
        return false
    }

    fun playNext() {
        currentTrackIndex = (currentTrackIndex + 1) % playlist.size
        val (track, artist) = playlist[currentTrackIndex]
        _mediaState.update {
            it.copy(
                currentTrack = track,
                artistName = artist,
                isPlaying = true
            )
        }
        launchYouTubeSearch("$track $artist")
    }

    fun playPrevious() {
        currentTrackIndex = if (currentTrackIndex - 1 < 0) playlist.size - 1 else currentTrackIndex - 1
        val (track, artist) = playlist[currentTrackIndex]
        _mediaState.update {
            it.copy(
                currentTrack = track,
                artistName = artist,
                isPlaying = true
            )
        }
        launchYouTubeSearch("$track $artist")
    }

    fun togglePlayPause() {
        _mediaState.update {
            it.copy(
                isPlaying = !it.isPlaying,
                steeringFeedback = "Infotainment: Play/Pause toggled"
            )
        }
    }
}
