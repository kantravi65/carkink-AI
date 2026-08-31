package com.example.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class NavState(
    val destination: String? = null,
    val isNavigating: Boolean = false,
    val currentRoad: String = "National Highway 8",
    val speedKmh: Int = 62,
    val remainingDistanceKm: Double = 0.0,
    val remainingTimeMin: Int = 0,
    val progress: Float = 0f, // 0.0 to 1.0 along the path
    val eta: String = "--:--",
    val coordinates: List<Offset> = emptyList(), // Simulated road points
    val carPosition: Offset = Offset(0f, 0f)
)

class NavigationManager(private val context: Context) {
    private val _navState = MutableStateFlow(NavState())
    val navState: StateFlow<NavState> = _navState.asStateFlow()

    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val sampleRoads = listOf(
        "Grand Trunk Road", "Outer Ring Road", "Connaught Place Bypass",
        "Pacific Coast Highway", "Main Street", "Aviation Boulevard",
        "NH-48 Expressway", "MG Road", "Cyber City Flyover"
    )

    init {
        // Start simple cruising simulation by default
        startCruising()
    }

    fun startCruising() {
        simulationJob?.cancel()
        _navState.value = NavState(
            destination = null,
            isNavigating = false,
            currentRoad = "Cruising on " + sampleRoads.random(),
            speedKmh = 55,
            coordinates = generateRandomPath(Offset(100f, 100f), Offset(400f, 300f))
        )

        simulationJob = scope.launch {
            var progress = 0f
            while (true) {
                delay(800)
                progress += 0.02f
                if (progress > 1f) {
                    progress = 0f
                    // Change road
                    _navState.update {
                        it.copy(
                            currentRoad = "Cruising on " + sampleRoads.random(),
                            coordinates = generateRandomPath(Offset(100f, 100f), Offset(400f, 300f))
                        )
                    }
                }
                
                _navState.update { state ->
                    val pos = interpolatePath(state.coordinates, progress)
                    state.copy(
                        progress = progress,
                        carPosition = pos,
                        speedKmh = (50..65).random()
                    )
                }
            }
        }
    }

    fun navigateTo(destination: String) {
        simulationJob?.cancel()

        // Generate coordinates for the simulated route
        val start = Offset(100f, 400f)
        val end = Offset(600f, 100f)
        val route = generateRandomPath(start, end)
        val initialDist = (15..45).random() + Random.nextDouble()
        val initialTime = (initialDist * 1.5).toInt() + 2

        _navState.update {
            it.copy(
                destination = destination,
                isNavigating = true,
                currentRoad = sampleRoads.random(),
                speedKmh = 72,
                remainingDistanceKm = Math.round(initialDist * 10) / 10.0,
                remainingTimeMin = initialTime,
                progress = 0f,
                coordinates = route,
                carPosition = start,
                eta = calculateEta(initialTime)
            )
        }

        simulationJob = scope.launch {
            var currentProgress = 0f
            val totalDistance = initialDist
            val totalTime = initialTime
            
            while (currentProgress < 1.0f) {
                delay(1000) // update every second
                currentProgress += 0.015f // travel speed
                if (currentProgress > 1f) currentProgress = 1f

                val distanceLeft = Math.round((totalDistance * (1f - currentProgress)) * 10) / 10.0
                val timeLeft = (totalTime * (1f - currentProgress)).toInt()

                _navState.update { state ->
                    val nextPos = interpolatePath(state.coordinates, currentProgress)
                    val nextRoad = if (currentProgress > 0.4f && currentProgress < 0.7f) {
                        "Merging into " + sampleRoads.random()
                    } else if (currentProgress >= 0.7f) {
                        "Approaching " + destination
                    } else {
                        state.currentRoad
                    }

                    state.copy(
                        progress = currentProgress,
                        remainingDistanceKm = distanceLeft,
                        remainingTimeMin = timeLeft,
                        carPosition = nextPos,
                        currentRoad = nextRoad,
                        speedKmh = if (currentProgress > 0.9f) 30 else (65..85).random()
                    )
                }
            }

            // Arrived
            _navState.update {
                it.copy(
                    currentRoad = "Arrived at $destination",
                    remainingDistanceKm = 0.0,
                    remainingTimeMin = 0,
                    speedKmh = 0,
                    isNavigating = false
                )
            }
            delay(5000)
            startCruising() // Go back to scenic cruising
        }
    }

    fun launchGoogleMaps(destination: String) {
        try {
            val gmmIntentUri = Uri.parse("geo:0,0?q=" + Uri.encode(destination))
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(mapIntent)
        } catch (e: Exception) {
            try {
                // Fallback: Open in web browser
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(destination))).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
            } catch (ex: Exception) {
                android.util.Log.e("NavigationManager", "Failed to launch maps browser fallback", ex)
            }
        }
    }

    private fun calculateEta(minutesToAdd: Int): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.add(java.util.Calendar.MINUTE, minutesToAdd)
        val format = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return format.format(calendar.time)
    }

    private fun generateRandomPath(start: Offset, end: Offset): List<Offset> {
        val points = mutableListOf<Offset>()
        points.add(start)
        
        // Generate intermediate zigzag/curved road points
        val numPoints = 4
        val dx = (end.x - start.x) / (numPoints + 1)
        val dy = (end.y - start.y) / (numPoints + 1)

        for (i in 1..numPoints) {
            val px = start.x + dx * i + (-40..40).random()
            val py = start.y + dy * i + (-40..40).random()
            points.add(Offset(px, py))
        }

        points.add(end)
        return points
    }

    private fun interpolatePath(points: List<Offset>, progress: Float): Offset {
        if (points.isEmpty()) return Offset(0f, 0f)
        if (points.size == 1) return points[0]

        val totalSegments = points.size - 1
        val segment = (progress * totalSegments).toInt().coerceIn(0, totalSegments - 1)
        val segmentProgress = (progress * totalSegments) - segment

        val startPt = points[segment]
        val endPt = points[segment + 1]

        val rx = startPt.x + (endPt.x - startPt.x) * segmentProgress
        val ry = startPt.y + (endPt.y - startPt.y) * segmentProgress
        return Offset(rx, ry)
    }
}
