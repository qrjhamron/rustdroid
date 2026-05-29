package com.rustdroid.manager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rustdroid.manager.data.ThemeMode

private val GraphiteDarkScheme = darkColorScheme(
    primary = Color(0xFF8B8FA3),
    onPrimary = Color(0xFFE8E9ED),
    primaryContainer = Color(0xFF2A2D35),
    onPrimaryContainer = Color(0xFFCACDD6),
    secondary = Color(0xFF6B7280),
    onSecondary = Color(0xFFF0F1F3),
    tertiary = Color(0xFFD4A06A),
    onTertiary = Color(0xFF1A1A1A),
    error = Color(0xFFD46A6A),
    onError = Color(0xFF1A1A1A),
    background = Color(0xFF0F1114),
    onBackground = Color(0xFFE0E1E5),
    surface = Color(0xFF181B20),
    onSurface = Color(0xFFE0E1E5),
    surfaceVariant = Color(0xFF22262D),
    onSurfaceVariant = Color(0xFF9CA0AD),
    outline = Color(0xFF3A3E47),
    outlineVariant = Color(0xFF2A2D35),
    surfaceContainerHighest = Color(0xFF2A2D35),
    surfaceContainerHigh = Color(0xFF22262D),
    surfaceContainer = Color(0xFF1C1F25),
    surfaceContainerLow = Color(0xFF181B20),
    surfaceContainerLowest = Color(0xFF0F1114)
)

private val GraphiteLightScheme = lightColorScheme(
    primary = Color(0xFF4B5063),
    onPrimary = Color(0xFFF8F9FB),
    primaryContainer = Color(0xFFE0E2E8),
    onPrimaryContainer = Color(0xFF2A2D35),
    secondary = Color(0xFF6B7280),
    onSecondary = Color(0xFFF8F9FB),
    tertiary = Color(0xFF9A7040),
    onTertiary = Color(0xFFF8F9FB),
    error = Color(0xFFB54545),
    onError = Color(0xFFF8F9FB),
    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF16181D),
    surface = Color(0xFFEAEBEF),
    onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFDCDDE3),
    onSurfaceVariant = Color(0xFF44464E),
    outline = Color(0xFFC4C6CE),
    outlineVariant = Color(0xFFDCDDE3)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp)
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
        colorScheme = if (dark) GraphiteDarkScheme else GraphiteLightScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
