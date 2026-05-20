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

    private val _isSynthesizing = MutableStateFlow(false)
    val isSynthesizing: StateFlow<Boolean> = _isSynthesizing.asStateFlow()

    private var onSynthesisComplete: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId?.startsWith("synth_") == true) {
                        _isSynthesizing.update { true }
                    } else {
                        _isPlaying.update { true }
                    }
                }
                override fun onDone(utteranceId: String?) {
                    if (utteranceId?.startsWith("synth_") == true) {
                        _isSynthesizing.update { false }
                        onSynthesisComplete?.invoke(true)
                        onSynthesisComplete = null
                    } else {
                        _isPlaying.update { false }
                    }
                }
                override fun onError(utteranceId: String?) {
                    if (utteranceId?.startsWith("synth_") == true) {
                        _isSynthesizing.update { false }
                        onSynthesisComplete?.invoke(false)
                        onSynthesisComplete = null
                    } else {
                        _isPlaying.update { false }
                    }
                }
            })
        }
    }

    private var voiceBasePitch = 1.0f

    fun setLanguage(lang: String) {
        if (!isInitialized) return
        val locale = when (lang) {
            "Spanish" -> Locale.Builder().setLanguage("es").build()
            "French" -> Locale.Builder().setLanguage("fr").build()
            "German" -> Locale.Builder().setLanguage("de").build()
            "Italian" -> Locale.Builder().setLanguage("it").build()
            "Japanese" -> Locale.Builder().setLanguage("ja").build()
            "Korean" -> Locale.Builder().setLanguage("ko").build()
            "Portuguese" -> Locale.Builder().setLanguage("pt").build()
            "Chinese" -> Locale.Builder().setLanguage("zh").build()
            "Hindi" -> Locale("hi", "IN")
            else -> Locale.US
        }
        tts?.setLanguage(locale)
    }

    fun setVoiceProfile(voiceId: String) {
        if (!isInitialized) return
        
        // Fallback simulation for distinct voices using pitch modifiers
        voiceBasePitch = when (voiceId) {
            "nova" -> 1.15f
            "alloy" -> 1.0f
            "echo" -> 0.85f
            "fable" -> 1.05f
            "onyx" -> 0.7f
            "shimmer" -> 1.25f
            else -> 1.0f
        }
        tts?.setPitch(voiceBasePitch)

        try {
            // Attempt to select a high-quality human-like voice if available for the current language
            val voices = tts?.voices
            val currentLang = tts?.language
            if (voices != null && currentLang != null) {
                // Find all voices for current language (excluding network connection required to ensure offline/local works)
                val matchingVoices = voices.filter { 
                    it.locale != null && it.locale.language == currentLang.language && !it.isNetworkConnectionRequired 
                }.sortedBy { it.name }
                
                if (matchingVoices.isNotEmpty()) {
                    val targetGender = when (voiceId) {
                        "nova", "shimmer" -> "female"
                        "echo", "fable", "onyx" -> "male"
                        else -> ""
                    }
                    
                    val voiceIndex = when (voiceId) {
                        "alloy" -> 0
                        "echo" -> 1
                        "fable" -> 2
                        "onyx" -> 3
                        "nova" -> 4
                        "shimmer" -> 5
                        else -> 0
                    }
                    
                    // Filter or find a voice matching the target gender in its name, or fallback dynamically
                    val selectedVoice = if (targetGender.isNotEmpty()) {
                        matchingVoices.firstOrNull { it.name.contains(targetGender, ignoreCase = true) }
                            ?: matchingVoices[voiceIndex % matchingVoices.size]
                    } else {
                        matchingVoices[voiceIndex % matchingVoices.size]
                    }
                    
                    tts?.voice = selectedVoice
                }
            }
        } catch (e: Exception) {
            // Ignore if voices cannot be accessed
        }
    }

    fun speak(text: String, emotion: Emotion, customSpeed: Float): Boolean {
        if (!isInitialized) return false
        
        // Emotion provides a base speed modifier, customSpeed scales it further.
        val finalSpeed = (emotion.speed * customSpeed).coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(finalSpeed)
        
        // Combine voice base pitch with emotional pitch
        val finalPitch = (voiceBasePitch * emotion.pitch).coerceIn(0.5f, 2.0f)
        tts?.setPitch(finalPitch)
        
        val params = android.os.Bundle()
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "auditext_utterance")
        return result == TextToSpeech.SUCCESS
    }

    fun stop() {
        tts?.stop()
        _isPlaying.update { false }
    }

    fun downloadToFile(text: String, emotion: Emotion, customSpeed: Float, file: java.io.File, utteranceId: String, callback: ((Boolean) -> Unit)? = null): Int {
        if (!isInitialized) return TextToSpeech.ERROR
        
        onSynthesisComplete = callback
        val finalSpeed = (emotion.speed * customSpeed).coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(finalSpeed)

        val finalPitch = (voiceBasePitch * emotion.pitch).coerceIn(0.5f, 2.0f)
        tts?.setPitch(finalPitch)
        
        val params = android.os.Bundle()
        return tts?.synthesizeToFile(text, params, file, "synth_$utteranceId") ?: TextToSpeech.ERROR
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
