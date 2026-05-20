package com.example.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.model.Emotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isPlaying.update { true }
                }
                override fun onDone(utteranceId: String?) {
                    _isPlaying.update { false }
                }
                override fun onError(utteranceId: String?) {
                    _isPlaying.update { false }
                }
            })
        }
    }

    fun setLanguage(lang: String) {
        val locale = when (lang) {
            "Spanish" -> Locale.Builder().setLanguage("es").build()
            "French" -> Locale.Builder().setLanguage("fr").build()
            "German" -> Locale.Builder().setLanguage("de").build()
            "Italian" -> Locale.Builder().setLanguage("it").build()
            "Japanese" -> Locale.Builder().setLanguage("ja").build()
            "Korean" -> Locale.Builder().setLanguage("ko").build()
            "Portuguese" -> Locale.Builder().setLanguage("pt").build()
            "Chinese" -> Locale.Builder().setLanguage("zh").build()
            else -> Locale.US
        }
        tts?.language = locale
    }

    fun setVoiceProfile(voiceId: String) {
        // Fallback simulation for distinct voices using pitch modifiers
        val pitch = when (voiceId) {
            "nova" -> 1.2f
            "alloy" -> 1.0f
            "echo" -> 0.8f
            "fable" -> 1.1f
            "onyx" -> 0.6f
            "shimmer" -> 1.4f
            else -> 1.0f
        }
        tts?.setPitch(pitch)
    }

    fun speak(text: String, emotion: Emotion, customSpeed: Float) {
        if (!isInitialized) return
        
        // Emotion provides a base speed modifier, customSpeed scales it further.
        val finalSpeed = emotion.speed * customSpeed
        tts?.setSpeechRate(finalSpeed)
        
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "auditext_utterance")
    }

    fun stop() {
        tts?.stop()
        _isPlaying.update { false }
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
