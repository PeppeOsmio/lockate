package com.peppeosmio.lockate.ui.screens.map

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** A confirmation whose action can't be undone, so the action is in the error colour */
@Composable
private fun DestructiveDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = { Text(title) },
        text = { Text(text) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(confirm)
            }
        },
        onDismissRequest = onDismiss,
    )
}

@Composable
fun LeaveGroupDialog(groupName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    DestructiveDialog(
        title = "Leave $groupName?",
        text =
            "You stop sharing with $groupName and it disappears from this phone. Coming back " +
                "takes a new invite.",
        confirm = "Leave",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun DeleteGroupDialog(
    groupName: String,
    membersCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DestructiveDialog(
        title = "Delete $groupName for everyone?",
        text =
            "$groupName and its places are deleted for " +
                (if (membersCount == 1) "you" else "all $membersCount members") +
                ". This can't be undone.",
        confirm = "Delete",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun MakeAdminDialog(memberName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        title = { Text("Make $memberName an admin?") },
        text = {
            Text(
                "They will be able to delete the group and make other members admins. This " +
                    "can't be undone from the app."
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Make admin") } },
        onDismissRequest = onDismiss,
    )
}
