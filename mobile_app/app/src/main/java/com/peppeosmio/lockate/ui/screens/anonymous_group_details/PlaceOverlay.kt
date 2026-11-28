package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.domain.anonymous_group.Place
import kotlin.math.abs
import kotlin.math.cos
import org.maplibre.compose.map.MapState
import org.maplibre.spatialk.geojson.Position

enum class PlaceStyle {
    // only the edge, for the place picked on the map
    Outline,

    // the other places while one is being edited
    Faint,

    // the place being edited, with a dot on its centre
    Editing,
}

private data class PlaceProjection(val center: DpOffset, val radius: Dp)

private const val METERS_PER_DEGREE_OF_LATITUDE = 111_320.0

// null until the map is laid out
private fun projectPlace(mapState: MapState, place: Place): PlaceProjection? {
    val center =
        mapState.screenLocationFromPosition(place.center.toMapLibreComposePosition()) ?: return null
    val degreesOfLongitude =
        place.radiusMeters /
            (METERS_PER_DEGREE_OF_LATITUDE * cos(Math.toRadians(place.center.latitude)))
    val edge =
        mapState.screenLocationFromPosition(
            Position(
                longitude = place.center.longitude + degreesOfLongitude,
                latitude = place.center.latitude,
            )
        ) ?: return null
    return PlaceProjection(
        center = DpOffset(center.x, center.y),
        radius = abs(edge.x.value - center.x.value).dp,
    )
}

/** Draws [place] over the map, following the camera and the size of the place as they change. */
@Composable
fun PlaceOverlay(mapState: MapState, place: Place, style: PlaceStyle = PlaceStyle.Outline) {
    val projection by
        remember(mapState.cameraPosition, place) {
            derivedStateOf { projectPlace(mapState, place) }
        }
    val (center, radius) = projection ?: return
    val color =
        if (style == PlaceStyle.Faint) {
            MaterialTheme.colorScheme.outline
        } else MaterialTheme.colorScheme.tertiary
    val fillAlpha =
        when (style) {
            PlaceStyle.Outline -> 0.06f
            PlaceStyle.Faint -> 0.08f
            PlaceStyle.Editing -> 0.25f
        }
    val borderWidth =
        when (style) {
            PlaceStyle.Outline -> 2.dp
            PlaceStyle.Faint -> 1.dp
            PlaceStyle.Editing -> 3.dp
        }
    val dotRingColor = MaterialTheme.colorScheme.surface
    // drawn on a canvas, because a Box would be shrunk to the size of the map and stop being round
    // as soon as the place is bigger than the screen
    Canvas(Modifier.fillMaxSize()) {
        val centerPx = Offset(center.x.toPx(), center.y.toPx())
        drawCircle(color.copy(alpha = fillAlpha), radius.toPx(), centerPx)
        drawCircle(color, radius.toPx(), centerPx, style = Stroke(borderWidth.toPx()))
        if (style == PlaceStyle.Editing) {
            drawCircle(dotRingColor, 8.dp.toPx(), centerPx)
            drawCircle(color, 6.dp.toPx(), centerPx)
        }
    }
    if (style != PlaceStyle.Faint) return
    Text(
        text = place.name,
        style = MaterialTheme.typography.labelMedium,
        color = color.copy(alpha = 0.6f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.offset(center.x - 60.dp, center.y - 9.dp).width(120.dp),
        textAlign = TextAlign.Center,
    )
}

/** A pin on the centre of [place], with its name; the place itself is only drawn when picked. */
@Composable
fun PlacePin(mapState: MapState, place: Place, isSelected: Boolean, onClick: () -> Unit) {
    val projection by
        remember(mapState.cameraPosition, place) {
            derivedStateOf {
                mapState.screenLocationFromPosition(place.center.toMapLibreComposePosition())?.let {
                    DpOffset(it.x, it.y)
                }
            }
        }
    val tip = projection ?: return
    val color =
        if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
    val pinSize = if (isSelected) 36.dp else 30.dp
    val haloSize = pinSize + 6.dp
    // the tip of the drop sits a little above the bottom of its icon
    val tipInset = 0.08f
    Icon(
        Icons.Filled.Place,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.surface,
        modifier =
            Modifier.offset(tip.x - haloSize / 2, tip.y - haloSize * (1 - tipInset)).size(haloSize),
    )
    Icon(
        Icons.Filled.Place,
        contentDescription = place.name,
        tint = color,
        modifier =
            Modifier.offset(tip.x - pinSize / 2, tip.y - pinSize * (1 - tipInset)).size(pinSize),
    )
    val touchSize = 48.dp
    Box(
        modifier =
            Modifier.offset(tip.x - touchSize / 2, tip.y - pinSize / 2 - touchSize / 2)
                .size(touchSize)
                .clickable(interactionSource = null, indication = null, onClick = onClick)
    )
    val labelWidth = 120.dp
    Box(
        modifier =
            Modifier.offset(tip.x - labelWidth / 2, tip.y + 2.dp)
                .width(labelWidth)
                .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
