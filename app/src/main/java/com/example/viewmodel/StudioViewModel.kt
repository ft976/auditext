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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
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
        ttsManager.setVoiceProfile(voiceId)
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
                val client = OkHttpClient()
                when (currentProvider) {
                    "Gemini" -> {
                        val request = Request.Builder()
                            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
                            .build()
                        client.newCall(request).execute().use { response ->
                            response.isSuccessful
                        }
                    }
                    "OpenAI" -> {
                        val request = Request.Builder()
                            .url("https://api.openai.com/v1/models")
                            .header("Authorization", "Bearer $key")
                            .build()
                        client.newCall(request).execute().use { response ->
                            response.isSuccessful
                        }
                    }
                    "ElevenLabs" -> {
                        val request = Request.Builder()
                            .url("https://api.elevenlabs.io/v1/user")
                            .header("xi-api-key", key)
                            .build()
                        client.newCall(request).execute().use { response ->
                            response.isSuccessful
                        }
                    }
                    "Deepgram" -> {
                        val request = Request.Builder()
                            .url("https://api.deepgram.com/v1/projects")
                            .header("Authorization", "Token $key")
                            .build()
                        client.newCall(request).execute().use { response ->
                            response.isSuccessful
                        }
                    }
                    "Cartesia" -> {
                        val request = Request.Builder()
                            .url("https://api.cartesia.ai/voices")
                            .header("X-API-Key", key)
                            .header("Cartesia-Version", "2024-06-10")
                            .build()
                        client.newCall(request).execute().use { response ->
                            response.isSuccessful
                        }
                    }
                    else -> true
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
            
            val success = synthesizeAudio(
                text = _text.value,
                voiceId = newItem.voice,
                emotion = selectedEmotion,
                speed = _speed.value,
                languageName = _language.value,
                tempFile = tempFile,
                utteranceId = newItem.id
            )
            
            withContext(Dispatchers.Main) {
                if (success) {
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            if (tempFile.exists() && tempFile.length() > 0) {
                                setDataSource(tempFile.absolutePath)
                                prepareAsync()
                                setOnPreparedListener {
                                    _playbackDuration.value = duration
                                    try {
                                        start()
                                        _isAudioPlaying.value = true
                                        _status.value = "playing"
                                    } catch (e: Exception) {
                                        _status.value = "idle"
                                    }
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
                } else {
                    _status.value = "idle"
                    android.widget.Toast.makeText(context, "Failed to generate audio", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getLangCode(lang: String): String {
        return when (lang) {
            "Spanish" -> "es-ES"
            "French" -> "fr-FR"
            "German" -> "de-DE"
            "Italian" -> "it-IT"
            "Japanese" -> "ja-JP"
            "Korean" -> "ko-KR"
            "Portuguese" -> "pt-PT"
            "Chinese" -> "zh-CN"
            "Hindi" -> "hi-IN"
            else -> "en-US"
        }
    }

    private fun escapeJsonString(str: String): String {
        val builder = java.lang.StringBuilder()
        builder.append("\"")
        for (c in str) {
            when (c) {
                '\"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '/' -> builder.append("\\/")
                '\b' -> builder.append("\\b")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (c < ' ') {
                        val t = "000" + Integer.toHexString(c.code)
                        builder.append("\\u").append(t.substring(t.length - 4))
                    } else {
                        builder.append(c)
                    }
                }
            }
        }
        builder.append("\"")
        return builder.toString()
    }

    private suspend fun synthesizeAudio(
        text: String,
        voiceId: String,
        emotion: Emotion,
        speed: Float,
        languageName: String,
        tempFile: java.io.File,
        utteranceId: String
    ): Boolean {
        val currentProvider = _provider.value
        val hasKey = currentApiKey.value.isNotEmpty() || (currentProvider == "Gemini" && com.example.BuildConfig.GEMINI_API_KEY.isNotEmpty() && com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY")
        
        if (currentProvider == "Native (Offline)" || !hasKey) {
            return suspendCancellableCoroutine { continuation ->
                ttsManager.setLanguage(languageName)
                ttsManager.setVoiceProfile(voiceId)
                val result = ttsManager.downloadToFile(text, emotion, speed, tempFile, utteranceId) { success ->
                    if (continuation.isActive) {
                        continuation.resume(success)
                    }
                }
                if (result != android.speech.tts.TextToSpeech.SUCCESS) {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }
        }
        
        return withContext(Dispatchers.IO) {
            val key = if (currentApiKey.value.isNotEmpty()) currentApiKey.value else com.example.BuildConfig.GEMINI_API_KEY
            try {
                val client = OkHttpClient()
                when (currentProvider) {
                    "OpenAI" -> {
                        val json = """
                            {
                              "model": "tts-1",
                              "input": ${escapeJsonString(text)},
                              "voice": "$voiceId",
                              "response_format": "mp3",
                              "speed": $speed
                            }
                        """.trimIndent()
                        val body = okhttp3.RequestBody.create("application/json; charset=utf-8".toMediaType(), json)
                        val request = Request.Builder()
                            .url("https://api.openai.com/v1/audio/speech")
                            .header("Authorization", "Bearer $key")
                            .post(body)
                            .build()
                        
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body?.byteStream()?.use { input ->
                                    tempFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        }
                    }
                    "ElevenLabs" -> {
                        val voiceMap = mapOf(
                            // Female
                            "alloy" to "sPGBB3cm0SPHjSc019B9", // Grace
                            "nova" to "EXAVITQu4vr4xnSDxMaL", // Nova
                            "shimmer" to "MF3m74ZOqHOdhvCO760Q", // Shimmer
                            // Male
                            "echo" to "pNInz6obpgDaGzMKJOJb", // Adam
                            "fable" to "ErXwobaYiN019PkySvjV", // Antoni
                            "onyx" to "VR6AewLTigWG4xSOukaG" // Arnold
                        )
                        val selectedVoiceId = voiceMap[voiceId] ?: "21m00Tcm4TlvDq8ikWAM"
                        val json = """
                            {
                              "text": ${escapeJsonString(text)},
                              "model_id": "eleven_monolingual_v1",
                              "voice_settings": {
                                "stability": 0.5,
                                "similarity_boost": 0.75
                              }
                            }
                        """.trimIndent()
                        val body = okhttp3.RequestBody.create("application/json; charset=utf-8".toMediaType(), json)
                        val request = Request.Builder()
                            .url("https://api.elevenlabs.io/v1/text-to-speech/$selectedVoiceId")
                            .header("xi-api-key", key)
                            .post(body)
                            .build()
                        
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body?.byteStream()?.use { input ->
                                    tempFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        }
                    }
                    "Deepgram" -> {
                        val voiceMap = mapOf(
                            "alloy" to "aura-asteria-en",
                            "echo" to "aura-orion-en",
                            "fable" to "aura-arcas-en",
                            "onyx" to "aura-perseus-en",
                            "nova" to "aura-stella-en",
                            "shimmer" to "aura-athena-en"
                        )
                        val deepgramVoice = voiceMap[voiceId] ?: "aura-asteria-en"
                        val json = """
                            {
                              "text": ${escapeJsonString(text)}
                            }
                        """.trimIndent()
                        val body = okhttp3.RequestBody.create("application/json; charset=utf-8".toMediaType(), json)
                        val request = Request.Builder()
                            .url("https://api.deepgram.com/v1/speak?model=$deepgramVoice&container=wav")
                            .header("Authorization", "Token $key")
                            .post(body)
                            .build()
                        
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body?.byteStream()?.use { input ->
                                    tempFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        }
                    }
                    "Cartesia" -> {
                        val voiceMap = mapOf(
                            "alloy" to "8254b868-f94d-4886-9a03-7cb52be6ff34",
                            "echo" to "79a0ae9b-008b-4a57-9d7a-b5e1b559281e",
                            "fable" to "29be4ea5-6df3-49a0-9799-a868a2bf6cb9",
                            "onyx" to "69267136-1bdc-411a-a077-42cd2ee403d4",
                            "nova" to "b311202e-fac6-455c-ae86-fcd05b768a29",
                            "shimmer" to "c4cdf86f-dbda-4402-995f-9fac06016147"
                        )
                        val cartesiaVoiceId = voiceMap[voiceId] ?: "8254b868-f94d-4886-9a03-7cb52be6ff34"
                        val json = """
                            {
                              "model_id": "sonic-english",
                              "transcript": ${escapeJsonString(text)},
                              "voice": {
                                "mode": "id",
                                "id": "$cartesiaVoiceId"
                              },
                              "output_format": {
                                "container": "wav",
                                "encoding": "pcm_s16le",
                                "sample_rate": 24000
                              }
                            }
                        """.trimIndent()
                        val body = okhttp3.RequestBody.create("application/json; charset=utf-8".toMediaType(), json)
                        val request = Request.Builder()
                            .url("https://api.cartesia.ai/tts/bytes")
                            .header("X-API-Key", key)
                            .header("Cartesia-Version", "2024-06-10")
                            .post(body)
                            .build()
                        
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                response.body?.byteStream()?.use { input ->
                                    tempFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        }
                    }
                    "Gemini" -> {
                        val voiceMap = mapOf(
                            "alloy" to "en-US-Neural2-A",
                            "echo" to "en-US-Neural2-D",
                            "fable" to "en-GB-Neural2-B",
                            "onyx" to "en-US-Neural2-J",
                            "nova" to "en-US-Neural2-F",
                            "shimmer" to "en-US-Neural2-H"
                        )
                        val voiceName = voiceMap[voiceId] ?: "en-US-Neural2-A"
                        val langCode = getLangCode(languageName)
                        val json = """
                            {
                              "input": { "text": ${escapeJsonString(text)} },
                              "voice": {
                                "languageCode": "$langCode",
                                "name": "$voiceName"
                              },
                              "audioConfig": {
                                "audioEncoding": "MP3"
                              }
                            }
                        """.trimIndent()
                        val body = okhttp3.RequestBody.create("application/json; charset=utf-8".toMediaType(), json)
                        val request = Request.Builder()
                            .url("https://texttospeech.googleapis.com/v1/text:synthesize?key=$key")
                            .post(body)
                            .build()
                        
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val respText = response.body?.string() ?: ""
                                val regex = "\"audioContent\":\\s*\"([^\"]+)\"".toRegex()
                                val match = regex.find(respText)
                                val base64Data = match?.groupValues?.get(1)
                                if (base64Data != null) {
                                    val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                    tempFile.writeBytes(decodedBytes)
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }
                        }
                    }
                    else -> false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
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
                try {
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            _playbackPosition.value = it.currentPosition
                        }
                    }
                } catch (e: Exception) {
                    // Ignore transient exceptions if player is in intermediate state
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
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("StudioViewModel", "Error in togglePlayback", e)
        }
    }

    fun stopPlayback() {
        ttsManager.stop()
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            android.util.Log.e("StudioViewModel", "Error in stopPlayback", e)
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
        _status.update { "generating" }
        viewModelScope.launch {
            val fileName = "Auditext_${item.id.take(8)}_${System.currentTimeMillis()}.wav"
            val tempFile = java.io.File(context.cacheDir, fileName)
            
            val selectedEmotion = EMOTIONS.find { it.key == item.emotion } ?: EMOTIONS[0]
            
            // Temporarily set provider to the historical item's provider for synthesis routing, then restore
            val originalProvider = _provider.value
            _provider.value = item.provider
            
            val success = synthesizeAudio(
                text = item.text,
                voiceId = item.voice,
                emotion = selectedEmotion,
                speed = _speed.value,
                languageName = item.language,
                tempFile = tempFile,
                utteranceId = item.id
            )
            
            _provider.value = originalProvider
            
            withContext(Dispatchers.Main) {
                _status.update { "idle" }
                if (success) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val publicUri = saveFileToPublicDownloads(tempFile, fileName)
                        withContext(Dispatchers.Main) {
                            if (publicUri != null) {
                                val updatedItem = item.copy(
                                    isDownloaded = true,
                                    localFilePath = publicUri.toString()
                                )
                                viewModelScope.launch {
                                    historyDao.insertHistory(updatedItem)
                                }
                                android.widget.Toast.makeText(context, "Audio saved to Downloads!", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, "Failed to save to public storage.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    android.widget.Toast.makeText(context, "Failed to download audio.", android.widget.Toast.LENGTH_SHORT).show()
                }
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
