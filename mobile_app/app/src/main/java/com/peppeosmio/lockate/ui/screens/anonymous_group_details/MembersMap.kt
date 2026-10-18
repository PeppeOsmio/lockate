package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.spatialk.geojson.Position
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.material3.AttributionLinks
import org.maplibre.compose.material3.DisappearingCompassButton
import org.maplibre.compose.material3.ExpandingAttributionButton
import kotlin.math.sqrt

data class MembersCluster(
    val center: DpOffset, val members: List<MapPoint>
)

fun clusterPoints(
    points: List<Pair<MapPoint, DpOffset>>, maxDistance: Dp = 60.dp   // threshold in screen pixels
): List<MembersCluster> {

    if (points.isEmpty()) return emptyList()

    val clusters = mutableListOf<MutableList<Pair<MapPoint, DpOffset>>>()
    val maxDistPx = maxDistance.value

    fun distance(a: DpOffset, b: DpOffset): Float {
        val dx = a.x.value - b.x.value
        val dy = a.y.value - b.y.value
        return sqrt(dx * dx + dy * dy)
    }

    for ((point, screenPos) in points) {
        var added = false

        // Try to assign point to an existing cluster
        for (cluster in clusters) {
            // current center of cluster
            val centerX = cluster.map { it.second.x.value }.average().toFloat()
            val centerY = cluster.map { it.second.y.value }.average().toFloat()
            val center = DpOffset(centerX.dp, centerY.dp)

            if (distance(screenPos, center) <= maxDistPx) {
                cluster += (point to screenPos)
                added = true
                break
            }
        }

        // Otherwise create a new cluster
        if (!added) {
            clusters += mutableListOf(point to screenPos)
        }
    }

    return clusters.map { cluster ->
        val centerX = cluster.map { it.second.x.value }.average().toFloat()
        val centerY = cluster.map { it.second.y.value }.average().toFloat()

        MembersCluster(
            center = DpOffset(centerX.dp, centerY.dp), members = cluster.map { it.first })
    }
}

@Composable
fun ClusterBubble(center: DpOffset, count: Int, onClick: () -> Unit) {
    val circleSize = 36.dp
    val halfCircleSize = circleSize / 2
    val backgroundColor = MaterialTheme.colorScheme.secondary
    val textColor = MaterialTheme.colorScheme.onSurface
    val text = if (count < 100) {
        "$count"
    } else {
        "99+"
    }
    Box(
        modifier = Modifier
            .offset(center.x - halfCircleSize, center.y - halfCircleSize)
            .size(circleSize)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor)
    }
}

@Composable
fun MemberMarker(
    center: DpOffset, member: MapPoint, isMe: Boolean, isOld: Boolean, onClick: () -> Unit
) {
    var backgroundColor = if (isMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }
    if (member.isOld) {
        backgroundColor = backgroundColor.copy(alpha = 0.5f)
    }
    val textColor = if (isMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }
    // Circle is exactly on the coordinate
    val circleSize = if (member.isSelected) 22.dp else 16.dp
    val touchTargetSize = 48.dp
    Box(
        modifier = Modifier
            .offset(x = center.x - touchTargetSize / 2, y = center.y - touchTargetSize / 2)
            .size(touchTargetSize)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .then(
                    if (member.isSelected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .background(
                    if (isOld) backgroundColor.copy(alpha = 0.5f) else backgroundColor, CircleShape
                )
        )
    }

    val labelWidth = 120.dp
    Text(
        text = member.name,
        modifier = Modifier
            .offset(x = center.x - labelWidth / 2, y = center.y + circleSize / 2)
            .width(labelWidth)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        color = textColor
    )
}

/**
 * The user's own position, drawn like Google Maps' blue dot: a white-ringed dot with a soft pulse
 * and a beam fading out in the heading direction.
 */
@Composable
fun MyMarker(center: DpOffset, orientation: Float?, isOld: Boolean) {
    val color = MaterialTheme.colorScheme.primary.let { if (isOld) it.copy(alpha = 0.5f) else it }
    val dotSize = 20.dp
    val ringWidth = 3.dp
    val beamRadius = 56.dp
    val markerSize = beamRadius * 2

    val pulse = rememberInfiniteTransition(label = "myMarkerPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2200, easing = LinearEasing)),
        label = "myMarkerPulseProgress"
    )

    Box(
        modifier = Modifier
            .offset(x = center.x - markerSize / 2, y = center.y - markerSize / 2)
            .size(markerSize)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)

            orientation?.let { heading ->
                val radius = beamRadius.toPx()
                drawArc(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = if (isOld) 0.3f else 0.55f), Color.Transparent),
                        center = c,
                        radius = radius
                    ),
                    startAngle = beamStartAngle(heading, BEAM_SPREAD_DEGREES),
                    sweepAngle = BEAM_SPREAD_DEGREES,
                    useCenter = true,
                    topLeft = Offset(c.x - radius, c.y - radius),
                    size = Size(radius * 2, radius * 2)
                )
            }

            if (!isOld) {
                val minRadius = (dotSize / 2).toPx()
                val maxRadius = (dotSize * 1.9f).toPx()
                drawCircle(
                    color = color.copy(alpha = 0.3f * (1f - pulse.value)),
                    radius = minRadius + (maxRadius - minRadius) * pulse.value,
                    center = c
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .shadow(elevation = 3.dp, shape = CircleShape)
                .size(dotSize)
                .background(Color.White, CircleShape)
                .padding(ringWidth)
                .background(color, CircleShape)
        )
    }
}

