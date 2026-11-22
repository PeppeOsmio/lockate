package com.peppeosmio.lockate.ui.composables

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.peppeosmio.lockate.ui.theme.LockateTheme

/** Shows a preview twice, in the light and in the dark theme */
@Preview(name = "Light", widthDp = 360)
@Preview(name = "Dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

@Composable
internal fun PreviewSurface(content: @Composable () -> Unit) {
    LockateTheme { Surface(content = content) }
}
