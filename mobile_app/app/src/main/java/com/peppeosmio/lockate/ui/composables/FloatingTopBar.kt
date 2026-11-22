package com.peppeosmio.lockate.ui.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The 56 dp pill floating over the map: [navigation] on the left, the title with an optional
 * subtitle (tapping it calls [onTitleClick], e.g. to switch group), [actions] on the right.
 */
@Composable
fun FloatingTopBar(
    title: String,
    navigation: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation()
            Box(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick)
                            else Modifier
                        )
                        .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            actions()
        }
    }
}

@ThemePreviews
@Composable
private fun FloatingTopBarPreview() {
    PreviewSurface {
        FloatingTopBar(
            modifier = Modifier.padding(12.dp),
            title = "Family",
            subtitle = "3 members",
            onTitleClick = {},
            navigation = {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Menu, contentDescription = "Servers and settings")
                }
            },
            actions = {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Group menu")
                }
            },
        )
    }
}
