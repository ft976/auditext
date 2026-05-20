package com.example.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HistoryEntity
import com.example.ui.theme.*
import com.example.viewmodel.StudioViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedDataScreen(
    viewModel: StudioViewModel,
    onBack: () -> Unit
) {
    val downloadedItems by viewModel.downloadedHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    var playingFileId by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloaded Audio", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DashboardPurple
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (downloadedItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FileDownload, 
                        contentDescription = null, 
                        modifier = Modifier.size(64.dp), 
                        tint = Color.Gray
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No downloaded audio yet", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(downloadedItems) { item ->
                    DownloadedAudioCard(
                        item = item,
                        isPlaying = playingFileId == item.id,
                        onPlay = {
                            if (playingFileId == item.id) {
                                mediaPlayer?.stop()
                                playingFileId = null
                            } else {
                                try {
                                    mediaPlayer?.release()
                                    mediaPlayer = MediaPlayer().apply {
                                        val path = item.localFilePath
                                        if (path != null) {
                                            if (path.startsWith("content://")) {
                                                setDataSource(context, android.net.Uri.parse(path))
                                            } else {
                                                setDataSource(path)
                                            }
                                            prepare()
                                            start()
                                            setOnCompletionListener {
                                                playingFileId = null
                                            }
                                        }
                                    }
                                    playingFileId = item.id
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Error playing audio", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDelete = {
                            item.localFilePath?.let { path ->
                                if (path.startsWith("content://")) {
                                    try {
                                        context.contentResolver.delete(android.net.Uri.parse(path), null, null)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                } else {
                                    val file = File(path)
                                    if (file.exists()) file.delete()
                                }
                            }
                            viewModel.deleteHistoryItem(item.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadedAudioCard(
    item: HistoryEntity,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.voice.replaceFirstChar { it.uppercase() } + " - " + item.language,
                    color = AccentPurple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = sdf.format(Date(item.timestamp)),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = item.text,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                maxLines = 3
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed)
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                FilledIconButton(
                    onClick = onPlay,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isPlaying) ErrorRed else AccentBlue
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play"
                    )
                }
            }
        }
    }
}
