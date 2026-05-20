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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

val Context.dataStore by preferencesDataStore(name = "settings")

class StudioViewModel(
    private val historyDao: HistoryDao,
    private val ttsManager: TtsManager,
    private val context: Context
) : ViewModel() {

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

    val history = historyDao.getAllHistory()

    private val API_KEY = stringPreferencesKey("api_key")
    private val _apiKey = MutableStateFlow("")
    val apiKey = _apiKey.asStateFlow()

    init {
        viewModelScope.launch {
            ttsManager.isPlaying.collect { playing ->
                if (playing) {
                    _status.update { "playing" }
                } else {
                    if (_status.value == "playing") _status.update { "idle" }
                }
            }
        }
        viewModelScope.launch {
            val key = context.dataStore.data.first()[API_KEY] ?: ""
            _apiKey.update { key }
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

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[API_KEY] = key
            }
            _apiKey.value = key
        }
    }

    fun generateAndSpeak() {
        if (_text.value.isEmpty()) return
        
        val currentProvider = _provider.value
        val hasKey = _apiKey.value.isNotEmpty() || (currentProvider == "Gemini" && com.example.BuildConfig.GEMINI_API_KEY.isNotEmpty() && com.example.BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY")

        if (currentProvider != "Native (Offline)" && !hasKey) {
            android.widget.Toast.makeText(context, "$currentProvider API Key is missing. Configure in settings.", android.widget.Toast.LENGTH_LONG).show()
            return
        }

        _status.update { "generating" }

        val newItem = HistoryEntity(
            id = UUID.randomUUID().toString(),
            text = _text.value,
            voice = _voice.value,
            emotion = _emotion.value,
            language = _language.value,
            provider = currentProvider,
            timestamp = System.currentTimeMillis()
        )
        
        viewModelScope.launch {
            historyDao.insertHistory(newItem)
            
            if (currentProvider != "Native (Offline)") {
                val keyPreview = if (_apiKey.value.isNotEmpty()) _apiKey.value.take(4) else com.example.BuildConfig.GEMINI_API_KEY.take(4)
                android.widget.Toast.makeText(context, "Generating via $currentProvider (Key: $keyPreview...)", android.widget.Toast.LENGTH_SHORT).show()
                kotlinx.coroutines.delay(1500) // Simulate network delay
            }

            ttsManager.setVoiceProfile(_voice.value)
            val selectedEmotion = EMOTIONS.find { it.key == _emotion.value } ?: EMOTIONS[0]
            ttsManager.speak(_text.value, selectedEmotion, _speed.value)
        }
    }

    fun stopPlaying() {
        ttsManager.stop()
        _status.update { "idle" }
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

    override fun onCleared() {
        ttsManager.shutdown()
        super.onCleared()
    }
}

class StudioViewModelFactory(
    private val historyDao: HistoryDao,
    private val ttsManager: TtsManager,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudioViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StudioViewModel(historyDao, ttsManager, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
