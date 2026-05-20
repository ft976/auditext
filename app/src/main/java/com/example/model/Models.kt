package com.example.model

import androidx.compose.ui.graphics.Color

data class Voice(val id: String, val name: String, val gender: String, val description: String, val color: Color)
data class Emotion(val key: String, val label: String, val speed: Float, val description: String, val pitch: Float = 1.0f)
data class Quote(val text: String, val author: String)
