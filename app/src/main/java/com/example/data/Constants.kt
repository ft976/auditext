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
  Emotion("neutral", "Neutral", 1.0f, "Clear, balanced, natural"),
  Emotion("happy", "Happy", 1.1f, "Warm, joyful, uplifting"),
  Emotion("sad", "Sad", 0.9f, "Melancholic, gentle, quiet"),
  Emotion("excited", "Excited", 1.25f, "High energy, fast pace"),
  Emotion("whispering", "Whispering", 0.85f, "Soft, intimate, private"),
  Emotion("storytelling", "Storytelling", 0.95f, "Warm, captivating"),
  Emotion("calm", "Calm", 0.9f, "Soothing, relaxed"),
  Emotion("angry", "Angry", 1.15f, "Stern, forceful, intense"),
  Emotion("fearful", "Fearful", 1.2f, "Trembling, nervous"),
  Emotion("serious", "Serious", 1.0f, "Formal, authoritative")
)

val QUOTES = listOf(
  Quote("Words mean more than what is set down on paper. It takes the human voice to infuse them with deeper meaning.", "Maya Angelou"),
  Quote("The human voice is the most perfect instrument of all.", "Arvo Pärt"),
  Quote("A word out of season may mar a whole lifetime.", "Greek Proverb")
)

val LANGUAGES = listOf("English", "Spanish", "French", "German", "Italian", "Japanese", "Korean", "Portuguese", "Chinese")

val PROVIDERS = listOf("Native (Offline)", "Gemini", "OpenAI", "ElevenLabs", "Deepgram", "Cartesia")
