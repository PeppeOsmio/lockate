package com.peppeosmio.lockate.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.peppeosmio.lockate.domain.UserActivity

internal val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF8B5000),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDCBE),
        onPrimaryContainer = Color(0xFF6A3C00),
        inversePrimary = Color(0xFFFFB870),
        secondary = Color(0xFF735A42),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFBDDC8),
        onSecondaryContainer = Color(0xFF2A1706),
        tertiary = Color(0xFF5B6237),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE0E7B1),
        onTertiaryContainer = Color(0xFF434A22),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        background = Color(0xFFFFF8F5),
        onBackground = Color(0xFF211A14),
        surface = Color(0xFFFFF8F5),
        onSurface = Color(0xFF211A14),
        surfaceVariant = Color(0xFFF3DFD2),
        onSurfaceVariant = Color(0xFF51443A),
        surfaceTint = Color(0xFF8B5000),
        inverseSurface = Color(0xFF372F28),
        inverseOnSurface = Color(0xFFFDEEE3),
        outline = Color(0xFF84746A),
        outlineVariant = Color(0xFFD6C3B6),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFF8F5),
        surfaceDim = Color(0xFFE6D7CD),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFF1E8),
        surfaceContainer = Color(0xFFFAEBE0),
        surfaceContainerHigh = Color(0xFFF4E5DB),
        surfaceContainerHighest = Color(0xFFEEE0D5),
    )

internal val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFFFB870),
        onPrimary = Color(0xFF4A2800),
        primaryContainer = Color(0xFF6A3C00),
        onPrimaryContainer = Color(0xFFFFDCBE),
        inversePrimary = Color(0xFF8B5000),
        secondary = Color(0xFFE2C0A4),
        onSecondary = Color(0xFF412C18),
        secondaryContainer = Color(0xFF59422C),
        onSecondaryContainer = Color(0xFFFFDCBE),
        tertiary = Color(0xFFC4CB97),
        onTertiary = Color(0xFF2D330D),
        tertiaryContainer = Color(0xFF434A22),
        onTertiaryContainer = Color(0xFFE0E7B1),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF19120C),
        onBackground = Color(0xFFEFE0D5),
        surface = Color(0xFF19120C),
        onSurface = Color(0xFFEFE0D5),
        surfaceVariant = Color(0xFF51443A),
        onSurfaceVariant = Color(0xFFD6C3B6),
        surfaceTint = Color(0xFFFFB870),
        inverseSurface = Color(0xFFEFE0D5),
        inverseOnSurface = Color(0xFF372F28),
        outline = Color(0xFF9E8E82),
        outlineVariant = Color(0xFF51443A),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF41372F),
        surfaceDim = Color(0xFF19120C),
        surfaceContainerLowest = Color(0xFF130D07),
        surfaceContainerLow = Color(0xFF221A14),
        surfaceContainer = Color(0xFF261E17),
        surfaceContainerHigh = Color(0xFF31281F),
        surfaceContainerHighest = Color(0xFF3C332A),
    )

/** A colour to fill with and the colour of what sits on it, e.g. an avatar and its initial */
@Immutable data class ColorPair(val color: Color, val onColor: Color)

/**
 * Colours that carry meaning beyond the colour scheme's roles, all derived from the scheme so they
 * follow the accent: the activity of a member and one colour per member.
 */
@Immutable
data class LockateColors(
    val still: Color,
    val onFoot: Color,
    val onBicycle: Color,
    val inVehicle: Color,
    val members: List<ColorPair>,
) {
    fun activity(activity: UserActivity): Color =
        when (activity) {
            UserActivity.Unknown,
            UserActivity.Still -> still
            UserActivity.OnFoot -> onFoot
            UserActivity.OnBicycle -> onBicycle
            UserActivity.InVehicle -> inVehicle
        }

    /** The same member gets the same colour on every phone, since it depends on the id only */
    fun member(memberId: String): ColorPair =
        members[Math.floorMod(memberId.hashCode(), members.size)]
}

/** [primary]'s hue turned by [degrees], at a fixed saturation and lightness */
private fun turnedHue(primary: Color, degrees: Float, saturation: Float, lightness: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(primary.toArgb(), hsl)
    val hue = (hsl[0] + degrees) % 360f
    return Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))
}

/**
 * Builds the colours from the scheme. Members: five colours, the primary's hue turned by 60° steps,
 * muted enough for a filled avatar with a white (light) or dark (dark theme) initial. Activities:
 * the primary's hue turned by 180° (on foot), 90° (bike) and 240° (vehicle), more vivid so a route
 * stands out on the map; with the orange primary they are blue, green and violet. Still is the
 * scheme's outline, the neutral case.
 */
internal fun lockateColors(scheme: ColorScheme, dark: Boolean): LockateColors {
    val primary = scheme.primary
    val memberSaturation = if (dark) 0.6f else 0.45f
    val memberLightness = if (dark) 0.75f else 0.38f
    val routeSaturation = if (dark) 0.75f else 0.65f
    val routeLightness = if (dark) 0.75f else 0.35f
    fun route(degrees: Float) = turnedHue(primary, degrees, routeSaturation, routeLightness)
    return LockateColors(
        still = scheme.outline,
        onFoot = route(180f),
        onBicycle = route(90f),
        inVehicle = route(240f),
        members =
            (1..5).map { step ->
                ColorPair(
                    turnedHue(primary, step * 60f, memberSaturation, memberLightness),
                    if (dark) Color(0xFF1F1F1F) else Color.White,
                )
            },
    )
}

internal val LocalLockateColors = staticCompositionLocalOf {
    lockateColors(LightColorScheme, dark = false)
}
