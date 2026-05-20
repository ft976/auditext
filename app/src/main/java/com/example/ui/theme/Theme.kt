package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = AccentBlue,
    secondary = AccentPurple,
    tertiary = VoiceGreen,
    background = CharcoalBackground,
    surface = CharcoalSurface,
    surfaceVariant = CharcoalSurfaceVariant,
    onPrimary = SoftWhite,
    onSecondary = SoftWhite,
    onBackground = OffWhite,
    onSurface = OffWhite,
    onSurfaceVariant = OffWhite,
    error = ErrorRed
  )

private val LightColorScheme = DarkColorScheme // Force dark theme aesthetically

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force Dark theme for the "Charcoal Studio" vibe
  // Dynamic color is disabled to maintain the specific aesthetic
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
