package com.inputleaf.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    Box(Modifier.fillMaxSize()) {
        LeafNavigation(viewModel)
    }
}
