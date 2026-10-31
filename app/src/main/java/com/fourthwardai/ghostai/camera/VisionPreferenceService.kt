package com.fourthwardai.ghostai.camera

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object VisionPrefsKeys {
    val VISION_ENABLED = booleanPreferencesKey("vision_enabled")
    val GREET_VISITORS_ENABLED = booleanPreferencesKey("greet_visitors_enabled")
}

val Context.visionDataStore by preferencesDataStore(name = "vision_prefs")

class VisionPreferenceService(private val context: Context) {

    suspend fun setVisionEnabled(enabled: Boolean) {
        context.visionDataStore.edit { prefs -> prefs[VisionPrefsKeys.VISION_ENABLED] = enabled }
    }

    /** Vision is on by default; the user doesn't have to opt in. */
    val visionEnabled: Flow<Boolean> =
        context.visionDataStore.data.map { prefs -> prefs[VisionPrefsKeys.VISION_ENABLED] ?: true }

    suspend fun setGreetVisitorsEnabled(enabled: Boolean) {
        context.visionDataStore.edit { prefs -> prefs[VisionPrefsKeys.GREET_VISITORS_ENABLED] = enabled }
    }

    /** Off by default: it keeps the camera analyzing continuously, so it's a mode switched on for Halloween night. */
    val greetVisitorsEnabled: Flow<Boolean> =
        context.visionDataStore.data.map { prefs -> prefs[VisionPrefsKeys.GREET_VISITORS_ENABLED] ?: false }
}
