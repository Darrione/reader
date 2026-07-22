package com.reader.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Default TTS speed and the app's default reading voice language (English). */
const val DEFAULT_TTS_SPEED = 1.0f
const val MIN_TTS_SPEED = 0.5f
const val MAX_TTS_SPEED = 3.0f
const val DEFAULT_TTS_LANGUAGE = "en"

private val TTS_SPEED_KEY = floatPreferencesKey("tts_speed")

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val ttsSpeed: Flow<Float> = dataStore.data.map { prefs ->
        prefs[TTS_SPEED_KEY] ?: DEFAULT_TTS_SPEED
    }

    suspend fun setTtsSpeed(speed: Float) {
        val clamped = speed.coerceIn(MIN_TTS_SPEED, MAX_TTS_SPEED)
        dataStore.edit { it[TTS_SPEED_KEY] = clamped }
    }
}
