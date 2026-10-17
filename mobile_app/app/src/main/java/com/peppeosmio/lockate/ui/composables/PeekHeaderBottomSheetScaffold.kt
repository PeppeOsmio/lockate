package com.peppeosmio.lockate.ui.composables

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

/**
 * A [BottomSheetScaffold] whose collapsed height is measured instead of fixed: collapsed, the sheet
 * shows the drag handle and [sheetHeader] only, and dragging it up reveals [sheetBody].
 * [content] receives the collapsed sheet height, e.g. to keep a floating button above the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeekHeaderBottomSheetScaffold(
    sheetHeader: @Composable () -> Unit,
    sheetBody: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    scaffoldState: BottomSheetScaffoldState = rememberBottomSheetScaffoldState(),
    sheetMaxHeight: Dp = Dp.Unspecified,
    snackbarHost: @Composable (SnackbarHostState) -> Unit = { SnackbarHost(it) },
    content: @Composable (sheetPeekHeight: Dp) -> Unit
) {
    val density = LocalDensity.current
    var dragHandleHeightPx by remember { mutableIntStateOf(0) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    // Before the first measurement, Material's default avoids collapsing the sheet to nothing
    val isMeasured = headerHeightPx != 0
    val targetPeekHeight = if (isMeasured) {
        with(density) { (dragHandleHeightPx + headerHeightPx).toDp() }
    } else {
        BottomSheetDefaults.SheetPeekHeight
    }
    // The first measurement snaps, so the sheet doesn't grow when the screen opens. Stiff springs,
    // since the content switches instantly and only the height catches up
    var animatePeekHeight by remember { mutableStateOf(false) }
    val sheetPeekHeight by animateDpAsState(
        targetValue = targetPeekHeight,
        animationSpec = if (animatePeekHeight) spring(stiffness = Spring.StiffnessHigh) else snap(),
        label = "sheetPeekHeight"
    )
    LaunchedEffect(isMeasured) {
        if (isMeasured) animatePeekHeight = true
    }

    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = scaffoldState,
        sheetPeekHeight = sheetPeekHeight,
        sheetDragHandle = {
            Box(modifier = Modifier.onSizeChanged { dragHandleHeightPx = it.height }) {
                BottomSheetDefaults.DragHandle()
            }
        },
        snackbarHost = snackbarHost,
        sheetContent = {
            Column(modifier = Modifier
                .heightIn(max = sheetMaxHeight)
                .animateContentSize(
                    spring(stiffness = Spring.StiffnessHigh, visibilityThreshold = IntSize.VisibilityThreshold)
                )) {
                // The header ends with the gesture bar inset, so the start of the body never shows
                // behind the gesture bar when the sheet is collapsed
                Column(modifier = Modifier.onSizeChanged { headerHeightPx = it.height }) {
                    sheetHeader()
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
                sheetBody?.let {
                    Column(modifier = Modifier.navigationBarsPadding()) { it() }
                }
            }
        }) {
        content(sheetPeekHeight)
    }
}
