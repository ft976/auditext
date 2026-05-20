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

    private var currentVoiceId = "alloy"
    private var currentLangName = "English"
    private var voiceBasePitch = 1.0f
    private var voiceBaseSpeed = 1.0f

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            
            // Reapply the active language and voice profile now that we are initialized!
            setLanguage(currentLangName)
            setVoiceProfile(currentVoiceId)

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

    fun setLanguage(lang: String) {
        currentLangName = lang
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
        applyVoiceSelection()
    }

    fun setVoiceProfile(voiceId: String) {
        currentVoiceId = voiceId
        if (!isInitialized) return
        
        // Calibrate pitch and speed perfectly for distinct character personalities
        when (voiceId) {
            "nova" -> {
                voiceBasePitch = 1.35f
                voiceBaseSpeed = 1.15f
            }
            "alloy" -> {
                voiceBasePitch = 1.00f
                voiceBaseSpeed = 1.00f
            }
            "echo" -> {
                voiceBasePitch = 0.82f
                voiceBaseSpeed = 0.88f
            }
            "fable" -> {
                voiceBasePitch = 0.94f
                voiceBaseSpeed = 1.04f
            }
            "onyx" -> {
                voiceBasePitch = 0.65f
                voiceBaseSpeed = 0.80f
            }
            "shimmer" -> {
                voiceBasePitch = 1.48f
                voiceBaseSpeed = 1.10f
            }
            else -> {
                voiceBasePitch = 1.00f
                voiceBaseSpeed = 1.00f
            }
        }
        
        tts?.setPitch(voiceBasePitch)
        applyVoiceSelection()
    }

    private fun applyVoiceSelection() {
        if (!isInitialized) return
        try {
            val voices = tts?.voices
            val currentLang = tts?.language
            if (voices != null && currentLang != null) {
                // Find matching voices under the current language
                // 1. First try to get offline-only voices for best local reliability
                var matchingVoices = voices.filter { 
                    it.locale != null && it.locale.language == currentLang.language && !it.isNetworkConnectionRequired
                }.sortedBy { it.name }
                
                // 2. Fall back to include online network voices if offline options are too limited or unavailable
                if (matchingVoices.size < 4) {
                    matchingVoices = voices.filter { 
                        it.locale != null && it.locale.language == currentLang.language
                    }.sortedBy { it.name }
                }
                
                if (matchingVoices.isNotEmpty()) {
                    // Try to map each voice ID to a distinct index
                    val voiceIndex = when (currentVoiceId) {
                        "alloy" -> 0
                        "echo" -> 1
                        "fable" -> 2
                        "onyx" -> 3
                        "nova" -> 4
                        "shimmer" -> 5
                        else -> 0
                    }
                    
                    val targetGender = when (currentVoiceId) {
                        "nova", "shimmer" -> "female"
                        "echo", "fable", "onyx" -> "male"
                        else -> ""
                    }
                    
                    // Filter matching voices that represent the target gender if possible
                    val genderFilteredVoices = when (targetGender) {
                        "female" -> matchingVoices.filter { 
                            it.name.contains("female", ignoreCase = true) || 
                            it.name.contains("fem", ignoreCase = true) || 
                            it.name.contains("a-local", ignoreCase = true) || 
                            it.name.contains("c-local", ignoreCase = true) || 
                            it.name.contains("e-local", ignoreCase = true) || 
                            it.name.contains("g-local", ignoreCase = true) || 
                            it.name.contains("i-local", ignoreCase = true)
                        }
                        "male" -> matchingVoices.filter {
                            it.name.contains("male", ignoreCase = true) || 
                            it.name.contains("masc", ignoreCase = true) || 
                            it.name.contains("b-local", ignoreCase = true) || 
                            it.name.contains("d-local", ignoreCase = true) || 
                            it.name.contains("f-local", ignoreCase = true) || 
                            it.name.contains("h-local", ignoreCase = true) || 
                            it.name.contains("j-local", ignoreCase = true)
                        }
                        else -> emptyList()
                    }
                    
                    // Assign sub-indices for specific gender roles so different characters do not pick the same voice
                    val subIndex = when (currentVoiceId) {
                        "nova" -> 0
                        "shimmer" -> 1
                        "echo" -> 0
                        "fable" -> 1
                        "onyx" -> 2
                        else -> 0
                    }
                    
                    var selectedVoice = if (genderFilteredVoices.isNotEmpty()) {
                        genderFilteredVoices[subIndex % genderFilteredVoices.size]
                    } else null
                    
                    if (selectedVoice == null) {
                        // Fallback to distinct index distribution across the list
                        selectedVoice = matchingVoices[voiceIndex % matchingVoices.size]
                    }
                    
                    tts?.voice = selectedVoice
                    Log.d("TtsManager", "Applied physical voice: ${selectedVoice.name} for $currentVoiceId")
                }
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "Error in applyVoiceSelection", e)
        }
    }

    fun speak(text: String, emotion: Emotion, customSpeed: Float): Boolean {
        if (!isInitialized) return false
        
        val finalSpeed = (emotion.speed * customSpeed * voiceBaseSpeed).coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(finalSpeed)
        
        val finalPitch = (voiceBasePitch * emotion.pitch).coerceIn(0.5f, 2.0f)
        tts?.setPitch(finalPitch)
        
        applyVoiceSelection()
        
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
        val finalSpeed = (emotion.speed * customSpeed * voiceBaseSpeed).coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(finalSpeed)

        val finalPitch = (voiceBasePitch * emotion.pitch).coerceIn(0.5f, 2.0f)
        tts?.setPitch(finalPitch)
        
        applyVoiceSelection()
        
        val params = android.os.Bundle()
        return tts?.synthesizeToFile(text, params, file, "synth_$utteranceId") ?: TextToSpeech.ERROR
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
