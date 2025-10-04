package com.fourthwardai.ghostai.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fourthwardai.ghostai.R
import timber.log.Timber

@Composable
fun VoiceSettingsDialog(
    isShowing: Boolean,
    voiceSettings: VoiceSettings,
    onDismiss: () -> Unit,
    onConfirm: (VoiceSettings) -> Unit,
) {
    if (!isShowing) return

    var currentVoiceSettings by remember { mutableStateOf(voiceSettings) }

    val voicesForService =
        currentVoiceSettings.voicesByService[currentVoiceSettings.selectedService].orEmpty()
    val currentVoiceLabel =
        currentVoiceSettings.voicesByService[currentVoiceSettings.selectedService]?.firstOrNull { it.id == currentVoiceSettings.selectedVoiceId }?.displayName
            ?: "Select a voice"
    Timber.d("CGH: voicesForService: $voicesForService")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voice_settings), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.service), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))

                ServiceRadioRow(
                    label = stringResource(R.string.eleven_labs),
                    value = TtsService.ELEVENLABS,
                    selected = currentVoiceSettings.selectedService == TtsService.ELEVENLABS,
                    onSelect = {
                        currentVoiceSettings = currentVoiceSettings.copy(selectedService = it)
                    },
                )
                ServiceRadioRow(
                    label = stringResource(R.string.openai),
                    value = TtsService.OPENAI,
                    selected = currentVoiceSettings.selectedService == TtsService.OPENAI,
                    onSelect = {
                        currentVoiceSettings = currentVoiceSettings.copy(selectedService = it)
                    },
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                Spacer(Modifier.height(16.dp))

                Text("Voice", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))

                VoiceDropdown(
                    text = currentVoiceLabel,
                    options = voicesForService,
                    onSelected = { voice ->
                        currentVoiceSettings = currentVoiceSettings.copy(selectedVoiceId = voice.id)
                    },
                    enabled = voicesForService.isNotEmpty(),
                )

                if (voicesForService.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.no_voices_for_service),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(currentVoiceSettings) },
                enabled = voicesForService.isNotEmpty(),
            ) {
                Text(
                    stringResource(R.string.save),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ServiceRadioRow(
    label: String,
    value: TtsService,
    selected: Boolean,
    onSelect: (TtsService) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(value) }
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = { onSelect(value) })
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceDropdown(
    text: String,
    options: List<Voice>,
    onSelected: (Voice) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        TextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = !expanded },
            value = text,
            onValueChange = { /* read-only */ },
            label = { Text(stringResource(R.string.voice)) },
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { voice ->
                DropdownMenuItem(
                    text = { Text(voice.displayName) },
                    onClick = {
                        onSelected(voice)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Preview
@Composable
fun TtsSettingsDialogPreview() {
    val voicesByService = mapOf(
        TtsService.ELEVENLABS to listOf(
            Voice(id = "1", displayName = "Adam"),
            Voice(id = "2", displayName = "Bella"),
            Voice(id = "3", displayName = "Charlie"),
        ),
        TtsService.OPENAI to listOf(
            Voice(id = "4", displayName = "Alloy"),
            Voice(id = "5", displayName = "Echo"),
            Voice(id = "6", displayName = "Fable"),
        ),
    )
    var selectedService by remember { mutableStateOf(TtsService.ELEVENLABS) }
    var selectedVoiceId by remember { mutableStateOf<String>("1") }

    VoiceSettingsDialog(
        isShowing = true,
        voiceSettings = VoiceSettings(selectedService, selectedVoiceId, voicesByService),
        onDismiss = {},
        onConfirm = {},
    )
}
