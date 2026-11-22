package com.peppeosmio.lockate.ui.screens.map

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.anonymous_group.AnonymousGroup

/**
 * The ⋮ menu of the top bar. A group this phone can no longer use only offers to remove it; delete
 * is for admins only.
 */
@Composable
fun GroupMenuItems(
    group: AnonymousGroup,
    isAdmin: Boolean,
    onInvite: () -> Unit,
    onCircles: () -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit,
    onRemove: () -> Unit,
) {
    if (!group.isMember || !group.existsRemote) {
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
            text = { Text("Remove from this phone") },
            onClick = onRemove,
        )
        return
    }
    DropdownMenuItem(
        leadingIcon = {
            Icon(painterResource(R.drawable.baseline_person_add_24), contentDescription = null)
        },
        text = { Text("Invite people") },
        onClick = onInvite,
    )
    DropdownMenuItem(
        leadingIcon = {
            Icon(
                painterResource(R.drawable.baseline_radio_button_checked_24),
                contentDescription = null,
            )
        },
        text = { Text("Circles") },
        onClick = onCircles,
    )
    HorizontalDivider()
    DropdownMenuItem(
        leadingIcon = {
            Icon(painterResource(R.drawable.outline_logout_24), contentDescription = null)
        },
        text = { Text("Leave group") },
        onClick = onLeave,
    )
    if (isAdmin) {
        val error = MaterialTheme.colorScheme.error
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = error) },
            text = {
                Column {
                    Text("Delete group", color = error)
                    Text(
                        "For everyone · admins only",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            onClick = onDelete,
        )
    }
}
