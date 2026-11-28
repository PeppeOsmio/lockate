package com.peppeosmio.lockate.ui.composables

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * A [BottomSheetScaffold] that collapses all the way down: collapsed, only the drag handle shows,
 * above the gesture bar, and dragging it up reveals [sheetContent]. [content] receives the
 * collapsed height, e.g. to keep a floating button above the handle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleSheetScaffold(
    sheetContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scaffoldState: BottomSheetScaffoldState = rememberBottomSheetScaffoldState(),
    sheetMaxHeight: Dp = Dp.Unspecified,
    snackbarHost: @Composable (SnackbarHostState) -> Unit = { SnackbarHost(it) },
    content: @Composable (sheetPeekHeight: Dp) -> Unit,
) {
    val density = LocalDensity.current
    var dragHandleHeightPx by remember { mutableIntStateOf(0) }
    var layoutHeightPx by remember { mutableIntStateOf(0) }
    val sheetPeekHeight =
        with(density) {
            dragHandleHeightPx.toDp() + WindowInsets.navigationBars.getBottom(density).toDp()
        }

    // Collapsed, the gesture bar's strip under the handle would show the top of the content: it
    // fades in over the first stretch of the drag instead
    val fadePx = with(density) { CONTENT_FADE_DISTANCE.toPx() }
    val collapsedOffsetPx = { layoutHeightPx - with(density) { sheetPeekHeight.toPx() } }
    val sheetState = scaffoldState.bottomSheetState

    BottomSheetScaffold(
        modifier = modifier.onSizeChanged { layoutHeightPx = it.height },
        scaffoldState = scaffoldState,
        sheetPeekHeight = sheetPeekHeight,
        sheetDragHandle = {
            Box(modifier = Modifier.onSizeChanged { dragHandleHeightPx = it.height }) {
                BottomSheetDefaults.DragHandle()
            }
        },
        snackbarHost = snackbarHost,
        sheetContent = {
            Column(
                modifier =
                    Modifier.heightIn(max = sheetMaxHeight)
                        .graphicsLayer {
                            val offset =
                                runCatching { sheetState.requireOffset() }.getOrNull()
                                    ?: return@graphicsLayer
                            alpha = ((collapsedOffsetPx() - offset) / fadePx).coerceIn(0f, 1f)
                        }
                        .navigationBarsPadding()
                        .animateContentSize(
                            spring(
                                stiffness = Spring.StiffnessHigh,
                                visibilityThreshold = IntSize.VisibilityThreshold,
                            )
                        )
            ) {
                sheetContent()
            }
        },
    ) {
        content(sheetPeekHeight)
    }
}

private val CONTENT_FADE_DISTANCE = 48.dp
