package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.domain.anonymous_group.Circle
import org.maplibre.compose.map.MapState
import org.maplibre.spatialk.geojson.Position
import kotlin.math.abs
import kotlin.math.cos

enum class CircleStyle {
    // the circles of the group, with their name
    Normal,

    // the other circles while one is being edited
    Faint,

    // the circle being edited, with a dot on its centre
    Editing
}

private data class CircleProjection(val center: DpOffset, val radius: Dp)

private const val METERS_PER_DEGREE_OF_LATITUDE = 111_320.0

// null until the map is laid out
private fun projectCircle(mapState: MapState, circle: Circle): CircleProjection? {
    val center = mapState.screenLocationFromPosition(circle.center.toMapLibreComposePosition())
        ?: return null
    val degreesOfLongitude = circle.radiusMeters /
            (METERS_PER_DEGREE_OF_LATITUDE * cos(Math.toRadians(circle.center.latitude)))
    val edge = mapState.screenLocationFromPosition(
        Position(
            longitude = circle.center.longitude + degreesOfLongitude,
            latitude = circle.center.latitude
        )
    ) ?: return null
    return CircleProjection(
        center = DpOffset(center.x, center.y), radius = abs(edge.x.value - center.x.value).dp
    )
}

/** Draws [circle] over the map, following the camera and the size of the circle as they change. */
@Composable
fun CircleOverlay(mapState: MapState, circle: Circle, style: CircleStyle = CircleStyle.Normal) {
    val projection by remember(mapState.cameraPosition, circle) {
        derivedStateOf { projectCircle(mapState, circle) }
    }
    val (center, radius) = projection ?: return
    val color = if (style == CircleStyle.Editing) {
        MaterialTheme.colorScheme.primary
    } else MaterialTheme.colorScheme.tertiary
    val fillAlpha = when (style) {
        CircleStyle.Normal -> 0.18f
        CircleStyle.Faint -> 0.08f
        CircleStyle.Editing -> 0.25f
    }
    val borderWidth = when (style) {
        CircleStyle.Normal -> 2.dp
        CircleStyle.Faint -> 1.dp
        CircleStyle.Editing -> 3.dp
    }
    val dotRingColor = MaterialTheme.colorScheme.surface
    // drawn on a canvas, because a Box would be shrunk to the size of the map and stop being round
    // as soon as the circle is bigger than the screen
    Canvas(Modifier.fillMaxSize()) {
        val centerPx = Offset(center.x.toPx(), center.y.toPx())
        drawCircle(color.copy(alpha = fillAlpha), radius.toPx(), centerPx)
        drawCircle(color, radius.toPx(), centerPx, style = Stroke(borderWidth.toPx()))
        if (style == CircleStyle.Editing) {
            drawCircle(dotRingColor, 8.dp.toPx(), centerPx)
            drawCircle(color, 6.dp.toPx(), centerPx)
        }
    }
    if (style == CircleStyle.Editing) return
    Text(
        text = circle.name,
        style = MaterialTheme.typography.labelMedium,
        color = if (style == CircleStyle.Faint) color.copy(alpha = 0.6f) else color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .offset(center.x - 60.dp, center.y - 9.dp)
            .width(120.dp),
        textAlign = TextAlign.Center
    )
}
