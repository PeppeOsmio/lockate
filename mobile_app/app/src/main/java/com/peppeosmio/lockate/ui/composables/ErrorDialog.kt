package com.peppeosmio.lockate.ui.composables

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.peppeosmio.lockate.utils.SnackbarErrorMessage

/**
 * Title says what failed ([SnackbarErrorMessage.text]), body says why ([SnackbarErrorMessage.errorInfo]).
 */
@Composable
fun ErrorDialog(error: SnackbarErrorMessage, onDismiss: () -> Unit) {
    AlertDialog(
        title = { Text(error.text) },
        text = error.errorInfo?.let { info ->
            {
                Text(if (info.title == error.text) info.body else "${info.title}\n${info.body}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Dismiss") } },
        confirmButton = {},
        onDismissRequest = onDismiss
    )
}
