package com.quantma.lite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline
)

/** Pure AMOLED black — same accent/text colors as dark, but pitch-black backgrounds. */
private val AmoledColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = AmoledBackground,
    onBackground = DarkOnBackground,
    surface = AmoledSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = AmoledOutline
)

@Composable
fun QuantMATheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    customTheme: CustomThemeColors = CustomThemeColors.Default,
    content: @Composable () -> Unit
) {
    val baseScheme = when (themeMode) {
        ThemeMode.LIGHT  -> LightColorScheme
        ThemeMode.DARK   -> DarkColorScheme
        ThemeMode.AMOLED -> AmoledColorScheme
        ThemeMode.SYSTEM -> if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
    }

    val colorScheme = baseScheme.copy(
        primary = customTheme.accent ?: baseScheme.primary,
        primaryContainer = customTheme.userBubble ?: baseScheme.primaryContainer,
        surfaceVariant = customTheme.assistantBubble ?: baseScheme.surfaceVariant,
        surface = customTheme.panel ?: baseScheme.surface
    )

    CompositionLocalProvider(LocalCustomTheme provides customTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
