package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import dev.sargunv.maplibrecompose.compose.CameraState
import dev.sargunv.maplibrecompose.compose.MaplibreMap
import dev.sargunv.maplibrecompose.compose.rememberStyleState
import dev.sargunv.maplibrecompose.core.BaseStyle
import dev.sargunv.maplibrecompose.core.MapOptions
import dev.sargunv.maplibrecompose.core.OrnamentOptions
import dev.sargunv.maplibrecompose.material3.controls.DisappearingCompassButton

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
        return kotlin.math.sqrt(dx * dx + dy * dy)
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
fun ClusterBubble(modifier: Modifier, count: Int, containsMe: Boolean) {
    val backgroundColor = if (containsMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }
    val textColor = if (containsMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val text = if (count < 100) {
        "$count"
    } else {
        "99+"
    }
    Box(
        modifier = modifier
            .size(36.dp)
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor)
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

    val pointsClusters by remember(cameraState.position, membersPoints, myPoint) {
        Log.d("Clusters", cameraState.position.toString())
        derivedStateOf {
            Log.d("Clusters", "Computing clusters")
            val proj = cameraState.projection ?: return@derivedStateOf emptyList()
            val points = membersPoints?.toMutableList() ?: mutableListOf()
            myPoint?.let {
                points.add(it)
            }
            clusterPoints(points.map { point ->
                val screen =
                    proj.screenLocationFromPosition(point.coordinates.toMapLibreComposePosition())
                Log.d("Clusters", point.coordinates.toString())
                Log.d("Clusters", screen.toString())
                point to screen
            })
        }
    }


    Box(modifier = modifier.clipToBounds()) {
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
            var size by remember { mutableStateOf(IntSize.Zero) }
            val positioningModifier = fun(modifier: Modifier): Modifier {
                return modifier
                    .offset(
                        x = cluster.center.x, y = cluster.center.y
                    )
                    .onGloballyPositioned { size = it.size }
                    .graphicsLayer {
                        translationX = -size.width / 2f
                        translationY = -size.height / 2f
                    }
            }
            val containsMe = cluster.members.any {
                it.id == myPoint?.id
            }
            if (cluster.members.size == 1) {
                // Draw normal single person
                val member = cluster.members.first()
                Column(
                    modifier = positioningModifier(Modifier.width(120.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    var backgroundColor = if (containsMe) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                    if(member.isOld) {
                        backgroundColor = backgroundColor.copy(alpha = 0.5f)
                    }
                    val textColor = if (containsMe) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(backgroundColor, CircleShape)
                    )
                    Text(
                        text = member.name,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color = textColor,
                    )
                }
            } else {
                // Draw cluster bubble
                ClusterBubble(
                    modifier = positioningModifier(Modifier),
                    count = cluster.members.size,
                    containsMe = containsMe
                )
            }
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