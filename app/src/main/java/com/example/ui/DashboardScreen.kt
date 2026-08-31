package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voice.LocalCommandParser
import com.example.voice.ParsedCommand
import com.example.media.MediaManager
import com.example.navigation.NavigationManager
import com.example.voice.VoiceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

// Color Palette for Premium Car Link System
val CarBlack = Color(0xFF0C0C0F)
val CarObsidian = Color(0xFF141419)
val CarGlass = Color(0x1F22222E)
val CarBorder = Color(0x2E63758A)
val CyanGlow = Color(0xFF00E5FF)
val AssistantPurple = Color(0xFFB066FF)
val AssistantBlue = Color(0xFF4285F4)
val AlertRed = Color(0xFFFF3B30)
val DarkGray = Color(0xFF23232C)
val LightText = Color(0xFFECECEC)
val MutedText = Color(0xFF9094A0)

enum class AssistantState {
    IDLE, LISTENING, PROCESSING, SPEAKING
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    voiceManager: VoiceManager,
    navigationManager: NavigationManager,
    mediaManager: MediaManager,
    triggerVoiceFlow: SharedFlow<Unit> = MutableSharedFlow(),
    onSteeringKeyEvent: (Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State bindings
    val navState by navigationManager.navState.collectAsState()
    val mediaState by mediaManager.mediaState.collectAsState()

    // Screen-level Speech/Assistant State
    var assistantState by remember { mutableStateOf(AssistantState.IDLE) }
    var voiceTranscript by remember { mutableStateOf("") }
    var commandInputText by remember { mutableStateOf("") }
    var geminiReplyText by remember { mutableStateOf("Welcome, driver. I am your assistant. Press 'TAP TO TALK' or use hands-free steering controls to issue voice commands.") }
    var permissionsGranted by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(false) }

