package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.domain.anonymous_group.Place
import com.peppeosmio.lockate.ui.composables.MemberAvatar
import com.peppeosmio.lockate.ui.theme.LockateTheme
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.material3.AttributionLinks
import org.maplibre.compose.material3.DisappearingCompassButton
import org.maplibre.compose.material3.ExpandingAttributionButton
import org.maplibre.spatialk.geojson.Position

data class MembersCluster(
    val center: DpOffset,
    val members: List<MapPoint>,
)

fun clusterPoints(
    points: List<Pair<MapPoint, DpOffset>>,
    maxDistance: Dp = 60.dp, // threshold in screen pixels
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
            center = DpOffset(centerX.dp, centerY.dp),
            members = cluster.map { it.first },
        )
    }
}

@Composable
fun ClusterBubble(center: DpOffset, count: Int, onClick: () -> Unit) {
    val circleSize = 36.dp
    val halfCircleSize = circleSize / 2
    val backgroundColor = MaterialTheme.colorScheme.secondary
    val textColor = MaterialTheme.colorScheme.onSurface
    val text =
        if (count < 100) {
            "$count"
        } else {
            "99+"
        }
    Box(
        modifier =
            Modifier.offset(center.x - halfCircleSize, center.y - halfCircleSize)
                .size(circleSize)
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = textColor)
    }
}

private val BALLOON_PADDING = 4.dp
private val BALLOON_TAIL = 10.dp
private val AVATAR_SIZE = 40.dp

/**
 * A member as a white balloon holding their avatar, like Life360: the tip of the balloon is the
 * exact position. The selected member gets a halo in their colour; the name, with
 * [MapPoint.caption] when there is one, sits under the tip.
 */
