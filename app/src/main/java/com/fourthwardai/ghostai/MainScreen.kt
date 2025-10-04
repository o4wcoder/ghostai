package com.fourthwardai.ghostai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.fourthwardai.ghostai.model.DeviceSettings
import com.fourthwardai.ghostai.model.FormFactor
import com.fourthwardai.ghostai.model.GhostUiState
import com.fourthwardai.ghostai.settings.VoiceSettings
import com.fourthwardai.ghostai.settings.VoiceSettingsDialog
import com.fourthwardai.ghostai.ui.theme.GhostAITheme

@Composable
fun MainScreen(
    deviceSettings: DeviceSettings,
    ghostUiState: GhostUiState,
    onGhostTouched: () -> Unit,
    onShowVoiceSettings: () -> Unit,
    onHideVoiceSettings: () -> Unit,
    onDismissMissingOpenAIKeyDialog: () -> Unit,
    onUpdateVoiceSettings: (VoiceSettings) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        GhostWithMist(
            deviceSettings = deviceSettings,
            ghostUiState = ghostUiState,
            onGhostTouched = onGhostTouched,
            onShowVoiceSettings = onShowVoiceSettings,
            modifier = Modifier.align(Alignment.Center),
        )

        VoiceSettingsDialog(isShowing = ghostUiState.showSettingsDialog, voiceSettings = ghostUiState.voiceSettings, onDismiss = {
            onHideVoiceSettings()
        }, onConfirm = onUpdateVoiceSettings)

        if (ghostUiState.showMissingOpenAIKeyDialog) {
            MissingOpenAiKeyDialog(onDismiss = { onDismissMissingOpenAIKeyDialog() })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewMainScreen() {
    GhostAITheme {
        MainScreen(
            deviceSettings = DeviceSettings(FormFactor.Phone, 2f, 60f),
            ghostUiState = getGhostUiStatePreviewUiState(),
            onGhostTouched = {},
            onShowVoiceSettings = {},
            onHideVoiceSettings = {},
            onDismissMissingOpenAIKeyDialog = {},
            onUpdateVoiceSettings = {},
        )
    }
}
