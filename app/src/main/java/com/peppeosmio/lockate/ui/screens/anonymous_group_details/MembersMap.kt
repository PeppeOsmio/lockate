package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.peppeosmio.lockate.R
import dev.sargunv.maplibrecompose.compose.CameraState
import dev.sargunv.maplibrecompose.compose.ClickResult
import dev.sargunv.maplibrecompose.compose.MaplibreMap
import dev.sargunv.maplibrecompose.compose.layer.CircleLayer
import dev.sargunv.maplibrecompose.compose.layer.SymbolLayer
import dev.sargunv.maplibrecompose.compose.rememberStyleState
import dev.sargunv.maplibrecompose.compose.source.rememberGeoJsonSource
import dev.sargunv.maplibrecompose.core.BaseStyle
import dev.sargunv.maplibrecompose.core.MapOptions
import dev.sargunv.maplibrecompose.core.OrnamentOptions
import dev.sargunv.maplibrecompose.core.source.GeoJsonData
import dev.sargunv.maplibrecompose.core.source.GeoJsonOptions
import dev.sargunv.maplibrecompose.expressions.ast.ColorLiteral
import dev.sargunv.maplibrecompose.expressions.dsl.const
import dev.sargunv.maplibrecompose.expressions.dsl.convertToString
import dev.sargunv.maplibrecompose.expressions.dsl.offset
import dev.sargunv.maplibrecompose.material3.controls.DisappearingCompassButton
import io.github.dellisd.spatialk.geojson.Feature
import io.github.dellisd.spatialk.geojson.FeatureCollection

@Composable
fun MembersMap(
    modifier: Modifier = Modifier,
    cameraState: CameraState,
    features: List<Feature>?,
    onTapMyLocation: () -> Unit
) {
    val styleState = rememberStyleState()
    val geoJsonOptions = remember { GeoJsonOptions() }
    val variant = if (isSystemInDarkTheme()) "dark" else "light"
    val apiKey = "121a0e377516f8bc"
    val mapStyle = remember(variant) {
        BaseStyle.Uri("https://api.protomaps.com/styles/v4/$variant/en.json?key=$apiKey")
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
        ) {

            val geoJsonSource = rememberGeoJsonSource(
                data = GeoJsonData.Features(FeatureCollection(features ?: emptyList())),
                options = geoJsonOptions
            )

            LaunchedEffect(features) {
                geoJsonSource.setData(
                    GeoJsonData.Features(FeatureCollection(features = features ?: emptyList()))
                )
            }


            CircleLayer(
                id = "membersCircles", source = geoJsonSource, onClick = { features ->
                    println(features.toString())
                    ClickResult.Consume
                }, radius = const(8.dp), color = ColorLiteral.of(MaterialTheme.colorScheme.primary)
            )

            SymbolLayer(
                id = "membersNames",
                source = geoJsonSource,
                onClick = { features ->
                    println(features.toString())
                    ClickResult.Consume
                },
                textField = dev.sargunv.maplibrecompose.expressions.dsl.Feature.get(const("name"))
                    .convertToString(),
                textSize = const(
                    TextUnit(
                        value = 1.0f, type = TextUnitType.Em
                    )
                ),
                textColor = ColorLiteral.of(MaterialTheme.colorScheme.onBackground),
                textOffset = offset(0.em, 1.5.em),
                textFont = const(listOf("Noto Sans Regular")),
                textAllowOverlap = const(true),
            )
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