@Composable
fun MemberMarker(tip: DpOffset, member: MapPoint, onClick: () -> Unit) {
    val diameter = AVATAR_SIZE + BALLOON_PADDING * 2
    val height = diameter + BALLOON_TAIL
    val halo = LockateTheme.colors.member(member.id).color.copy(alpha = 0.25f)
    Box(
        modifier =
            Modifier.offset(x = tip.x - diameter / 2, y = tip.y - height)
                .size(width = diameter, height = height)
                .clickable(interactionSource = null, indication = null, onClick = onClick)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val radius = size.width / 2
            if (member.isSelected) {
                drawCircle(halo, radius = radius + 6.dp.toPx(), center = Offset(radius, radius))
            }
            val balloon = balloonPath(radius, size.height, (BALLOON_TAIL / 2).toPx())
            // a soft shadow under the balloon, so it stands out on light maps
            translate(top = 1.dp.toPx()) { drawPath(balloon, Color.Black.copy(alpha = 0.25f)) }
            drawPath(balloon, Color.White)
        }
        MemberAvatar(
            modifier = Modifier.offset(BALLOON_PADDING, BALLOON_PADDING),
            name = member.name,
            memberId = member.id,
            activity = member.activity,
            paused = member.isOld,
            size = AVATAR_SIZE,
        )
    }
    val labelWidth = 140.dp
    Box(
        modifier =
            Modifier.offset(x = tip.x - labelWidth / 2, y = tip.y + 2.dp)
                .width(labelWidth)
                .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        ) {
            Text(
                text = listOfNotNull(member.name, member.caption).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** A place of [radius] with a tail down to the tip at [height], [halfTail] wide on each side */
private fun balloonPath(radius: Float, height: Float, halfTail: Float): Path {
    val joinY = radius + sqrt(radius * radius - halfTail * halfTail)
    return Path().apply {
        moveTo(radius, height)
        lineTo(radius - halfTail, joinY)
        arcTo(
            rect = Rect(0f, 0f, radius * 2, radius * 2),
            startAngleDegrees =
                Math.toDegrees(atan2(joinY - radius, -halfTail).toDouble()).toFloat(),
            sweepAngleDegrees =
                360f - 2 * Math.toDegrees(atan2(halfTail, joinY - radius).toDouble()).toFloat(),
            forceMoveTo = false,
        )
        close()
    }
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

    val pulse =
        rememberInfiniteTransition(label = "myMarkerPulse")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(tween(durationMillis = 2200, easing = LinearEasing)),
                label = "myMarkerPulseProgress",
            )

    Box(
        modifier =
            Modifier.offset(x = center.x - markerSize / 2, y = center.y - markerSize / 2)
                .size(markerSize)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)

            orientation?.let { heading ->
                val radius = beamRadius.toPx()
                drawArc(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    color.copy(alpha = if (isOld) 0.3f else 0.55f),
                                    Color.Transparent,
                                ),
                            center = c,
                            radius = radius,
                        ),
                    startAngle = beamStartAngle(heading, BEAM_SPREAD_DEGREES),
                    sweepAngle = BEAM_SPREAD_DEGREES,
                    useCenter = true,
                    topLeft = Offset(c.x - radius, c.y - radius),
                    size = Size(radius * 2, radius * 2),
                )
            }

            if (!isOld) {
                val minRadius = (dotSize / 2).toPx()
                val maxRadius = (dotSize * 1.9f).toPx()
                drawCircle(
                    color = color.copy(alpha = 0.3f * (1f - pulse.value)),
                    radius = minRadius + (maxRadius - minRadius) * pulse.value,
                    center = c,
                )
            }
        }

        Box(
            modifier =
                Modifier.align(Alignment.Center)
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
    places: List<Place>,
    selectedPlaceId: String?,
    onTapPlace: (placeId: String) -> Unit,
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

    val animatedMembersPoints = membersPoints?.map { point ->
        key(point.id) {
            val coordinates by animateCoordinatesAsState(point.coordinates)
            point.copy(coordinates = coordinates)
        }
    }
    val animatedMyPoint = myPoint?.let { point ->
        key(point.id) {
            val coordinates by animateCoordinatesAsState(point.coordinates)
            point.copy(coordinates = coordinates)
        }
    }

    val pointsClusters by
        remember(mapState.cameraPosition, animatedMembersPoints) {
            derivedStateOf {
                val points = animatedMembersPoints ?: emptyList()
                // null until the map is laid out
                clusterPoints(
                    points.mapNotNull { point ->
                        val screen =
                            mapState.screenLocationFromPosition(
                                point.coordinates.toMapLibreComposePosition()
                            ) ?: return@mapNotNull null
                        point to screen
                    }
                )
            }
        }

    val myPointProjection by
        remember(mapState.cameraPosition, animatedMyPoint) {
            derivedStateOf {
                animatedMyPoint?.let {
                    mapState.screenLocationFromPosition(it.coordinates.toMapLibreComposePosition())
                }
            }
        }

    Box(modifier = modifier.clipToBounds()) {
        MaplibreMap(modifier = Modifier.fillMaxSize(), state = mapState) {
            DisappearingCompassButton(
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = compassTopPadding)
            )
            // The data licences require showing the attribution declared by the style's sources
            ExpandingAttributionButton(
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        // leaves room for the my-location button, so the text wraps instead of
                        // going under it
                        .padding(start = 8.dp, end = 88.dp, bottom = bottomPadding + 8.dp),
                expandedContent = { attributions, textStyle ->
                    AttributionLinks(
                        attributions,
                        textStyle = textStyle,
                        breakWithinAttribution = true,
                    )
                },
            )
        }

        // TODO consider returning to rendering via maplibre native layers

        places.firstOrNull { it.id == selectedPlaceId }?.let { PlaceOverlay(mapState, it) }
        places.forEach { place ->
            key(place.id) {
                PlacePin(mapState, place, place.id == selectedPlaceId) {
                    onTapPlace(place.id)
                }
            }
        }

        // from the bottom: place pins, this phone's dot, the members, so a member's balloon is
        // never hidden by a pin
        myPointProjection?.let {
            MyMarker(
                center = DpOffset(x = it.x, y = it.y),
                orientation = myDeviceOrientation,
                isOld = myPoint!!.isOld,
            )
        }
        pointsClusters.forEach { cluster ->
            if (cluster.members.size == 1) {
                val member = cluster.members.first()
                MemberMarker(
                    tip = cluster.center,
                    member = member,
                    onClick = { onTapMember(member.id) },
                )
            } else {
                ClusterBubble(
                    center = cluster.center,
                    count = cluster.members.size,
                    onClick = {
                        coroutineScope.launch {
                            mapState.animateCamera(
                                CameraUpdate(
                                    center =
                                        Position(
                                            longitude =
                                                cluster.members
                                                    .map { it.coordinates.longitude }
                                                    .average(),
                                            latitude =
                                                cluster.members
                                                    .map { it.coordinates.latitude }
                                                    .average(),
                                        ),
                                    zoom = mapState.cameraPosition.zoom + 2,
                                )
                            )
                        }
                    },
                )
            }
            //          This is a debug yellow dot positioned exactly on the coordinates.
            //          Box(
            //              modifier = Modifier
            //                  .size(4.dp)
            //                  .offset(cluster.center.x, cluster.center.y)
            //                      // anchor = center of the place (16.dp)
            //                      translationX = -2.dp.toPx()
            //                      translationY = -2.dp.toPx()
            //                  }
            //                  .background(
            //                      Color.Yellow, CircleShape
            //                  ))
        }
    }
}
