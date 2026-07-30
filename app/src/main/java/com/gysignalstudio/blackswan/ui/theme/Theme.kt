package com.gysignalstudio.blackswan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFFF8FAFC),
  onPrimary = Color(0xFF020617),
  primaryContainer = Color(0xFF1E293B),
  onPrimaryContainer = Color(0xFFF8FAFC),
  secondary = Color(0xFF38BDF8),
  onSecondary = Color(0xFF082F49),
  background = Color(0xFF090D16),
  onBackground = Color(0xFFF8FAFC),
  surface = Color(0xFF0F172A),
  onSurface = Color(0xFFF8FAFC),
  surfaceVariant = Color(0xFF1E293B),
  onSurfaceVariant = Color(0xFFCBD5E1),
  outline = Color(0xFF475569),
  outlineVariant = Color(0xFF334155),
  error = Color(0xFFEF4444),
  onError = Color.White
)

private val LightColorScheme = lightColorScheme(
  primary = Color(0xFF991B1B),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFFEE2E2),
  onPrimaryContainer = Color(0xFF7F1D1D),
  secondary = Color(0xFF0369A1),
  onSecondary = Color.White,
  background = Color(0xFFF4F7FB),
  onBackground = Color(0xFF111827),
  surface = Color.White,
  onSurface = Color(0xFF111827),
  surfaceVariant = Color(0xFFE8EEF5),
  onSurfaceVariant = Color(0xFF475569),
  outline = Color(0xFF64748B),
  outlineVariant = Color(0xFFCBD5E1),
  error = Color(0xFFB91C1C),
  onError = Color.White
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
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
