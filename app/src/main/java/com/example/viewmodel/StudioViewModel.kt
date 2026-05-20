package com.example.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.model.Emotion
import com.example.tts.TtsManager
import android.media.MediaPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType

val Context.dataStore by preferencesDataStore(name = "settings")

class StudioViewModel(
    private val historyDao: HistoryDao,
    private val context: Context
) : ViewModel() {

    private val ttsManager = TtsManager(context)

    private val _text = MutableStateFlow("")
    val text = _text.asStateFlow()

    private val _language = MutableStateFlow("English")
    val language = _language.asStateFlow()

    private val _voice = MutableStateFlow(VOICES[0].id)
    val voice = _voice.asStateFlow()

    private val _emotion = MutableStateFlow(EMOTIONS[0].key)
    val emotion = _emotion.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed = _speed.asStateFlow()

    private val _provider = MutableStateFlow("Native (Offline)")
    val provider = _provider.asStateFlow()

    private val _status = MutableStateFlow("idle") // idle, generating, playing
    val status = _status.asStateFlow()

    private val _lastGeneratedItem = MutableStateFlow<HistoryEntity?>(null)
    val lastGeneratedItem = _lastGeneratedItem.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting = _isImporting.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackTickerJob: Job? = null

    private val _playbackPosition = MutableStateFlow(0)
    val playbackPosition = _playbackPosition.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0)
    val playbackDuration = _playbackDuration.asStateFlow()

    private val _isAudioPlaying = MutableStateFlow(false)
    val isAudioPlaying = _isAudioPlaying.asStateFlow()

    private val _isAnalyzingEmotion = MutableStateFlow(false)
    val isAnalyzingEmotion = _isAnalyzingEmotion.asStateFlow()

    val history = historyDao.getAllHistory()
    val downloadedHistory = historyDao.getDownloadedHistory()

    private val HAS_SEEN_WELCOME = androidx.datastore.preferences.core.booleanPreferencesKey("has_seen_welcome")
    
    private val _hasSeenWelcome = MutableStateFlow(false)
    val hasSeenWelcome = _hasSeenWelcome.asStateFlow()

    private val THEME_MODE = androidx.datastore.preferences.core.stringPreferencesKey("theme_mode")
    
    private val _themeMode = MutableStateFlow("System")
    val themeMode = _themeMode.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()
    
    // Manage all API keys separately
    private val _apiKeys = MutableStateFlow<Map<String, String>>(emptyMap())

    val currentApiKey = combine(_provider, _apiKeys) { providerName, keysMap ->
        keysMap[providerName] ?: ""
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        viewModelScope.launch {
            ttsManager.isPlaying.collect { playing ->
                if (playing) {
                    _status.update { "playing" }
                } else if (_status.value == "playing") {
                    _status.update { "idle" }
                }
            }
        }
        viewModelScope.launch {
            context.dataStore.data.collect { prefs ->
                _hasSeenWelcome.value = prefs[HAS_SEEN_WELCOME] ?: false
                _themeMode.value = prefs[THEME_MODE] ?: "System"
                
                val keys = mutableMapOf<String, String>()
                PROVIDERS.forEach { p ->
                    keys[p] = prefs[stringPreferencesKey("api_key_$p")] ?: ""
                }
                _apiKeys.value = keys
                _isReady.value = true
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[THEME_MODE] = mode
            }
            _themeMode.value = mode
        }
    }

    fun completeWelcome() {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[HAS_SEEN_WELCOME] = true
            }
            _hasSeenWelcome.value = true
        }
    }

    fun setText(newText: String) {
        if (newText.length <= 4096) _text.value = newText
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        ttsManager.setLanguage(lang)
    }

    fun setVoice(voiceId: String) {
        _voice.value = voiceId
    }

    fun setEmotion(emotionKey: String) {
        _emotion.value = emotionKey
        val em = EMOTIONS.find { it.key == emotionKey }
        em?.let { emotionObj -> _speed.update { emotionObj.speed } }
    }

    fun setSpeed(newSpeed: Float) {
        _speed.value = (Math.round(newSpeed * 100.0) / 100.0).toFloat().coerceIn(0.5f, 2.0f)
    }

    fun setProvider(p: String) {
        _provider.value = p
    }

    private val _isValidating = MutableStateFlow(false)
    val isValidating = _isValidating.asStateFlow()

    fun validateAndSaveApiKey(key: String, onComplete: (Boolean, String) -> Unit) {
        val currentProvider = _provider.value
        if (key.isEmpty()) {
            onComplete(false, "Key cannot be empty")
            return
        }

        _isValidating.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val isValid = try {
                when (currentProvider) {
                    "Gemini" -> {
                        val client = OkHttpClient()
                        val request = Request.Builder()
                            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
                            .build()
                        client.newCall(request).execute().use { response ->
                            response.isSuccessful
                        }
                    }
                    else -> true // For others assume valid for now or implement as needed
                }
            } catch (e: Exception) {
                false
            }

            withContext(Dispatchers.Main) {
                _isValidating.value = false
                if (isValid) {
                    saveApiKey(key)
                    onComplete(true, "Key validated and saved successfully!")
                } else {
                    onComplete(false, "Invalid API key for $currentProvider. Please check and try again.")
                }
            }
        }
    }

    fun saveApiKey(key: String) {
        val currentProvider = _provider.value
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[stringPreferencesKey("api_key_$currentProvider")] = key
            }
        }
    }

    fun generateAndSpeak() {
        if (_text.value.isEmpty()) return
        
        val currentProvider = _provider.value
        val hasKey = currentApiKey.value.isNotEmpty() || (currentProvider == "Gemini" && com.example.BuildConfig.GEMINI_API_KEY.isNotEmpty() && com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY")

        if (currentProvider != "Native (Offline)" && !hasKey) {
            android.widget.Toast.makeText(context, "$currentProvider API Key is missing. Configure in settings.", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        stopPlayback()
        _status.update { "generating" }

        viewModelScope.launch {
            var finalEmotionKey = _emotion.value

            // AI Emotion detection if Gemini is available
            if (currentProvider == "Gemini" || hasKey) {
                val apiKey = if (currentApiKey.value.isNotEmpty()) currentApiKey.value else com.example.BuildConfig.GEMINI_API_KEY
                if (apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY") {
                    _isAnalyzingEmotion.value = true
                    finalEmotionKey = detectEmotionViaAI(_text.value, apiKey)
                    _isAnalyzingEmotion.value = false
                    _emotion.value = finalEmotionKey // Update UI to show AI's choice
                    delay(500)
                }
            }

            val newItem = HistoryEntity(
                id = UUID.randomUUID().toString(),
                text = _text.value,
                voice = _voice.value,
                emotion = finalEmotionKey,
                language = _language.value,
                provider = currentProvider,
                timestamp = System.currentTimeMillis()
            )
            
            historyDao.insertHistory(newItem)
            _lastGeneratedItem.value = newItem
            
            if (currentProvider != "Native (Offline)") {
                val keyPreview = if (currentApiKey.value.isNotEmpty()) currentApiKey.value.take(4) else com.example.BuildConfig.GEMINI_API_KEY.take(4)
                android.widget.Toast.makeText(context, "Generating via $currentProvider (Emotion: ${finalEmotionKey.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }})", android.widget.Toast.LENGTH_SHORT).show()
                delay(500)
            }

            val tempFile = java.io.File(context.cacheDir, "last_generated_audio.wav")
            val selectedEmotion = EMOTIONS.find { it.key == finalEmotionKey } ?: EMOTIONS[0]
            
            ttsManager.downloadToFile(_text.value, selectedEmotion, _speed.value, tempFile, newItem.id) { success ->
                if (success) {
                    viewModelScope.launch(Dispatchers.Main) {
                        try {
                            mediaPlayer?.release()
                            mediaPlayer = MediaPlayer().apply {
                                if (tempFile.exists() && tempFile.length() > 0) {
                                    setDataSource(tempFile.absolutePath)
                                    prepareAsync()
                                    setOnPreparedListener {
                                        _playbackDuration.value = duration
                                        start()
                                        _isAudioPlaying.value = true
                                        _status.value = "playing"
                                    }
                                    setOnCompletionListener {
                                        _isAudioPlaying.value = false
                                        _status.value = "idle"
                                        stopTicker()
                                    }
                                    setOnErrorListener { _, what, extra ->
                                        android.util.Log.e("StudioViewModel", "MediaPlayer Error: $what, $extra")
                                        _status.value = "idle"
                                        false
                                    }
                                } else {
                                    _status.value = "idle"
                                    android.widget.Toast.makeText(context, "Audio file not found or empty", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            startTicker()
                        } catch (e: Exception) {
                            _status.value = "idle"
                            android.widget.Toast.makeText(context, "Error playing audio", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    _status.value = "idle"
                    android.widget.Toast.makeText(context, "Failed to generate audio", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun detectEmotionViaAI(text: String, apiKey: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val emotionKeys = EMOTIONS.joinToString(", ") { it.key }
                val prompt = "Analyze the text and choose the best emotion for TTS. Respond ONLY with the lowercase key: $emotionKeys.\n\nText: \"$text\""
                
                // Escape quotes for JSON manually to avoid Gson dependency
                val escapedPrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n")
                
                val json = """
                    {
                      "contents": [{
                        "parts": [{ "text": "$escapedPrompt" }]
                      }]
                    }
                """.trimIndent()
                
                val body = okhttp3.RequestBody.create(
                    "application/json; charset=utf-8".toMediaType(),
                    json
                )
                
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                    .post(body)
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        // Extract text using simple pattern matching
                        val regex = "\"text\":\\s*\"([^\"]+)\"".toRegex()
                        val match = regex.find(responseBody)
                        val detectedText = match?.groupValues?.get(1)?.trim()?.lowercase() ?: "neutral"
                        
                        if (EMOTIONS.any { it.key == detectedText }) {
                            detectedText
                        } else {
                            "neutral"
                        }
                    } else {
                        "neutral"
                    }
                }
            } catch (e: Exception) {
                "neutral"
            }
        }
    }

    private fun startTicker() {
        playbackTickerJob?.cancel()
        playbackTickerJob = viewModelScope.launch {
            while (true) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        _playbackPosition.value = it.currentPosition
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopTicker() {
        playbackTickerJob?.cancel()
        playbackTickerJob = null
    }

    fun togglePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isAudioPlaying.value = false
                _status.value = "idle"
                stopTicker()
            } else {
                it.start()
                _isAudioPlaying.value = true
                _status.value = "playing"
                startTicker()
            }
        }
    }

    fun stopPlayback() {
        ttsManager.stop()
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        _isAudioPlaying.value = false
        _status.value = "idle"
        _playbackPosition.value = 0
        stopTicker()
    }

    fun stopPlaying() {
        stopPlayback()
    }
    
    fun deleteHistoryItem(id: String) {
        viewModelScope.launch { historyDao.deleteHistory(id) }
    }

    fun restoreHistoryItem(item: HistoryEntity) {
        _text.value = item.text
        setLanguage(item.language)
        setVoice(item.voice)
        setEmotion(item.emotion)
        setProvider(item.provider)
    }

    fun downloadAudio(item: HistoryEntity) {
        viewModelScope.launch {
            val fileName = "Auditext_${item.id.take(8)}_${System.currentTimeMillis()}.wav"
            val tempFile = java.io.File(context.cacheDir, fileName)
            
            val selectedEmotion = EMOTIONS.find { it.key == item.emotion } ?: EMOTIONS[0]
            
            val result = ttsManager.downloadToFile(item.text, selectedEmotion, _speed.value, tempFile, item.id)
            
            if (result == android.speech.tts.TextToSpeech.SUCCESS) {
                val publicUri = saveFileToPublicDownloads(tempFile, fileName)
                if (publicUri != null) {
                    val updatedItem = item.copy(
                        isDownloaded = true,
                        localFilePath = publicUri.toString()
                    )
                    historyDao.insertHistory(updatedItem)
                    android.widget.Toast.makeText(context, "Audio saved to Downloads!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "Failed to save to public storage.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                android.widget.Toast.makeText(context, "Failed to download audio.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun saveFileToPublicDownloads(tempFile: java.io.File, fileName: String): android.net.Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                }
                
                val resolver = context.contentResolver
                val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                } else {
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                
                val uri = resolver.insert(collection, contentValues)
                
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    tempFile.delete()
                }
                uri
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun importTextFromFile(uri: android.net.Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    }
                }
                if (content != null) {
                    setText(content)
                    android.widget.Toast.makeText(context, "Document imported!", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to read file", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                _isImporting.value = false
            }
        }
    }

    override fun onCleared() {
        ttsManager.shutdown()
        mediaPlayer?.release()
        stopTicker()
        super.onCleared()
    }
}

class StudioViewModelFactory(
    private val historyDao: HistoryDao,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudioViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StudioViewModel(historyDao, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