    // Logs for testing
    val systemLogs = remember { mutableStateListOf<String>("System initialized.", "Ready for hands-free YouTube and Maps commands.") }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results[Manifest.permission.RECORD_AUDIO] == true
        if (permissionsGranted) {
            systemLogs.add("Audio permissions granted.")
        } else {
            systemLogs.add("Warning: Audio permissions denied. Use text box fallback.")
        }
    }

    LaunchedEffect(Unit) {
        val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        permissionsGranted = hasAudio
        if (!hasAudio) {
            systemLogs.add("Mic permission needed for voice. Click 'TAP TO TALK' or type commands below.")
        } else {
            systemLogs.add("Audio permission active.")
        }
    }

    // Function to process and execute command locally
    fun executeParsedCommand(commandText: String) {
        if (commandText.isBlank()) return
        
        assistantState = AssistantState.PROCESSING
        systemLogs.add("Processing command: \"$commandText\"")
        
        coroutineScope.launch {
            try {
                val result = LocalCommandParser.processVoiceCommand(commandText)
                geminiReplyText = result.responseText
                assistantState = AssistantState.SPEAKING
                
                // Voice announcement
                voiceManager.speak(result.responseText)
                systemLogs.add("Parsed action: ${result.action}")

                when (result.action) {
                    "NAVIGATE" -> {
                        val destination = result.destination ?: "the requested coordinates"
                        systemLogs.add("Navigation triggered to: $destination")
                        navigationManager.navigateTo(destination)
                    }
                    "YOUTUBE_PLAY" -> {
                        val query = result.query ?: commandText
                        systemLogs.add("YouTube playing query: $query")
                        mediaManager.playTrack(query)
                    }
                    else -> {
                        systemLogs.add("General command parsed")
                    }
                }
                
                delay(4000)
                if (assistantState == AssistantState.SPEAKING) {
                    assistantState = AssistantState.IDLE
                }
            } catch (e: Exception) {
                assistantState = AssistantState.IDLE
                geminiReplyText = "I had trouble parsing that command: ${e.localizedMessage}"
                voiceManager.speak(geminiReplyText)
                systemLogs.add("Error processing: ${e.localizedMessage}")
            }
        }
    }

    // Voice trigger action
    fun triggerVoiceListening() {
        if (!permissionsGranted) {
            systemLogs.add("Permissions required. Please grant RECORD_AUDIO.")
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }

        assistantState = AssistantState.LISTENING
        voiceTranscript = "Listening..."
        voiceManager.startListening(
            onResult = { result ->
                voiceTranscript = result
                executeParsedCommand(result)
            },
            onError = { err ->
                assistantState = AssistantState.IDLE
                voiceTranscript = "Failed: $err"
                systemLogs.add("STT Error: $err")
            },
            onPartialResult = { partial ->
                voiceTranscript = partial
            }
        )
    }

    LaunchedEffect(Unit) {
        triggerVoiceFlow.collectLatest {
            systemLogs.add("Hardware Voice Button Pressed!")
            triggerVoiceListening()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(CarBlack),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CarObsidian)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Car Logo",
                        tint = CyanGlow,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "CAR LINK STREAM",
                        color = LightText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CyanGlow.copy(alpha = 0.15f))
                            .border(1.dp, CyanGlow, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LOCAL AI",
                            color = CyanGlow,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Quick Status Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (permissionsGranted) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Mic Indicator",
                        tint = if (permissionsGranted) CyanGlow else MutedText,
                        modifier = Modifier.size(18.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "Wifi Indicator",
                        tint = CyanGlow,
                        modifier = Modifier.size(18.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.BluetoothConnected,
                        contentDescription = "Bluetooth Status",
                        tint = CyanGlow,
                        modifier = Modifier.size(18.dp)
                    )
                    IconButton(
                        onClick = { showSetupDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Device Setup",
                            tint = CyanGlow,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        // Main split-screen car dashboard grid (Landscape Optimized)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CarBlack)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // MODULE 1: Smart Assistant Panel (Left 35%)
            Box(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarGlass)
                    .border(1.dp, CarBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Assistant Spark",
                                tint = AssistantPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "SMART ASSISTANT",
                                color = LightText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Glowing Mic Center
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(95.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkGray)
                                .clickable { triggerVoiceListening() }
                                .testTag("voice_trigger_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            // Sound pulse animations
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = if (assistantState == AssistantState.LISTENING) 1.4f else 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .drawBehind {
                                            if (assistantState == AssistantState.LISTENING) {
                                                drawCircle(
                                                    color = CyanGlow.copy(alpha = 0.35f),
                                                    radius = size.minDimension / 1.5f * pulseScale
                                                )
                                            } else if (assistantState == AssistantState.PROCESSING) {
                                                drawCircle(
                                                    color = AssistantPurple.copy(alpha = 0.35f),
                                                    radius = size.minDimension / 1.5f * pulseScale
                                                )
                                            }
                                        }
                                        .clip(CircleShape)
                                        .background(
                                            when (assistantState) {
                                                AssistantState.LISTENING -> CyanGlow
                                                AssistantState.PROCESSING -> AssistantPurple
                                                AssistantState.SPEAKING -> AssistantBlue
                                                else -> MutedText
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (assistantState) {
                                            AssistantState.LISTENING -> Icons.Default.SettingsVoice
                                            AssistantState.PROCESSING -> Icons.Default.QueryStats
                                            AssistantState.SPEAKING -> Icons.Default.VolumeUp
                                            else -> Icons.Default.Mic
                                        },
                                        contentDescription = "Mic State",
                                        tint = CarBlack,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = when (assistantState) {
                                        AssistantState.LISTENING -> "LISTENING TO CAR..."
                                        AssistantState.PROCESSING -> "PARSING COMMAND..."
                                        AssistantState.SPEAKING -> "ASSISTANT SPEAKING..."
                                        else -> "TAP TO SPEAK"
                                    },
                                    color = when (assistantState) {
                                        AssistantState.LISTENING -> CyanGlow
                                        AssistantState.PROCESSING -> AssistantPurple
                                        AssistantState.SPEAKING -> AssistantBlue
                                        else -> LightText
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // Transcript Area
                        Text(
                            text = if (voiceTranscript.isNotBlank()) "\"$voiceTranscript\"" else "Awaiting your voice command...",
                            color = if (assistantState == AssistantState.LISTENING) CyanGlow else MutedText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // Assistant Text Response Panel (Glass)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CarBlack.copy(alpha = 0.5f))
                                .border(0.5.dp, CarBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    Text(
                                        text = geminiReplyText,
                                        color = if (assistantState == AssistantState.SPEAKING) CyanGlow else LightText,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Fallback Text Input (Crucial for AI Studio Web Emulator without mic support)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TextField(
                            value = commandInputText,
                            onValueChange = { commandInputText = it },
                            placeholder = { Text("Type voice command...", fontSize = 11.sp, color = MutedText) },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("command_input_fallback"),
                            textStyle = TextStyle(fontSize = 11.sp, color = LightText),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = DarkGray,
                                unfocusedContainerColor = DarkGray,
                                focusedTextColor = LightText,
                                unfocusedTextColor = LightText,
                                cursorColor = CyanGlow,
                                focusedIndicatorColor = CyanGlow,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp)
                        )
                        IconButton(
                            onClick = {
                                if (commandInputText.isNotBlank()) {
                                    voiceTranscript = commandInputText
                                    executeParsedCommand(commandInputText)
                                    commandInputText = ""
                                }
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyanGlow)
                                .testTag("send_command_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send Command",
                                tint = CarBlack,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // MODULE 2: Simulated Real-time Navigation (Center 35%)
            Box(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarGlass)
                    .border(1.dp, CarBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = "Navigation Spark",
                                    tint = CyanGlow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "REAL-TIME NAVIGATION",
                                    color = LightText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            // Active Status indicator
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (navState.isNavigating) CyanGlow.copy(alpha = 0.2f) else DarkGray)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (navState.isNavigating) "ROUTING" else "CRUISING",
                                    color = if (navState.isNavigating) CyanGlow else MutedText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Canvas drawing of simulated GPS path
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CarBlack)
                                .border(0.5.dp, CarBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // 1. Draw Road Grid lines
                                val gridColor = Color(0x0F88A0C0)
                                val spacing = 40f
                                for (x in 0..(size.width / spacing).toInt()) {
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(x * spacing, 0f),
                                        end = Offset(x * spacing, size.height),
                                        strokeWidth = 1f
                                    )
                                }
                                for (y in 0..(size.height / spacing).toInt()) {
                                    drawLine(
                                        color = gridColor,
                                        start = Offset(0f, y * spacing),
                                        end = Offset(size.width, y * spacing),
                                        strokeWidth = 1f
                                    )
                                }

                                // 2. Draw Simulated roads (secondary paths)
                                drawLine(
                                    color = Color(0x19FFFFFF),
                                    start = Offset(0f, size.height / 2),
                                    end = Offset(size.width, size.height / 2),
                                    strokeWidth = 12f
                                )
                                drawLine(
                                    color = Color(0x19FFFFFF),
                                    start = Offset(size.width / 3, 0f),
                                    end = Offset(size.width / 3, size.height),
                                    strokeWidth = 8f
                                )

                                // 3. Draw course path to active destination
                                if (navState.coordinates.isNotEmpty()) {
                                    val routePath = Path().apply {
                                        moveTo(navState.coordinates[0].x, navState.coordinates[0].y)
                                        for (i in 1 until navState.coordinates.size) {
                                            lineTo(navState.coordinates[i].x, navState.coordinates[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = routePath,
                                        color = if (navState.isNavigating) CyanGlow else Color(0x4D00E5FF),
                                        style = Stroke(
                                            width = 6f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                                        )
                                    )

                                    // Draw Destination Flag / Star
                                    val dest = navState.coordinates.last()
                                    drawCircle(
                                        color = AlertRed,
                                        radius = 8f,
                                        center = dest
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 4f,
                                        center = dest
                                    )
                                }

                                // 4. Draw Simulated Vehicle Arrow
                                val carPos = navState.carPosition
                                drawCircle(
                                    color = CyanGlow,
                                    radius = 7f,
                                    center = carPos
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 3f,
                                    center = carPos
                                )
                            }

                            // Current Road Overlay
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CarObsidian.copy(alpha = 0.8f))
                                    .border(0.5.dp, CyanGlow.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = navState.currentRoad,
                                    color = LightText,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Navigation stats overlay (distance & ETA)
                            if (navState.isNavigating) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CarObsidian.copy(alpha = 0.85f))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "${navState.remainingDistanceKm} km",
                                        color = CyanGlow,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "${navState.remainingTimeMin} min · ETA ${navState.eta}",
                                        color = LightText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Digital Speedometer cluster
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "CURRENT SPEED", color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = navState.speedKmh.toString(),
                                        color = LightText,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = " km/h",
                                        color = MutedText,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(bottom = 3.dp)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = "ACTIVE DESTINATION", color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = navState.destination ?: "Free Cruising Mode",
                                    color = if (navState.destination != null) CyanGlow else LightText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Button to launch Google Maps on the device
                    Button(
                        onClick = {
                            val dest = navState.destination ?: "Delhi"
                            navigationManager.launchGoogleMaps(dest)
                            systemLogs.add("External Intent: Launching Google Maps for \"$dest\"")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("launch_maps_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanGlow,
                            contentColor = CarBlack
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Map Icon",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LAUNCH DEVICE MAPS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // MODULE 3: YouTube & Media Controller (Right 30%)
            Box(
                modifier = Modifier
                    .weight(0.30f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarGlass)
                    .border(1.dp, CarBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = "YouTube Spark",
                                tint = AlertRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "YOUTUBE MEDIA",
                                color = LightText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // YouTube Track visual Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(85.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CarBlack)
                                .border(0.5.dp, CarBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Red YouTube mockup square
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AlertRed),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "YouTube Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = mediaState.currentTrack,
                                        color = LightText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = mediaState.artistName,
                                        color = MutedText,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Animated Waveform to show playing state
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(25.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "waveform")
                            val animList = List(8) { index ->
                                infiniteTransition.animateFloat(
                                    initialValue = 0.2f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(400 + (index * 80), easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "bar_$index"
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                animList.forEach { value ->
                                    val heightFactor = if (mediaState.isPlaying) value.value else 0.15f
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height((20 * heightFactor).dp)
                                            .clip(CircleShape)
                                            .background(if (mediaState.isPlaying) AlertRed else MutedText)
                                    )
                                }
                            }
                        }

                        // Virtual Infotainment / Steering Wheel hardware interface
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "STEERING WHEEL MEDIA ACTION LINK",
                                color = MutedText,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            
                            // Visual display of steering feedback
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkGray)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = mediaState.steeringFeedback,
                                    color = CyanGlow,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            // SIMULATION BUTTONS: Allows AI Studio users to trigger Steering Wheel button signals!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = { onSteeringKeyEvent(1) }, // Next Track Key
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .testTag("steering_next_sim"),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkGray),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("ST-NEXT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = LightText)
                                }
                                Button(
                                    onClick = { onSteeringKeyEvent(2) }, // Previous Track Key
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .testTag("steering_prev_sim"),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkGray),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("ST-PREV", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = LightText)
                                }
                                Button(
                                    onClick = { onSteeringKeyEvent(3) }, // Play/Pause Key
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .testTag("steering_toggle_sim"),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkGray),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("ST-TOGGLE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = LightText)
                                }
                            }
                        }
                    }

                    // On-screen Infotainment touch buttons (Skip, play, pause)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { mediaManager.playPrevious() },
                            modifier = Modifier.size(34.dp).background(DarkGray, CircleShape)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = LightText)
                        }

                        IconButton(
                            onClick = { mediaManager.togglePlayPause() },
                            modifier = Modifier.size(42.dp).background(AlertRed, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (mediaState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White
                            )
                        }

                        IconButton(
                            onClick = { mediaManager.playNext() },
                            modifier = Modifier.size(34.dp).background(DarkGray, CircleShape)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = LightText)
                        }
                    }
                }
            }
        }
    }

    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = { showSetupDialog = false },
            containerColor = CarObsidian,
            titleContentColor = LightText,
            textContentColor = LightText,
            title = {
                Text("Device Setup / Fix Wheel Button")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Ambrane devices often hide default Android settings. Use these buttons to force-open the hidden menus so you can assign the voice button to this app.",
                        fontSize = 12.sp,
                        color = MutedText
                    )
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } catch (e: Exception) {
                                systemLogs.add("Could not open Accessibility settings")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanGlow, contentColor = CarBlack),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1. Open Hidden Accessibility")
                    }
                    Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
                            } catch (e: Exception) {
                                systemLogs.add("Could not open Voice Input settings")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkGray, contentColor = LightText),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("2. Open Hidden Assistant Settings")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSetupDialog = false }) {
                    Text("Close", color = CyanGlow)
                }
            }
        )
    }
}
