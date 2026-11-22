package com.peppeosmio.lockate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/**
 * The orange Material 3 theme, light or dark like the system. Material's own typography and shapes
 * already are the M3 scale (Roboto, 28 dp for sheets, 16 dp for FABs and cards), so only the
 * colours are the app's.
 */
@Composable
fun LockateTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val lockateColors = remember(colorScheme) { lockateColors(colorScheme, darkTheme) }
    CompositionLocalProvider(LocalLockateColors provides lockateColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content,
        )
    }
}

object LockateTheme {
    val colors: LockateColors
        @Composable @ReadOnlyComposable get() = LocalLockateColors.current
}
