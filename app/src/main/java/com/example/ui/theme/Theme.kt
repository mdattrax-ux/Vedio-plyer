package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val WhiteGrayColorScheme = lightColorScheme(
    primary = AccentDark,
    onPrimary = PureWhite,
    primaryContainer = SurfaceGray,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = PureWhite,
    secondaryContainer = SurfaceGraySubtle,
    onSecondaryContainer = TextPrimary,
    tertiary = TextTertiary,
    onTertiary = PureWhite,
    background = PureWhite,
    onBackground = TextPrimary,
    surface = PureWhite,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceGray,
    onSurfaceVariant = TextSecondary,
    outline = BorderGray,
    outlineVariant = DividerGray
)

@Composable
fun AuraPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // White + Gray identity prioritized
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WhiteGrayColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = AuraPlayerTheme(darkTheme, dynamicColor, content)
