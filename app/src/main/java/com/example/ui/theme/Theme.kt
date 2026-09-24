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

private val DarkColorScheme = darkColorScheme(
    primary = DietPiGreenPrimary,
    onPrimary = Color(0xFF091F00),
    primaryContainer = DietPiGreenContainer,
    onPrimaryContainer = DietPiOnGreenContainer,
    secondary = DietPiGreenLight,
    onSecondary = Color(0xFF091F00),
    tertiary = MetricDiskBlue,
    background = DietPiDarkBg,
    onBackground = Color(0xFFEEEEEE),
    surface = DietPiDarkSurface,
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = DietPiDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFA0A0A5),
    outline = DietPiDarkCardOutline,
    outlineVariant = Color(0xFF28282D)
)

private val LightColorScheme = lightColorScheme(
    primary = DietPiGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8FFB0),
    onPrimaryContainer = Color(0xFF1E4D00),
    secondary = DietPiGreenPrimary,
    onSecondary = Color.White,
    tertiary = Color(0xFF0284C7),
    background = DietPiLightBg,
    onBackground = Color(0xFF0F172A),
    surface = DietPiLightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = DietPiLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = DietPiLightCardOutline,
    outlineVariant = Color(0xFFCBD5E1)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep DietPi signature emerald branding by default
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> DarkColorScheme // DietPi dashboard signature dark look is standard
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
