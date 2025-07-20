package com.example.ghostai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.ghostai.model.GhostUiState
import com.example.ghostai.ui.theme.GhostAITheme

@Composable
fun MainScreen(
    ghostUiState: GhostUiState,
    onGhostTouched: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        GhostWithMist(
            ghostUiState = ghostUiState,
            onGhostThouched = onGhostTouched,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewMainScreen() {
    GhostAITheme {
        MainScreen(ghostUiState = getGhostUiStatePreviewUiState(), onGhostTouched = {})
    }
}
