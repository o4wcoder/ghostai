package com.fourthwardai.ghostai.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fourthwardai.ghostai.settings.TtsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object TtsPrefsKeys {
    val SERVICE = stringPreferencesKey("tts_service")
    val VOICE_ID = stringPreferencesKey("tts_voice_id")
}

val Context.ttsDataStore by preferencesDataStore(name = "tts_prefs")

class TtsPreferenceService(private val context: Context) {

    suspend fun saveSelection(service: TtsService, voiceId: String?) {
        context.ttsDataStore.edit { prefs ->
            prefs[TtsPrefsKeys.SERVICE] = service.name
            voiceId?.let { prefs[TtsPrefsKeys.VOICE_ID] = it } ?: prefs.remove(TtsPrefsKeys.VOICE_ID)
        }
    }

    val selectedService: Flow<TtsService?> =
        context.ttsDataStore.data.map { prefs ->
            prefs[TtsPrefsKeys.SERVICE]?.let { runCatching { TtsService.valueOf(it) }.getOrNull() }
        }

    val selectedVoiceId: Flow<String?> =
        context.ttsDataStore.data.map { prefs -> prefs[TtsPrefsKeys.VOICE_ID] }
}