private const val BEAM_SPREAD_DEGREES = 70f

@Composable
fun MembersMap(
    modifier: Modifier = Modifier,
    mapState: MapState,
    membersPoints: List<MapPoint>?,
    myPoint: MapPoint?,
    myDeviceOrientation: Float?,
    compassTopPadding: Dp,
    bottomPadding: Dp,
    onTapMember: (memberId: String) -> Unit,
    onMapGesture: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(mapState) {
        snapshotFlow { mapState.cameraMoveReason }
            .filter { it == CameraMoveReason.Gesture }
            .collect { onMapGesture() }
    }

    val pointsClusters by remember(mapState.cameraPosition, membersPoints) {
        Log.d("Clusters", mapState.cameraPosition.toString())
        derivedStateOf {
            Log.d("Clusters", "Computing clusters")
            val points = membersPoints?.toMutableList() ?: mutableListOf()
            // null until the map is laid out
            clusterPoints(points.mapNotNull { point ->
                val screen =
                    mapState.screenLocationFromPosition(point.coordinates.toMapLibreComposePosition())
                        ?: return@mapNotNull null
                Log.d("Clusters", point.coordinates.toString())
                Log.d("Clusters", screen.toString())
                point to screen
            })
        }
    }

    val myPointProjection by remember(mapState.cameraPosition, myPoint) {
        derivedStateOf {
            myPoint?.let {
                mapState.screenLocationFromPosition(it.coordinates.toMapLibreComposePosition())
            }
        }
    }


    Box(
        modifier = modifier.clipToBounds()
    ) {
        MaplibreMap(modifier = Modifier.fillMaxSize(), state = mapState) {
            DisappearingCompassButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = compassTopPadding)
            )
            // The data licences require showing the attribution declared by the style's sources
            ExpandingAttributionButton(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    // leaves room for the my-location button, so the text wraps instead of going under it
                    .padding(start = 8.dp, end = 88.dp, bottom = bottomPadding + 8.dp),
                expandedContent = { attributions, textStyle ->
                    AttributionLinks(attributions, textStyle = textStyle, breakWithinAttribution = true)
                }
            )
        }

        // TODO consider returning to rendering via maplibre native layers

        pointsClusters.forEach { cluster ->
            val containsMe = cluster.members.any {
                it.id == myPoint?.id
            }

            if (cluster.members.size == 1) {
                val member = cluster.members.first()
                MemberMarker(
                    center = cluster.center,
                    isMe = containsMe,
                    member = member,
                    isOld = member.isOld,
                    onClick = { onTapMember(member.id) }
                )
            } else {
                ClusterBubble(
                    center = cluster.center, count = cluster.members.size, onClick = {
                        coroutineScope.launch {
                            mapState.animateCamera(
                                CameraUpdate(
                                    center = Position(
                                        longitude = cluster.members.map { it.coordinates.longitude }.average(),
                                        latitude = cluster.members.map { it.coordinates.latitude }.average()
                                    ), zoom = mapState.cameraPosition.zoom + 2
                                )
                            )
                        }
                    }
                )
            }
//          This is a debug yellow dot positioned exactly on the coordinates.
//          Box(
//              modifier = Modifier
//                  .size(4.dp)
//                  .offset(cluster.center.x, cluster.center.y)
//                      // anchor = center of the circle (16.dp)
//                      translationX = -2.dp.toPx()
//                      translationY = -2.dp.toPx()
//                  }
//                  .background(
//                      Color.Yellow, CircleShape
//                  ))
        }
        myPointProjection?.let {
            MyMarker(
                center = DpOffset(x = it.x, y = it.y),
                orientation = myDeviceOrientation,
                isOld = myPoint!!.isOld
            )
        }

    }
}
