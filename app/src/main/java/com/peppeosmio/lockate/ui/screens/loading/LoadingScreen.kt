package com.peppeosmio.lockate.ui.screens.loading

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoadingScreen(
    navigateToHome: () -> Unit,
    navigateToConnectionSettings: () -> Unit,
    viewModel: LoadingViewModel = koinViewModel()
) {
    LaunchedEffect(true) {
        navigateToHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
