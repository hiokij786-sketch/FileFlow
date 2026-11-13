package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode

private val FileFlowLightColorScheme = lightColorScheme(
    primary = GoldPrimary,
    onPrimary = Color.White,
    primaryContainer = GoldSoftTint,
    onPrimaryContainer = GoldDark,
    secondary = InkBlack,
    onSecondary = Color.White,
    secondaryContainer = SoftGreyContainer,
    onSecondaryContainer = InkBlack,
    tertiary = GoldDark,
    onTertiary = Color.White,
    tertiaryContainer = GoldSoftTint,
    onTertiaryContainer = InkBlack,
    background = WhiteBackground,
    onBackground = InkBlack,
    surface = WhiteBackground,
    onSurface = InkBlack,
    surfaceVariant = SoftGreySurface,
    onSurfaceVariant = CharcoalMuted,
    surfaceContainerLowest = WhiteBackground,
    surfaceContainerLow = SoftGreySurface,
    surfaceContainer = SoftGreySurface,
    surfaceContainerHigh = SoftGreyContainer,
    surfaceContainerHighest = SoftGreyBorder,
    outline = SoftGreyBorder,
    outlineVariant = Color(0xFFECEEF3),
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonTint,
    onErrorContainer = CrimsonError
)

private val FileFlowDarkColorScheme = darkColorScheme(
    primary = GoldLightAccent,
    onPrimary = ObsidianBackground,
    primaryContainer = GoldDarkContainer,
    onPrimaryContainer = GoldLightAccent,
    secondary = OffWhiteText,
    onSecondary = ObsidianBackground,
    secondaryContainer = ObsidianSurfaceHigh,
    onSecondaryContainer = OffWhiteText,
    tertiary = GoldPrimary,
    onTertiary = ObsidianBackground,
    tertiaryContainer = GoldDarkContainer,
    onTertiaryContainer = GoldLightAccent,
    background = ObsidianBackground,
    onBackground = OffWhiteText,
    surface = ObsidianSurface,
    onSurface = OffWhiteText,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = SilverMutedText,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurface,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    surfaceContainerHighest = ObsidianBorder,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF232836),
    error = Color(0xFFF27373),
    onError = ObsidianBackground,
    errorContainer = CrimsonDarkTint,
    onErrorContainer = Color(0xFFFCA5A5)
)

val FileFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FileFlowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) FileFlowDarkColorScheme else FileFlowLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FileFlowTypography,
        shapes = FileFlowShapes,
        content = content
    )
}
