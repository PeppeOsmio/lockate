package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
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

data class Cluster(
    val center: DpOffset, val members: List<MapPoint>
)

fun clusterPoints(
    points: List<Pair<MapPoint, DpOffset>>, cellSize: Dp = 80.dp
): List<Cluster> {
    val buckets = mutableMapOf<Pair<Int, Int>, MutableList<Pair<MapPoint, DpOffset>>>()

    for ((point, screen) in points) {
        val key =
            (screen.x.value.toInt() / cellSize.value.toInt()) to (screen.y.value.toInt() / cellSize.value.toInt())
        buckets.getOrPut(key) { mutableListOf() }.add(point to screen)
    }

    return buckets.values.map { bucket ->
        val centerX = bucket.map { it.second.x.value }.average().toFloat()
        val centerY = bucket.map { it.second.y.value }.average().toFloat()
        Cluster(DpOffset(centerX.dp, centerY.dp), bucket.map { it.first })
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


    Box(modifier = modifier) {
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

//        projectedPoints.forEach { (point, screen) ->
//            Column(
//                modifier = Modifier.offset(
//                    x = screen.x, y = screen.y
//                ), horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//                Box(
//                    modifier = Modifier
//                        .size(16.dp)
//                        .background(MaterialTheme.colorScheme.secondary, CircleShape)
//                )
//                Text(point.name)
//            }
//        }

        Log.d("Clusters", "clusters count: ${pointsClusters.size}")

        pointsClusters.forEach { cluster ->
            var size by remember { mutableStateOf(IntSize.Zero) }
            val pointModifier = Modifier
                .offset(
                    x = cluster.center.x, y = cluster.center.y
                )
                .onGloballyPositioned { size = it.size }
                .graphicsLayer {
                    translationX = -size.width / 2f
                    translationY = -size.height / 2f
                }
            val containsMe = cluster.members.any {
                it.id == myPoint?.id
            }
            if (cluster.members.size == 1) {
                // Draw normal single person
                val member = cluster.members.first()
                Column(
                    modifier = pointModifier, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val backgroundColor = if (containsMe) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
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
                    Text(member.name, color = textColor)
                }
            } else {
                // Draw cluster bubble
                ClusterBubble(
                    modifier = pointModifier, count = cluster.members.size, containsMe = containsMe
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