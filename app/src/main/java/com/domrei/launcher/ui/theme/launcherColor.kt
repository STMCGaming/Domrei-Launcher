package com.domrei.launcher.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Single source of truth for every color used across Domrei Launcher.
 * All UI composables, backgrounds, dialogs, and Material theme schemes read from here.
 */
object LauncherColorTokens {
    val White = Color.White
    val Black = Color.Black
    val Transparent = Color.Transparent
    const val TransparentArgb = 0x00000000

    // Material Theme baseline fallbacks
    val Purple80 = Color(0xFFD0BCFF)
    val PurpleGrey80 = Color(0xFFCCC2DC)
    val Pink80 = Color(0xFFEFB8C8)
    val Purple40 = Color(0xFF6650A4)
    val PurpleGrey40 = Color(0xFF625B71)
    val Pink40 = Color(0xFF7D5260)

    // Default user-customizable accent
    val DefaultAccent = Color(0xFFFFDF8E)
    val DefaultAccentSecondary = Color(0xFFFFB84D)
    val DarkInk = Color(0xFF0A1224)

    // Hue rainbow stops for interactive Color Picker bar
    val HueSpectrum: List<Color> = listOf(
        Color(0xFFFF0000),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF00FFFF),
        Color(0xFF0000FF),
        Color(0xFFFF00FF),
        Color(0xFFFF0000),
    )

    // Semantic status colors
    val Success = Color(0xFF7EE2A8)
    val Error = Color(0xFFFF7A7E)
    val ErrorStrong = Color(0xFFE5484D)
    val ErrorText = Color(0xFFFFB2B4)

    // Frosted glass & modal surface colors
    val GlassBackdropTop = Color(0x380A1224)
    val GlassBackdropMid = Color(0x2E0F1B33)
    val GlassBackdropBottom = Color(0x44070D1A)

    val ProgressBackdropTop = Color(0x200A1224)
    val ProgressBackdropMid = Color(0x160F1B33)
    val ProgressBackdropBottom = Color(0x2A070D1A)

    val ProgressBackdropLegacyTop = Color(0x520A1224)
    val ProgressBackdropLegacyMid = Color(0x460F1B33)
    val ProgressBackdropLegacyBottom = Color(0x64070D1A)

    val DialogSurface = Color(0x33101A2E)

    /** Colors for the animated 2D Minecraft night sky scene in Background.kt. */
    object Background {
        val SkyStop0 = Color(0xFF040711)
        val SkyStop1 = Color(0xFF081024)
        val SkyStop2 = Color(0xFF0F1D3A)
        val SkyStop3 = Color(0xFF172C54)
        val SkyStop4 = Color(0xFF213C6E)
        val SkyStop5 = Color(0xFF2C4E86)

        val Star = Color(0xFFE8F2FF)
        val HorizonGlowMid = Color(0x334A78B8)
        val HorizonGlowEnd = Color(0x6634598F)

        val MountainFarFill = Color(0xFF121E36)
        val MountainFarRim = Color(0xFF2B446E)
        val MountainMidFill = Color(0xFF0B1324)
        val MountainMidRim = Color(0xFF1F3354)
        val TreeFill = Color(0xFF0B1324)
        val TreeRim = Color(0xFF243B61)
        val GroundNearFill = Color(0xFF050912)
        val GroundNearRim = Color(0xFF142238)

        val MoonHaloOuter = Color(0x1470A8FF)
        val MoonHaloMid = Color(0x248CBEFF)
        val MoonHaloInner = Color(0x3DB8DAFF)
        val MoonFaceLight = Color(0xFFDCEAFF)
        val MoonCore = Color(0xFFF4F9FF)
        val MoonCrater0 = Color(0xFFB3C9EA)
        val MoonCrater1 = Color(0xFF9AB4DC)
        val MoonCrater2 = Color(0xFFAEC4E6)
        val MoonCrater3 = Color(0xFF8CA7D1)

        val CloudShadow = Color(0x9935507D)
        val CloudMid = Color(0xD9B4CEF0)
        val CloudHighlight = Color(0xE6EEF6FF)
        val CloudRim = Color(0xD97899C8)
    }
}

