package com.example.data

import com.example.model.*
import com.example.ui.theme.*

val VOICES = listOf(
  Voice("nova", "Nova", "Female", "Energetic and bright", VoiceBlue),
  Voice("alloy", "Alloy", "Neutral", "Versatile and clear", VoiceGreen),
  Voice("echo", "Echo", "Male", "Warm and round", VoiceOrange),
  Voice("fable", "Fable", "Male", "British and expressive", VoiceIndigo),
  Voice("onyx", "Onyx", "Male", "Deep and authoritative", VoiceSlate),
  Voice("shimmer", "Shimmer", "Female", "Clear and articulate", VoicePurple),
)

val EMOTIONS = listOf(
  Emotion("neutral", "Neutral", 1.0f, "Clear, balanced, natural", 1.0f),
  Emotion("happy", "Happy", 1.1f, "Warm, joyful, uplifting", 1.2f),
  Emotion("sad", "Sad", 0.9f, "Melancholic, gentle, quiet", 0.8f),
  Emotion("excited", "Excited", 1.25f, "High energy, fast pace", 1.3f),
  Emotion("whispering", "Whispering", 0.85f, "Soft, intimate, private", 0.7f),
  Emotion("storytelling", "Storytelling", 0.95f, "Warm, captivating", 1.05f),
  Emotion("calm", "Calm", 0.9f, "Soothing, relaxed", 0.9f),
  Emotion("angry", "Angry", 1.15f, "Stern, forceful, intense", 0.75f),
  Emotion("fearful", "Fearful", 1.2f, "Trembling, nervous", 1.4f),
  Emotion("serious", "Serious", 1.0f, "Formal, authoritative", 0.85f)
)

val QUOTES = listOf(
  Quote("Words mean more than what is set down on paper. It takes the human voice to infuse them with deeper meaning.", "Maya Angelou"),
  Quote("The human voice is the most perfect instrument of all.", "Arvo Pärt"),
  Quote("A word out of season may mar a whole lifetime.", "Greek Proverb")
)

val LANGUAGES = listOf("English", "Spanish", "French", "German", "Italian", "Japanese", "Korean", "Portuguese", "Chinese", "Hindi")

val PROVIDERS = listOf("Native (Offline)", "Gemini", "OpenAI", "ElevenLabs", "Deepgram", "Cartesia")
