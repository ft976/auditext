package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.painterResource
import com.example.R
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
    onNavigateToHistory: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val text by viewModel.text.collectAsStateWithLifecycle()
    val emotion by viewModel.emotion.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val lastGeneratedItem by viewModel.lastGeneratedItem.collectAsStateWithLifecycle()
    val playbackPosition by viewModel.playbackPosition.collectAsStateWithLifecycle()
    val playbackDuration by viewModel.playbackDuration.collectAsStateWithLifecycle()
    val isAudioPlaying by viewModel.isAudioPlaying.collectAsStateWithLifecycle()
    val isAnalyzingEmotion by viewModel.isAnalyzingEmotion.collectAsStateWithLifecycle()

    var quoteIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val linkedInUrl = "https://www.linkedin.com/in/rehan-ahmad-863386382?utm_source=share_via&utm_content=profile&utm_medium=member_android"

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importTextFromFile(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Auditext", 
                        fontWeight = FontWeight.ExtraBold, 
                        color = Color.White,
                        letterSpacing = 1.sp
                    ) 
                },
                navigationIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.auditext_icon_ultra_bold_1779298671594),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        tint = Color.Unspecified
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DashboardPurple
                ),
                actions = {
                    IconButton(onClick = onNavigateToAbout) {
                        Icon(Icons.Default.Info, contentDescription = "About", tint = SoftWhite)
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.History, contentDescription = "History", tint = SoftWhite)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = SoftWhite)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                PlaybackCard(
                    status = status,
                    duration = playbackDuration,
                    position = playbackPosition,
                    isPlaying = isAudioPlaying,
                    isAnalyzingEmotion = isAnalyzingEmotion,
                    onTogglePlay = { viewModel.togglePlayback() },
                    onStop = { viewModel.stopPlaying() }
                )
            }

            // Script Header with Document Support
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Script", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    TextButton(
                        onClick = { filePicker.launch("text/plain") },
                        enabled = !isImporting
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isImporting) "Importing..." else "Import .txt", color = AccentBlue)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
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
                    isAudioPlaying = isAudioPlaying,
                    onGenerate = { viewModel.generateAndSpeak() },
                    onTogglePlay = { viewModel.togglePlayback() },
                    onStop = { viewModel.stopPlaying() },
                    onSpeedChange = { viewModel.setSpeed(it) },
                    onDownload = {
                        lastGeneratedItem?.let { viewModel.downloadAudio(it) }
                            ?: android.widget.Toast.makeText(context, "Generate audio first to download", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Emotion Grid
            item {
                Text("Emotion", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
                Text("Voice", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
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
                        label = { Text("Language", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        LANGUAGES.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption, color = MaterialTheme.colorScheme.onSurface) },
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
            
            // Footer with LinkedIn Link
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
                    Text(
                        "Created by Rehan Ahmad",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Connect on LinkedIn",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(linkedInUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Cannot open link", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(4.dp)
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun PlaybackCard(
    status: String,
    duration: Int,
    position: Int,
    isPlaying: Boolean,
    isAnalyzingEmotion: Boolean,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit
) {
    val progress = if (duration > 0) position.toFloat() / duration else 0f
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isAnalyzingEmotion -> "Analyzing Sentiment..."
                            status == "generating" -> "Generating Audio..."
                            else -> "Studio Playback"
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = if (isAnalyzingEmotion) "AI is choosing emotional tone" else status.replaceFirstChar { it.uppercase() },
                        color = if (status == "playing" || isAnalyzingEmotion) AccentBlue else Color.Gray,
                        fontSize = 14.sp
                    )
                }
                
                if (status == "generating" || isAnalyzingEmotion) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentBlue, strokeWidth = 2.dp)
                } else if (duration > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onTogglePlay) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = AccentBlue
                            )
                        }
                        IconButton(onClick = onStop) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = ErrorRed)
                        }
                    }
                }
            }
            
            if (duration > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = AccentBlue,
                    trackColor = MaterialTheme.colorScheme.surface
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(position), color = Color.Gray, fontSize = 12.sp)
                    Text(formatDuration(duration), color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

fun formatDuration(ms: Int): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
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
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
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
    isAudioPlaying: Boolean,
    onGenerate: () -> Unit,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onDownload: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status == "playing" || (status == "idle" && isAudioPlaying)) {
                    Button(
                        onClick = onTogglePlay,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(if (isAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isAudioPlaying) "Pause" else "Resume")
                    }
                } else {
                    Button(
                        onClick = onGenerate,
                        enabled = !textEmpty && status != "generating",
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        if (status == "generating") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (status == "generating") "Generating..." else "Generate & Preview")
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speed: ${speed}x", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
                IconButton(onClick = { onSpeedChange(speed - 0.1f) }) {
                    Icon(Icons.Default.Remove, tint = MaterialTheme.colorScheme.onSurface, contentDescription = "-")
                }
                Slider(
                    value = speed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2.0f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentPurple,
                        activeTrackColor = AccentBlue,
                        inactiveTrackColor = MaterialTheme.colorScheme.surface
                    )
                )
                IconButton(onClick = { onSpeedChange(speed + 0.1f) }) {
                    Icon(Icons.Default.Add, tint = MaterialTheme.colorScheme.onSurface, contentDescription = "+")
                }
            }
        }
    }
}

@Composable
fun EmotionChip(emotion: Emotion, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) AccentPurple else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

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
        colors = CardDefaults.cardColors(containerColor = if(isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant),
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
                Text(voiceProfile.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
                Text(voiceProfile.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            Surface(
                color = MaterialTheme.colorScheme.background,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.FormatQuote, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "\"${quote.text}\"",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("- ${quote.author}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                TextButton(onClick = onNext) {
                    Text("Next Quote", color = AccentPurple)
                }
            }
        }
    }
}
