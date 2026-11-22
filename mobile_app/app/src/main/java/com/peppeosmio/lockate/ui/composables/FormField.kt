package com.peppeosmio.lockate.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R

/**
 * A single-line outlined field. [password] hides the text behind an eye toggle; [monospace] is for
 * codes read aloud, like an invite code.
 */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorText: String? = null,
    password: Boolean = false,
    monospace: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val hidden = password && !visible
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        isError = errorText != null,
        supportingText = (errorText ?: supportingText)?.let { { Text(it) } },
        singleLine = true,
        textStyle =
            if (monospace) LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
            else LocalTextStyle.current,
        keyboardOptions =
            if (password) keyboardOptions.copy(keyboardType = KeyboardType.Password)
            else keyboardOptions,
        visualTransformation =
            if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon =
            if (password) {
                {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            painter =
                                painterResource(
                                    if (visible) R.drawable.outline_visibility_off_24
                                    else R.drawable.outline_visibility_24
                                ),
                            contentDescription = if (visible) "Hide $label" else "Show $label",
                        )
                    }
                }
            } else null,
    )
}

@ThemePreviews
@Composable
private fun FormFieldPreview() {
    PreviewSurface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FormField(value = "Family", onValueChange = {}, label = "Group name")
            FormField(
                value = "",
                onValueChange = {},
                label = "API key",
                supportingText = "Only if the server asks for one",
                password = true,
            )
            FormField(
                value = "5211-dose-slick-dance",
                onValueChange = {},
                label = "Invite code",
                monospace = true,
            )
            FormField(
                value = "ftp://x",
                onValueChange = {},
                label = "Server address",
                errorText = "Not a valid address",
            )
        }
    }
}
