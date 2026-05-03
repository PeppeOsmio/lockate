package com.peppeosmio.lockate.ui.composables

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundedSearchAppBar(
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    isExpanded: Boolean,
    searchPlaceholder: @Composable () -> Unit,
    query: String,
    onQueryChange: (query: String) -> Unit,
    leadingIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val focusRequester = remember { FocusRequester() }

    val animationDuration = 167

    val scrollFraction = scrollBehavior?.state?.contentOffset ?: 0f
    val isElevated = scrollFraction < 0f
    val elevateTransition =
        updateTransition(targetState = isElevated, label = "ElevateTransitionAnimation")
    val elevationDp by elevateTransition.animateDp(
        transitionSpec = { tween(animationDuration) }, label = "AppBarElevationAnimation"
    ) {
        if (it) 3.dp else 0.dp
    }
    val elevatedColor by elevateTransition.animateColor(
        transitionSpec = { tween(animationDuration) }) {
        if (it) MaterialTheme.colorScheme.surfaceColorAtElevation(
            elevationDp
        ) else Color.Transparent
    }

    val expandTransition =
        updateTransition(targetState = isExpanded, label = "ExpandTransitionAnimation")
    val appBarBorderRadius by expandTransition.animateDp(
        transitionSpec = { tween(durationMillis = animationDuration) },
        label = "CornerRadiusAnimation"
    ) {
        if (it) 0.dp else 24.dp
    }
    val appBarPadding by expandTransition.animateDp(
        transitionSpec = { tween(durationMillis = animationDuration) }, label = "PaddingAnimation"
    ) {
        if (it) 0.dp else 16.dp
    }
    val boxColor by expandTransition.animateColor(
        transitionSpec = { tween(durationMillis = (animationDuration * 0.75).toInt()) }, label = "BoxColorAnimation"
    ) {
        if (it) MaterialTheme.colorScheme.surfaceContainerHigh else elevatedColor
    }

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            focusRequester.requestFocus()
        }
    }

    val textFieldInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(textFieldInteractionSource) {
        textFieldInteractionSource.interactions.collect {
            if (it is PressInteraction.Release) {
                onTap()
            }
        }
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()

    Box(
        modifier = Modifier
            .background(color = boxColor)
            .padding(horizontal = appBarPadding)
    ) {
        TextField(
            modifier = modifier
                .fillMaxWidth()
                .padding(statusBarPadding)
                .padding(top = 16.dp, bottom = 8.dp)
                .height(56.dp)
                .focusRequester(focusRequester),
            value = query,
            onValueChange = onQueryChange,
            placeholder = { searchPlaceholder() },
            leadingIcon = {
                leadingIcon()
            },
            trailingIcon = {
                Row {
                    actions()
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(appBarBorderRadius), // pill shape
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            interactionSource = textFieldInteractionSource
        )
    }
}