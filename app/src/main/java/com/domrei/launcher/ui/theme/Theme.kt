package com.domrei.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = LauncherColorTokens.DefaultAccent,
    secondary = LauncherColorTokens.DefaultAccentSecondary,
    tertiary = LauncherColorTokens.Pink80,
    background = LauncherColorTokens.Background.SkyStop0,
    surface = LauncherColorTokens.DarkInk,
    onPrimary = LauncherColorTokens.DarkInk,
    onBackground = LauncherColorTokens.White,
    onSurface = LauncherColorTokens.White,
    error = LauncherColorTokens.Error,
)

private val LightColorScheme = lightColorScheme(
    primary = LauncherColorTokens.Purple40,
    secondary = LauncherColorTokens.PurpleGrey40,
    tertiary = LauncherColorTokens.Pink40,
    error = LauncherColorTokens.ErrorStrong,
)

@Composable
fun DomreiLauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accentColor: Color = LauncherColorTokens.DefaultAccent,
    content: @Composable () -> Unit,
) {
    val launcherColors = remember(accentColor) {
        createLauncherColors(accentColor)
    }

    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = baseScheme.copy(
        primary = launcherColors.accent,
        onPrimary = launcherColors.onAccent,
        secondary = launcherColors.accentSecondary,
        error = launcherColors.error,
    )

    CompositionLocalProvider(LocalLauncherColors provides launcherColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