@Immutable
data class LauncherColors(
    val accent: Color,
    val accentSecondary: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val accentSubtleContainer: Color,
    val accentBorder: Color,
    val accentBorderSubtle: Color,
    val accentBorderStrong: Color,
    val accentGlowSoft: Color,
    val white: Color,
    val black: Color,
    val transparent: Color,
    val textPrimary: Color,
    val textStrong: Color,
    val textSecondary: Color,
    val textButton: Color,
    val textBody: Color,
    val textMuted: Color,
    val textSubtle: Color,
    val textDim: Color,
    val textDisabled: Color,
    val textActionDisabled: Color,
    val iconPrimary: Color,
    val iconMuted: Color,
    val iconDisabled: Color,
    val surfaceCard: Color,
    val surfaceCardSelected: Color,
    val surfaceCardMuted: Color,
    val surfaceButton: Color,
    val surfaceButtonSubtle: Color,
    val surfaceButtonSelected: Color,
    val surfaceButtonDisabled: Color,
    val surfaceBadge: Color,
    val surfaceInput: Color,
    val surfaceInputFocused: Color,
    val borderSubtle: Color,
    val borderDefault: Color,
    val borderButton: Color,
    val borderButtonStrong: Color,
    val borderModal: Color,
    val borderSelectedCard: Color,
    val borderHighlightTop: Color,
    val borderHighlightMid: Color,
    val borderHighlightBottom: Color,
    val borderRailTop: Color,
    val borderRailMid: Color,
    val borderRailBottom: Color,
    val divider: Color,
    val railDividerTop: Color,
    val railDividerMid: Color,
    val railDividerBottom: Color,
    val dialogScrim: Color,
    val dialogSurface: Color,
    val glassBackdropTop: Color,
    val glassBackdropMid: Color,
    val glassBackdropBottom: Color,
    val progressBackdropTop: Color,
    val progressBackdropMid: Color,
    val progressBackdropBottom: Color,
    val progressBackdropLegacyTop: Color,
    val progressBackdropLegacyMid: Color,
    val progressBackdropLegacyBottom: Color,
    val success: Color,
    val error: Color,
    val errorStrong: Color,
    val errorText: Color,
    val errorContainer: Color,
    val errorSubtleContainer: Color,
    val errorCancelContainer: Color,
    val errorBorder: Color,
    val errorBorderStrong: Color,
    val errorBorderSubtle: Color,
) {
    val glassBackdropStops: List<Pair<Float, Color>>
        get() = listOf(
            0.0f to glassBackdropTop,
            0.5f to glassBackdropMid,
            1.0f to glassBackdropBottom,
        )

    fun glassBackdropStopsFor(supportsBlur: Boolean): List<Pair<Float, Color>> = if (supportsBlur) {
        glassBackdropStops
    } else {
        listOf(
            0.0f to progressBackdropLegacyTop,
            0.5f to progressBackdropLegacyMid,
            1.0f to progressBackdropLegacyBottom,
        )
    }

    fun progressBackdropStops(supportsBlur: Boolean): List<Pair<Float, Color>> = if (supportsBlur) {
        listOf(
            0.0f to progressBackdropTop,
            0.5f to progressBackdropMid,
            1.0f to progressBackdropBottom,
        )
    } else {
        listOf(
            0.0f to progressBackdropLegacyTop,
            0.5f to progressBackdropLegacyMid,
            1.0f to progressBackdropLegacyBottom,
        )
    }

    val panelBorderStops: List<Pair<Float, Color>>
        get() = listOf(
            0.0f to borderHighlightTop,
            0.5f to borderHighlightMid,
            1.0f to borderHighlightBottom,
        )

    val railBorderStops: List<Pair<Float, Color>>
        get() = listOf(
            0.0f to borderRailTop,
            0.5f to borderRailMid,
            1.0f to borderRailBottom,
        )

    val railDividerStops: List<Pair<Float, Color>>
        get() = listOf(
            0.0f to railDividerTop,
            0.5f to railDividerMid,
            1.0f to railDividerBottom,
        )
}

fun deriveSecondaryAccent(accent: Color): Color {
    if (accent == LauncherColorTokens.DefaultAccent) {
        return LauncherColorTokens.DefaultAccentSecondary
    }
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(accent.toArgb(), hsv)
    if (hsv[1] > 0.08f) {
        hsv[0] = (hsv[0] + 348f) % 360f
        hsv[1] = (hsv[1] + 0.18f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 0.88f).coerceIn(0.28f, 1f)
    } else {
        hsv[2] = (hsv[2] * 0.78f).coerceIn(0.2f, 1f)
    }
    return Color(AndroidColor.HSVToColor(hsv))
}

