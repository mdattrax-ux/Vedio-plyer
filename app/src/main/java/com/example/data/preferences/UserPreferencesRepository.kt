package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auraplayer_preferences")

data class UserPreferences(
    val defaultSpeed: Float = 1.0f,
    val resumePlayback: Boolean = true,
    val backgroundPlaybackEnabled: Boolean = true,
    val repeatMode: Int = 0, // 0 = OFF, 1 = ONE, 2 = ALL
    val doubleTapSeekSeconds: Int = 10,
    val defaultAspectRatio: String = "FIT"
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val DEFAULT_SPEED = floatPreferencesKey("default_playback_speed")
        val RESUME_PLAYBACK = booleanPreferencesKey("resume_playback")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val DOUBLE_TAP_SEEK_SECONDS = intPreferencesKey("double_tap_seek_seconds")
        val DEFAULT_ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        UserPreferences(
            defaultSpeed = preferences[PreferencesKeys.DEFAULT_SPEED] ?: 1.0f,
            resumePlayback = preferences[PreferencesKeys.RESUME_PLAYBACK] ?: true,
            backgroundPlaybackEnabled = preferences[PreferencesKeys.BACKGROUND_PLAYBACK] ?: true,
            repeatMode = preferences[PreferencesKeys.REPEAT_MODE] ?: 0,
            doubleTapSeekSeconds = preferences[PreferencesKeys.DOUBLE_TAP_SEEK_SECONDS] ?: 10,
            defaultAspectRatio = preferences[PreferencesKeys.DEFAULT_ASPECT_RATIO] ?: "FIT"
        )
    }

    suspend fun updateDefaultSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_SPEED] = speed
        }
    }

    suspend fun updateResumePlayback(enable: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RESUME_PLAYBACK] = enable
        }
    }

    suspend fun updateBackgroundPlayback(enable: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKGROUND_PLAYBACK] = enable
        }
    }

    suspend fun updateRepeatMode(repeatMode: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REPEAT_MODE] = repeatMode
        }
    }

    suspend fun updateDoubleTapSeek(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DOUBLE_TAP_SEEK_SECONDS] = seconds
        }
    }

    suspend fun updateDefaultAspectRatio(ratio: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_ASPECT_RATIO] = ratio
        }
    }
}
