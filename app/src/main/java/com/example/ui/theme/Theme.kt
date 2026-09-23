package com.example.ui.theme

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

private val BeautifulBubbleColorScheme = lightColorScheme(
    primary = BubbleBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2EAF8),
    onPrimaryContainer = BubbleTextPrimary,
    secondary = BubbleCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCF6FF),
    onSecondaryContainer = Color(0xFF004050),
    background = BubbleBg,
    surface = BubbleCardBg,
    onBackground = BubbleTextPrimary,
    onSurface = BubbleTextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = BubbleTextSecondary,
    outline = BubbleBorder,
    outlineVariant = Color(0xFFE2E8F0)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic system color so our custom pastel palette is consistently active
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  // Always use our custom aesthetic to match the user's uploaded mock reference perfectly on both dark & light modes
  val colorScheme = BeautifulBubbleColorScheme

  MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
  )
}
