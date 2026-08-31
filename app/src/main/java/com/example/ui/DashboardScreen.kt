package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

// Simple Palette
val CarBlack = Color(0xFF0C0C0F)
val CarObsidian = Color(0xFF141419)
val CyanGlow = Color(0xFF00E5FF)
val AssistantPurple = Color(0xFFB066FF)
val AssistantBlue = Color(0xFF4285F4)
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

    var assistantState by remember { mutableStateOf(AssistantState.IDLE) }
    var voiceTranscript by remember { mutableStateOf("") }
    var commandInputText by remember { mutableStateOf("") }
    var geminiReplyText by remember { mutableStateOf("Ready to receive voice commands. Press the button or use steering controls.") }
    var permissionsGranted by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        permissionsGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.DirectionsCar, "Car Logo", tint = CyanGlow, modifier = Modifier.size(24.dp))
                    Text("CAR LINK AI ASSISTANT", color = LightText, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (permissionsGranted) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Mic Indicator",
                        tint = if (permissionsGranted) CyanGlow else MutedText,
                        modifier = Modifier.size(24.dp)
                    )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CarBlack)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Main Mic Button
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(DarkGray)
                    .clickable { triggerVoiceListening() }
                    .testTag("voice_trigger_button"),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (assistantState == AssistantState.LISTENING) 1.5f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                Box(
                    modifier = Modifier
                        .size(100.dp)
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
                        contentDescription = "Mic State",
                        tint = CarBlack,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (voiceTranscript.isNotBlank()) "\"$voiceTranscript\"" else "Awaiting your voice command...",
                color = if (assistantState == AssistantState.LISTENING) CyanGlow else MutedText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = geminiReplyText,
                color = if (assistantState == AssistantState.SPEAKING) CyanGlow else LightText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Simplified setup buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        } catch (e: Exception) {
                            // Do nothing
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkGray, contentColor = LightText)
                ) {
                    Text("1. Open Device Settings")
                }
                
                Button(
                    onClick = {
                        try {
                            // Using a direct component intent for common Android TV/Car boxes if generic fails
                            val intent = Intent().apply {
                                setClassName("com.android.settings", "com.android.settings.Settings\$AccessibilitySettingsActivity")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } catch (e2: Exception) {
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanGlow, contentColor = CarBlack)
                ) {
                    Text("2. Open Accessibility (For Wheel Fix)")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = commandInputText,
                    onValueChange = { commandInputText = it },
                    placeholder = { Text("Type voice command (fallback)...", fontSize = 14.sp, color = MutedText) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    textStyle = TextStyle(fontSize = 14.sp, color = LightText),
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
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(CyanGlow)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Command",
                        tint = CarBlack,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    if (showSetupDialog) {
        androidx.compose.material3.AlertDialog(
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
                        "Ambrane devices often hijack standard Android settings. Use these buttons to force-open the menus.",
                        fontSize = 12.sp,
                        color = MutedText
                    )
                    androidx.compose.material3.Button(
                        onClick = {
                            try {
                                val intent = Intent()
                                intent.setClassName("com.android.settings", "com.android.settings.Settings\$AccessibilitySettingsActivity")
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                } catch(e2: Exception) {}
                            }
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CyanGlow, contentColor = CarBlack),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1. Force Hidden Accessibility")
                    }
                    androidx.compose.material3.Button(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            } catch (e: Exception) {}
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DarkGray, contentColor = LightText),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("2. Open Main Settings")
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showSetupDialog = false }) {
                    Text("Close", color = CyanGlow)
                }
            }
        )
    }
}
