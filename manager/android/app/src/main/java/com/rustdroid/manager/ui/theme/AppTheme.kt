package com.rustdroid.manager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rustdroid.manager.data.ThemeMode

private val GrayLightScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF4B5563),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFF8FAFC),
    secondary = androidx.compose.ui.graphics.Color(0xFF6B7280),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFF8FAFC),
    background = androidx.compose.ui.graphics.Color(0xFFF1F3F5),
    onBackground = androidx.compose.ui.graphics.Color(0xFF111827),
    surface = androidx.compose.ui.graphics.Color(0xFFE5E7EB),
    onSurface = androidx.compose.ui.graphics.Color(0xFF111827),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFD1D5DB),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF374151)
)

private val GrayDarkScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF9CA3AF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF111827),
    secondary = androidx.compose.ui.graphics.Color(0xFF6B7280),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFF9FAFB),
    background = androidx.compose.ui.graphics.Color(0xFF111315),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE5E7EB),
    surface = androidx.compose.ui.graphics.Color(0xFF1F2328),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE5E7EB),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2E3238),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF9CA3AF)
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )
)

@Composable
fun RustDroidTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (dark) GrayDarkScheme else GrayLightScheme,
        typography = AppTypography,
        content = content
    )
}
