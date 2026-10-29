package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = WaLightTealPrimary,
    onPrimary = Color.White,
    primaryContainer = WaLightTealDark,
    onPrimaryContainer = Color.White,
    secondary = WaLightTicksBlue,
    onSecondary = Color.White,
    tertiary = CalcLightKeyEquals,
    background = CalcLightBackground,
    surface = WaLightSurface,
    onBackground = WaLightTextPrimary,
    onSurface = WaLightTextPrimary,
    surfaceVariant = WaLightTopBar,
    onSurfaceVariant = WaLightTextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Enforce clean, requested Lite theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
