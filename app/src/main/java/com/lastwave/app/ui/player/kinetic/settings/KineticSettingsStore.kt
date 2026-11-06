package com.lastwave.app.ui.player.kinetic.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Separate DataStore file so LastWave's own settings store is not touched. */
private val Context.kineticDataStore: DataStore<Preferences> by preferencesDataStore(name = "kinetic_lyrics")

private val KEY = stringPreferencesKey("kinetic_settings_v1")

/**
 * Process-wide holder of the kinetic-lyrics settings.
 *
 * [state] is null until the first load finishes (the lyrics panel waits for it so it
 * never flashes the wrong renderer). [update] changes the in-memory value immediately
 * (sliders preview live) and persists after a short debounce.
 */
object KineticSettingsStore {
    private val _state = MutableStateFlow<KineticSettings?>(null)
    val state: StateFlow<KineticSettings?> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    suspend fun ensureLoaded(context: Context) {
        if (_state.value != null) return
        val loaded = runCatching {
            KineticSettings.decode(context.applicationContext.kineticDataStore.data.first()[KEY])
        }.getOrDefault(KineticSettings.Default)
        if (_state.value == null) _state.value = loaded
    }

    fun update(context: Context, transform: (KineticSettings) -> KineticSettings) {
        val next = transform(_state.value ?: KineticSettings.Default)
        _state.value = next
        val app = context.applicationContext
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(300)
            runCatching { app.kineticDataStore.edit { it[KEY] = next.encode() } }
        }
    }
}
