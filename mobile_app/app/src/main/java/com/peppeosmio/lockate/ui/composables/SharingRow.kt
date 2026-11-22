package com.peppeosmio.lockate.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R

/**
 * Whether this phone shares its location with the group; the whole row toggles the switch.
 * [supporting] adds a second line, e.g. while the server can't be reached.
 */
@Composable
fun SharingRow(
    sharing: Boolean,
    onSharingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    ListItem(
        modifier =
            modifier.toggleable(
                value = sharing,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onSharingChange,
            ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (sharing) scheme.primaryContainer else scheme.surfaceContainerHighest
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (sharing) R.drawable.outline_location_searching_24
                            else R.drawable.outline_location_disabled_24
                        ),
                    contentDescription = null,
                    tint = if (sharing) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                )
            }
        },
        headlineContent = {
            Text(if (sharing) "Sharing your location" else "Not sharing your location")
        },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Switch(checked = sharing, onCheckedChange = null, enabled = enabled) },
    )
}

@ThemePreviews
@Composable
private fun SharingRowPreview() {
    PreviewSurface {
        Column {
            SharingRow(sharing = true, onSharingChange = {})
            SharingRow(
                sharing = true,
                onSharingChange = {},
                supporting = "Sends again once connected",
            )
            SharingRow(sharing = false, onSharingChange = {})
        }
    }
}