fun createLauncherColors(
    accent: Color = LauncherColorTokens.DefaultAccent,
): LauncherColors {
    val white = LauncherColorTokens.White
    val black = LauncherColorTokens.Black
    val onAccent = if (accent.luminance() > 0.55f) LauncherColorTokens.DarkInk else white

    return LauncherColors(
        accent = accent,
        accentSecondary = deriveSecondaryAccent(accent),
        onAccent = onAccent,
        accentContainer = accent.copy(alpha = 0.22f),
        accentSubtleContainer = accent.copy(alpha = 0.18f),
        accentBorder = accent.copy(alpha = 0.55f),
        accentBorderSubtle = accent.copy(alpha = 0.40f),
        accentBorderStrong = accent.copy(alpha = 0.75f),
        accentGlowSoft = accent.copy(alpha = 0.48f),
        white = white,
        black = black,
        transparent = LauncherColorTokens.Transparent,
        textPrimary = white,
        textStrong = white.copy(alpha = 0.92f),
        textSecondary = white.copy(alpha = 0.90f),
        textButton = white.copy(alpha = 0.88f),
        textBody = white.copy(alpha = 0.86f),
        textMuted = white.copy(alpha = 0.85f),
        textSubtle = white.copy(alpha = 0.78f),
        textDim = white.copy(alpha = 0.65f),
        textDisabled = white.copy(alpha = 0.42f),
        textActionDisabled = white.copy(alpha = 0.35f),
        iconPrimary = white,
        iconMuted = white.copy(alpha = 0.55f),
        iconDisabled = white.copy(alpha = 0.38f),
        surfaceCard = white.copy(alpha = 0.08f),
        surfaceCardSelected = white.copy(alpha = 0.22f),
        surfaceCardMuted = white.copy(alpha = 0.05f),
        surfaceButton = white.copy(alpha = 0.14f),
        surfaceButtonSubtle = white.copy(alpha = 0.10f),
        surfaceButtonSelected = white.copy(alpha = 0.24f),
        surfaceButtonDisabled = white.copy(alpha = 0.08f),
        surfaceBadge = white.copy(alpha = 0.16f),
        surfaceInput = white.copy(alpha = 0.12f),
        surfaceInputFocused = white.copy(alpha = 0.18f),
        borderSubtle = white.copy(alpha = 0.14f),
        borderDefault = white.copy(alpha = 0.16f),
        borderButton = white.copy(alpha = 0.22f),
        borderButtonStrong = white.copy(alpha = 0.32f),
        borderModal = white.copy(alpha = 0.28f),
        borderSelectedCard = white.copy(alpha = 0.42f),
        borderHighlightTop = white.copy(alpha = 0.34f),
        borderHighlightMid = white.copy(alpha = 0.16f),
        borderHighlightBottom = white.copy(alpha = 0.26f),
        borderRailTop = white.copy(alpha = 0.28f),
        borderRailMid = white.copy(alpha = 0.14f),
        borderRailBottom = white.copy(alpha = 0.22f),
        divider = white.copy(alpha = 0.28f),
        railDividerTop = white.copy(alpha = 0.18f),
        railDividerMid = white.copy(alpha = 0.34f),
        railDividerBottom = white.copy(alpha = 0.18f),
        dialogScrim = black.copy(alpha = 0.20f),
        dialogSurface = LauncherColorTokens.DialogSurface,
        glassBackdropTop = LauncherColorTokens.GlassBackdropTop,
        glassBackdropMid = LauncherColorTokens.GlassBackdropMid,
        glassBackdropBottom = LauncherColorTokens.GlassBackdropBottom,
        progressBackdropTop = LauncherColorTokens.ProgressBackdropTop,
        progressBackdropMid = LauncherColorTokens.ProgressBackdropMid,
        progressBackdropBottom = LauncherColorTokens.ProgressBackdropBottom,
        progressBackdropLegacyTop = LauncherColorTokens.ProgressBackdropLegacyTop,
        progressBackdropLegacyMid = LauncherColorTokens.ProgressBackdropLegacyMid,
        progressBackdropLegacyBottom = LauncherColorTokens.ProgressBackdropLegacyBottom,
        success = LauncherColorTokens.Success,
        error = LauncherColorTokens.Error,
        errorStrong = LauncherColorTokens.ErrorStrong,
        errorText = LauncherColorTokens.ErrorText,
        errorContainer = LauncherColorTokens.ErrorStrong.copy(alpha = 0.22f),
        errorSubtleContainer = LauncherColorTokens.ErrorStrong.copy(alpha = 0.16f),
        errorCancelContainer = LauncherColorTokens.ErrorStrong.copy(alpha = 0.25f),
        errorBorder = LauncherColorTokens.Error.copy(alpha = 0.45f),
        errorBorderStrong = LauncherColorTokens.Error.copy(alpha = 0.40f),
        errorBorderSubtle = LauncherColorTokens.Error.copy(alpha = 0.40f),
    )
}

fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color.toArgb(), hsv)
    return hsv
}

fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    return Color(AndroidColor.HSVToColor(floatArrayOf(h, s, v)))
}

fun formatColorHex(color: Color): String {
    val rgb = color.toArgb() and 0x00FFFFFF
    return "#%06X".format(rgb)
}

fun parseHexColorOrNull(raw: String): Color? {
    val cleaned = raw.trim().removePrefix("#").uppercase()
    if (cleaned.length != 6 && cleaned.length != 8) return null
    val parsed = cleaned.toLongOrNull(16) ?: return null
    val argb = if (cleaned.length == 6) {
        (0xFF000000L or parsed).toInt()
    } else {
        parsed.toInt()
    }
    return Color(argb)
}

val LocalLauncherColors = compositionLocalOf { createLauncherColors() }

object LauncherTheme {
    val colors: LauncherColors
        @Composable
        get() = LocalLauncherColors.current
}
