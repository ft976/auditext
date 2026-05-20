package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.model.Emotion
import com.example.model.Voice
import com.example.model.Quote
import com.example.ui.theme.*
import com.example.viewmodel.StudioViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val text by viewModel.text.collectAsStateWithLifecycle()
    val emotion by viewModel.emotion.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()

    var quoteIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auditext Studio", fontWeight = FontWeight.Bold, color = SoftWhite) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface),
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History", tint = SoftWhite)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = SoftWhite)
                    }
                }
            )
        },
        containerColor = CharcoalBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Status Card
            item {
                StatusCard(status = status, onStop = { viewModel.stopPlaying() })
            }

            // Script Input
            item {
                ScriptInput(
                    text = text,
                    onTextChange = { viewModel.setText(it) }
                )
            }

            // Controls Strip
            item {
                ControlsStrip(
                    status = status,
                    textEmpty = text.isEmpty(),
                    speed = speed,
                    onGenerate = { viewModel.generateAndSpeak() },
                    onStop = { viewModel.stopPlaying() },
                    onSpeedChange = { viewModel.setSpeed(it) }
                )
            }

            // Emotion Grid
            item {
                Text("Emotion", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(EMOTIONS) { emo ->
                        EmotionChip(
                            emotion = emo,
                            isSelected = emotion == emo.key,
                            onClick = { viewModel.setEmotion(emo.key) }
                        )
                    }
                }
            }

            // Voice Profiles
            item {
                Text("Voice", color = SoftWhite, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    VOICES.forEach { v ->
                        VoiceCard(
                            voiceProfile = v,
                            isSelected = voice == v.id,
                            onClick = { viewModel.setVoice(v.id) }
                        )
                    }
                }
            }

            // Language Setup
            item {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = language,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Language", color = OffWhite) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CharcoalSurfaceVariant,
                            unfocusedContainerColor = CharcoalSurfaceVariant,
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite
                        ),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(CharcoalSurface)
                    ) {
                        LANGUAGES.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption, color = SoftWhite) },
                                onClick = {
                                    viewModel.setLanguage(selectionOption)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Quotes
            item {
                QuoteCard(
                    quote = QUOTES[quoteIndex],
                    onNext = { quoteIndex = (quoteIndex + 1) % QUOTES.size }
                )
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun StatusCard(status: String, onStop: () -> Unit) {
    val isPlaying = status == "playing"
    
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = if (isPlaying) 1.2f else 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Status: ${status.replaceFirstChar { it.uppercase() }}",
                    color = SoftWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                if (isPlaying) {
                    Text("Audio playing...", color = AccentBlue, fontSize = 14.sp)
                }
            }
            
            if (isPlaying) {
                // simple waveform sim
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height((24 * scale * (it + 1)).dp.coerceAtMost(40.dp))
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentBlue)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScriptInput(text: String, onTextChange: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Enter your text here...", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CharcoalSurfaceVariant,
                unfocusedContainerColor = CharcoalSurfaceVariant,
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = SoftWhite,
                unfocusedTextColor = SoftWhite
            ),
            shape = RoundedCornerShape(16.dp),
            maxLines = 10
        )
        Text(
            text = "${text.length} / 4096",
            color = if (text.length >= 4096) ErrorRed else Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 4.dp, end = 8.dp)
        )
    }
}

@Composable
fun ControlsStrip(
    status: String,
    textEmpty: Boolean,
    speed: Float,
    onGenerate: () -> Unit,
    onStop: () -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status == "playing") {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                        Spacer(Modifier.width(8.dp))
                        Text("Stop Audio")
                    }
                } else {
                    Button(
                        onClick = onGenerate,
                        enabled = !textEmpty && status != "generating",
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(if (status == "generating") Icons.Default.Sync else Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (status == "generating") "Generating..." else "Generate & Speak")
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = { /* Simulated download */ }) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = OffWhite)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speed: ${speed}x", color = OffWhite, modifier = Modifier.width(90.dp))
                IconButton(onClick = { onSpeedChange(speed - 0.1f) }) {
                    Icon(Icons.Default.Remove, tint = SoftWhite, contentDescription = "-")
                }
                Slider(
                    value = speed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentPurple,
                        activeTrackColor = AccentBlue,
                        inactiveTrackColor = CharcoalSurface
                    )
                )
                IconButton(onClick = { onSpeedChange(speed + 0.1f) }) {
                    Icon(Icons.Default.Add, tint = SoftWhite, contentDescription = "+")
                }
            }
        }
    }
}

@Composable
fun EmotionChip(emotion: Emotion, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) AccentPurple else CharcoalSurfaceVariant
    val contentColor = if (isSelected) Color.White else OffWhite

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .shadow(if (isSelected) 8.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(emotion.label, color = contentColor, fontWeight = FontWeight.Bold)
            Text(emotion.description, color = contentColor.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

@Composable
fun VoiceCard(voiceProfile: Voice, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if(isSelected) CharcoalSurface else CharcoalSurfaceVariant),
        border = if (isSelected) BorderStroke(2.dp, voiceProfile.color) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(voiceProfile.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = voiceProfile.color)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(voiceProfile.name, fontWeight = FontWeight.Bold, color = SoftWhite, fontSize = 18.sp)
                Text(voiceProfile.description, color = OffWhite, fontSize = 14.sp)
            }
            Surface(
                color = CharcoalBackground,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = voiceProfile.gender, 
                    color = Color.Gray, 
                    fontSize = 12.sp, 
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun QuoteCard(quote: Quote, onNext: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalSurfaceVariant),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.FormatQuote, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "\"${quote.text}\"",
                color = SoftWhite,
                fontSize = 16.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("- ${quote.author}", color = OffWhite, fontWeight = FontWeight.Bold)
                TextButton(onClick = onNext) {
                    Text("Next Quote", color = AccentPurple)
                }
            }
        }
    }
}
