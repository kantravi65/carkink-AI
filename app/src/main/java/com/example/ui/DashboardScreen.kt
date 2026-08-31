package com.example.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voice.LocalCommandParser
import com.example.media.MediaManager
import com.example.navigation.NavigationManager
import com.example.voice.VoiceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

// Styled Dark Car Theme Palette
val CarBlack = Color(0xFF0C0C0F)
val CarObsidian = Color(0xFF141419)
val CarPanelBackground = Color(0xFF1C1D24)
val CyanGlow = Color(0xFF00E5FF)
val AssistantPurple = Color(0xFFB066FF)
val AssistantBlue = Color(0xFF4285F4)
val DarkGray = Color(0xFF23232C)
val LightText = Color(0xFFECECEC)
val MutedText = Color(0xFF9094A0)
val WarningYellow = Color(0xFFFFB300)
val SuccessGreen = Color(0xFF00E676)

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

    var assistantState by remember { mutableStateOf(AssistantState.IDLE) }
    var voiceTranscript by remember { mutableStateOf("") }
    var commandInputText by remember { mutableStateOf("") }
    var geminiReplyText by remember { mutableStateOf("Ready to receive voice commands. Press the button or use steering controls.") }
    var permissionsGranted by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(false) }

    val navState by navigationManager.navState.collectAsState()
    val mediaState by mediaManager.mediaState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results[Manifest.permission.RECORD_AUDIO] == true
        val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                              results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) {
            navigationManager.enableRealGps()
        }
    }

    LaunchedEffect(Unit) {
        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        val hasLocation = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        permissionsGranted = hasMic
        if (hasLocation) {
            navigationManager.enableRealGps()
        } else {
            // Prompt for all relevant car permissions
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun executeParsedCommand(commandText: String) {
        if (commandText.isBlank()) return
        
        assistantState = AssistantState.PROCESSING
        
        coroutineScope.launch {
            try {
                val result = LocalCommandParser.processVoiceCommand(commandText)
                geminiReplyText = result.responseText
                assistantState = AssistantState.SPEAKING
                
                voiceManager.speak(result.responseText)

                when (result.action) {
                    "NAVIGATE" -> {
                        val destination = result.destination ?: "the requested coordinates"
                        navigationManager.navigateTo(destination)
                        navigationManager.launchGoogleMaps(destination)
                    }
                    "YOUTUBE_PLAY" -> {
                        val query = result.query ?: commandText
                        mediaManager.playTrack(query, "")
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
            }
        }
    }

    fun triggerVoiceListening() {
        if (!permissionsGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
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
            },
            onPartialResult = { partial ->
                voiceTranscript = partial
            }
        )
    }

    LaunchedEffect(Unit) {
        triggerVoiceFlow.collectLatest {
            triggerVoiceListening()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(CarBlack),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CarObsidian)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.DirectionsCar, "Car Logo", tint = CyanGlow, modifier = Modifier.size(28.dp))
                    Column {
                        Text(
                            "AMBRANE CAR LINK INTEGRATION", 
                            color = LightText, 
                            fontSize = 15.sp, 
                            fontWeight = FontWeight.Bold, 
                            letterSpacing = 1.sp
                        )
                        Text(
                            if (navState.isRealGpsActive) "REAL-TIME TELEMETRY CONNECTED" else "GPS SIMULATION ACTIVE (No Location Permission)", 
                            color = if (navState.isRealGpsActive) SuccessGreen else WarningYellow, 
                            fontSize = 10.sp, 
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSetupDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Device Setup",
                            tint = CyanGlow,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        // Split-screen Landscape optimized layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CarBlack)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Module 1: AI Assistant & Microphone (Left Panel - 35% weight)
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarPanelBackground)
                    .border(1.dp, DarkGray, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "AI ASSISTANT", 
                    color = MutedText, 
                    fontSize = 12.sp, 
                    fontWeight = FontWeight.Bold, 
                    letterSpacing = 0.5.sp
                )

                // Large central voice button with glowing animation
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(DarkGray)
                        .clickable { triggerVoiceListening() }
                        .testTag("voice_trigger_button"),
                    contentAlignment = Alignment.Center
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (assistantState == AssistantState.LISTENING) 1.35f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .drawBehind {
                                if (assistantState == AssistantState.LISTENING) {
                                    drawCircle(color = CyanGlow.copy(alpha = 0.35f), radius = size.minDimension / 1.5f * pulseScale)
                                } else if (assistantState == AssistantState.PROCESSING) {
                                    drawCircle(color = AssistantPurple.copy(alpha = 0.35f), radius = size.minDimension / 1.5f * pulseScale)
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
                            contentDescription = "Voice Button State",
                            tint = CarBlack,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Voice text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (voiceTranscript.isNotBlank()) "\"$voiceTranscript\"" else "Press to speak",
                        color = if (assistantState == AssistantState.LISTENING) CyanGlow else LightText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = geminiReplyText,
                        color = if (assistantState == AssistantState.SPEAKING) CyanGlow else MutedText,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Fallback direct text input
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = commandInputText,
                        onValueChange = { commandInputText = it },
                        placeholder = { Text("Type command...", fontSize = 11.sp, color = MutedText) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        textStyle = TextStyle(fontSize = 12.sp, color = LightText),
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
                        shape = RoundedCornerShape(8.dp)
                    )
                    IconButton(
                        onClick = {
                            if (commandInputText.isNotBlank()) {
                                voiceTranscript = commandInputText
                                executeParsedCommand(commandInputText)
                                commandInputText = ""
                            }
                        },
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(CyanGlow)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = CarBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Module 2: GPS Telemetry & Instruments (Center Panel - 35% weight)
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarPanelBackground)
                    .border(1.dp, DarkGray, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "INSTRUMENT CLUSTER", 
                    color = MutedText, 
                    fontSize = 12.sp, 
                    fontWeight = FontWeight.Bold, 
                    letterSpacing = 0.5.sp
                )

                // Highly visible modern digital Speedometer
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${navState.speedKmh}",
                        color = if (navState.isRealGpsActive) CyanGlow else WarningYellow,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "KM/H",
                        color = LightText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = if (navState.isRealGpsActive) "LIVE GPS TELEMETRY" else "SIMULATED TELEMETRY",
                        color = if (navState.isRealGpsActive) SuccessGreen else MutedText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Physical Telemetry reading from actual sensors
                Column(
                    modifier = Modifier.fillMaxWidth().background(DarkGray, RoundedCornerShape(8.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("GPS Latitude", color = MutedText, fontSize = 10.sp)
                        Text(if (navState.isRealGpsActive) "%.5f".format(navState.latitude) else "Cruising...", color = LightText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("GPS Longitude", color = MutedText, fontSize = 10.sp)
                        Text(if (navState.isRealGpsActive) "%.5f".format(navState.longitude) else "Cruising...", color = LightText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("GPS Accuracy", color = MutedText, fontSize = 10.sp)
                        Text(if (navState.isRealGpsActive) "%.1f m".format(navState.accuracy) else "No Satellite Signal", color = if (navState.isRealGpsActive) SuccessGreen else WarningYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Active Location Address/Road Text Info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Navigation, "Road Icon", tint = CyanGlow, modifier = Modifier.size(16.dp))
                    Text(
                        text = navState.currentRoad,
                        color = LightText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Module 3: Control Deck & Shortcut Apps (Right Panel - 30% weight)
            Column(
                modifier = Modifier
                    .weight(0.30f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarPanelBackground)
                    .border(1.dp, DarkGray, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "CONTROL DECK", 
                    color = MutedText, 
                    fontSize = 12.sp, 
                    fontWeight = FontWeight.Bold, 
                    letterSpacing = 0.5.sp
                )

                // Current playing track
                Column(
                    modifier = Modifier.fillMaxWidth().background(DarkGray, RoundedCornerShape(8.dp)).padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = mediaState.currentTrack,
                        color = LightText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mediaState.artistName,
                        color = MutedText,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }

                // Quick Launcher Action Shortcuts
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { navigationManager.launchGoogleMaps(navState.destination ?: "Near me") },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkGray, contentColor = LightText),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Map, "Maps", modifier = Modifier.size(16.dp), tint = CyanGlow)
                            Text("Open Google Maps", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { mediaManager.launchYouTubeSearch("Play standard background music") },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkGray, contentColor = LightText),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.PlayArrow, "YouTube", modifier = Modifier.size(16.dp), tint = AssistantPurple)
                            Text("Open YouTube Music", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { showSetupDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanGlow, contentColor = CarBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Settings, "Setup", modifier = Modifier.size(16.dp))
                            Text("Accessibility Setup", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    var micTestState by remember { mutableStateOf("IDLE") } // "IDLE", "RECORDING", "PLAYBACK", "ERROR"
    var micTestMessage by remember { mutableStateOf("Not tested yet. Use this to verify if the box's mic input is functional.") }

    // Advanced Troubleshooting Settings Dialog
    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = { showSetupDialog = false },
            containerColor = CarObsidian,
            titleContentColor = LightText,
            textContentColor = LightText,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Build, "Diagnostics", tint = CyanGlow, modifier = Modifier.size(24.dp))
                        Text("System Diagnostics & Helper", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { showSetupDialog = false }) {
                        Icon(Icons.Default.Close, "Close", tint = MutedText)
                    }
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // --- SECTION 1: MICROPHONE HARDWARE DIAGNOSTICS ---
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkGray),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.SettingsVoice,
                                    contentDescription = "Mic Test",
                                    tint = when (micTestState) {
                                        "RECORDING" -> WarningYellow
                                        "PLAYBACK" -> SuccessGreen
                                        else -> CyanGlow
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Text("MIC HARDWARE TEST (LOOPBACK)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LightText)
                            }
                            
                            Text(
                                "Car CarPlay boxes often block microphone input channels. This tool records 3 seconds of raw audio from the mic and plays it back to confirm the hardware connection.",
                                fontSize = 10.sp,
                                color = MutedText
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        voiceManager.startMicHardwareTest(
                                            onRecordStart = {
                                                micTestState = "RECORDING"
                                                micTestMessage = "🎤 RECORDING (3s)... Speak loudly into your Brezza's microphone now!"
                                            },
                                            onRecordEnd = {
                                                micTestState = "PROCESSING"
                                                micTestMessage = "Processing recording buffer..."
                                            },
                                            onPlaybackStart = {
                                                micTestState = "PLAYBACK"
                                                micTestMessage = "🔊 PLAYING BACK... Listen carefully to see if you hear your own voice."
                                            },
                                            onPlaybackEnd = {
                                                micTestState = "IDLE"
                                                micTestMessage = "Loopback complete. If you heard your voice, mic hardware is fully working! If silent, your car's USB CarPlay feed is blocking microphone access."
                                            },
                                            onError = { err ->
                                                micTestState = "ERROR"
                                                micTestMessage = err
                                            }
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when (micTestState) {
                                            "RECORDING" -> WarningYellow
                                            "PLAYBACK" -> SuccessGreen
                                            else -> CyanGlow
                                        },
                                        contentColor = CarBlack
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = micTestState == "IDLE" || micTestState == "ERROR",
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        text = when (micTestState) {
                                            "RECORDING" -> "Recording..."
                                            "PLAYBACK" -> "Playing..."
                                            else -> "Start 3s Test Loop"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = when (micTestState) {
                                        "RECORDING" -> "🔴 LIVE RECORDING"
                                        "PLAYBACK" -> "🟢 PLAYBACK ACTIVE"
                                        else -> "READY"
                                    },
                                    color = when (micTestState) {
                                        "RECORDING" -> WarningYellow
                                        "PLAYBACK" -> SuccessGreen
                                        else -> MutedText
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CarBlack, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = micTestMessage,
                                    color = if (micTestState == "ERROR") WarningYellow else LightText,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    // --- SECTION 2: SETTINGS & BYPASS METHODS ---
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkGray),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Settings, "Bypasses", tint = CyanGlow, modifier = Modifier.size(18.dp))
                                Text("SYSTEM BYPASS CONTROLS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LightText)
                            }

                            Text(
                                "Ambrane boxes run custom, heavily modified Android system firmware. Some settings activities are disabled or removed entirely from the partition. Click a bypass below to search for a route:",
                                fontSize = 10.sp,
                                color = MutedText
                            )

                            val bypassOptions = remember {
                                listOf(
                                    Triple(
                                        "1. Standard Settings",
                                        Intent(Settings.ACTION_SETTINGS),
                                        "Open main system configurations."
                                    ),
                                    Triple(
                                        "2. Standard Accessibility",
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                                        "Enable VoiceKeyService for steering controls and ad-skipping."
                                    ),
                                    Triple(
                                        "3. Explicit Main Route",
                                        Intent().apply { setClassName("com.android.settings", "com.android.settings.Settings\$AccessibilitySettingsActivity") },
                                        "Direct package launcher for core settings."
                                    ),
                                    Triple(
                                        "4. Automotive Settings Overlay",
                                        Intent("android.settings.car.EXTRA_SETTINGS"),
                                        "Standard setting UI for Car units."
                                    ),
                                    Triple(
                                        "5. Chinese Headunit Settings (Syu)",
                                        Intent().apply { setClassName("com.syu.settings", "com.syu.settings.MainActivity") },
                                        "Bypasses restrictions on MTK/Syu systems."
                                    )
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                bypassOptions.forEach { (title, intent, description) ->
                                    val isSupported = remember(intent) {
                                        try {
                                            context.packageManager.resolveActivity(intent, 0) != null
                                        } catch (e: Exception) {
                                            false
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(CarBlack, RoundedCornerShape(8.dp))
                                            .clickable {
                                                try {
                                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    micTestMessage = "Failed to launch $title: ${e.localizedMessage}"
                                                }
                                            }
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(0.7f)) {
                                            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightText)
                                            Text(description, fontSize = 9.sp, color = MutedText)
                                        }
                                        Text(
                                            text = if (isSupported) "AVAILABLE" else "NOT FOUND",
                                            color = if (isSupported) SuccessGreen else Color.Gray,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(
                                                    if (isSupported) SuccessGreen.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.1f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = DarkGray, thickness = 1.dp)

                    Text(
                        "Note: If all setting bypasses show [NOT FOUND], accessibility services are fully locked out by the Ambrane manufacturer. Use the onscreen Voice button and typed-input fallback instead.",
                        fontSize = 10.sp,
                        color = WarningYellow,
                        lineHeight = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSetupDialog = false }) {
                    Text("Close", color = CyanGlow, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
