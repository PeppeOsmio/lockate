package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import dev.sargunv.maplibrecompose.compose.CameraState
import dev.sargunv.maplibrecompose.compose.MaplibreMap
import dev.sargunv.maplibrecompose.compose.rememberStyleState
import dev.sargunv.maplibrecompose.core.BaseStyle
import dev.sargunv.maplibrecompose.core.MapOptions
import dev.sargunv.maplibrecompose.core.OrnamentOptions
import dev.sargunv.maplibrecompose.material3.controls.DisappearingCompassButton
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
fun ClusterBubble(center: DpOffset, count: Int) {
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
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor)
    }
}

@Composable
fun MemberMarker(
    center: DpOffset, member: MapPoint, isMe: Boolean, isOld: Boolean
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
    val circleSize = 16.dp
    val halfCircleSize = circleSize / 2
    Box(modifier = Modifier.offset(x = center.x - halfCircleSize, y = center.y - halfCircleSize)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(circleSize)
                .background(
                    if (isOld) backgroundColor.copy(alpha = 0.5f) else backgroundColor, CircleShape
                )
        )

        Text(
            text = member.name,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(120.dp)
                .offset(x = (-60).dp + halfCircleSize, y = 16.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = textColor
        )
    }
}

@Composable
fun MyMarker(
    center: DpOffset, isOld: Boolean
) {
    var backgroundColor = MaterialTheme.colorScheme.primary
    if (isOld) {
        backgroundColor = backgroundColor.copy(alpha = 0.5f)
    }
    // Circle is exactly on the coordinate
    val circleSize = 16.dp
    val halfCircleSize = circleSize / 2
    Box(modifier = Modifier.offset(x = center.x - halfCircleSize, y = center.y - halfCircleSize)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(circleSize)
                .background(
                    if (isOld) backgroundColor.copy(alpha = 0.5f) else backgroundColor, CircleShape
                )
        )
    }
}


@Composable
fun MembersMap(
    modifier: Modifier = Modifier,
    cameraState: CameraState,
    membersPoints: List<MapPoint>?,
    myPoint: MapPoint?,
    onTapMyLocation: () -> Unit
) {
    val styleState = rememberStyleState()
    val variant = if (isSystemInDarkTheme()) "dark" else "light"
    val apiKey = "121a0e377516f8bc"
    val mapStyle = remember(variant) {
        BaseStyle.Uri("https://api.protomaps.com/styles/v4/$variant/en.json?key=$apiKey")
    }

    val pointsClusters by remember(cameraState.position, myPoint) {
        Log.d("Clusters", cameraState.position.toString())
        derivedStateOf {
            Log.d("Clusters", "Computing clusters")
            val proj = cameraState.projection ?: return@derivedStateOf emptyList()
            val points = membersPoints?.toMutableList() ?: mutableListOf()
            clusterPoints(points.map { point ->
                val screen =
                    proj.screenLocationFromPosition(point.coordinates.toMapLibreComposePosition())
                Log.d("Clusters", point.coordinates.toString())
                Log.d("Clusters", screen.toString())
                point to screen
            })
        }
    }

    val myPointProjection by remember(cameraState.position, myPoint) {
        derivedStateOf {
            val proj = cameraState.projection ?: return@derivedStateOf null
            myPoint?.let {
                proj.screenLocationFromPosition(it.coordinates.toMapLibreComposePosition())
            }
        }
    }


    Box(
        modifier = modifier.clipToBounds()
    ) {
        MaplibreMap(
            cameraState = cameraState,
            styleState = styleState,
            options = MapOptions(
                ornamentOptions = OrnamentOptions(
                    isLogoEnabled = false,
                    isAttributionEnabled = false,
                    isCompassEnabled = true,
                    compassAlignment = Alignment.TopEnd,
                    isScaleBarEnabled = false,
                )
            ),
            baseStyle = mapStyle,
        ) {}

        // TODO consider returning to rendering via maplibre native layers

        Log.d("Clusters", "clusters count: ${pointsClusters.size}")

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
                )
            } else {
                ClusterBubble(
                    center = cluster.center, count = cluster.members.size
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
            MyMarker(center = DpOffset(x = it.x, y = it.y), isOld = myPoint!!.isOld)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            DisappearingCompassButton(cameraState, modifier = Modifier.align(Alignment.TopEnd))
        }

        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp), onClick = onTapMyLocation
        ) {
            Icon(
                painter = painterResource(R.drawable.outline_location_searching_24),
                contentDescription = ""
            )
        }
    }
}