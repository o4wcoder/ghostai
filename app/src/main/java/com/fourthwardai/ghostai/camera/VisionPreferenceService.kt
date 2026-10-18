package com.fourthwardai.ghostai.camera

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object VisionPrefsKeys {
    val VISION_ENABLED = booleanPreferencesKey("vision_enabled")
}

val Context.visionDataStore by preferencesDataStore(name = "vision_prefs")

class VisionPreferenceService(private val context: Context) {

    suspend fun setVisionEnabled(enabled: Boolean) {
        context.visionDataStore.edit { prefs -> prefs[VisionPrefsKeys.VISION_ENABLED] = enabled }
    }

    /** Vision is on by default; the user doesn't have to opt in. */
    val visionEnabled: Flow<Boolean> =
        context.visionDataStore.data.map { prefs -> prefs[VisionPrefsKeys.VISION_ENABLED] ?: true }
}
