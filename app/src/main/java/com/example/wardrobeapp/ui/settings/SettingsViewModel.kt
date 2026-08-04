package com.example.wardrobeapp.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wardrobeapp.data.remote.ai.AiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.wardrobeapp.dataStore
import com.example.wardrobeapp.data.repository.SettingsRepository
import com.example.wardrobeapp.data.repository.WeatherRepository
import com.example.wardrobeapp.WardrobeApplication



private object PrefKeys {
    val DARK_MODE    = booleanPreferencesKey("dark_mode")
    val UNIT_SYSTEM  = stringPreferencesKey("unit_system")
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val weatherRepository: WeatherRepository,
    private val aiClient: AiClient,
    private val context: Context
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState(isLoading = true))

        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val darkModeFlow = context.dataStore.data
                .map { it[PrefKeys.DARK_MODE] ?: false }

            val unitFlow = context.dataStore.data
                .map {
                    if (it[PrefKeys.UNIT_SYSTEM] == UnitSystem.IMPERIAL.name)
                        UnitSystem.IMPERIAL else UnitSystem.METRIC
                }

            val locationFlow = settingsRepository.savedLocation
                .map { it?.name ?: "" }

            combine(darkModeFlow, unitFlow, locationFlow, settingsRepository.isAiEnabled) {
                dark, unit, loc, aiEnabled ->
                SettingsUiState(
                    isDarkMode = dark,
                    unitSystem = unit,
                    location = loc,
                    isAiEnabled = aiEnabled,
                    isLoading = false
                )
            }.collect { computed ->
                // Preserve transient state (AI error, location search status), which isn't part
                // of the persisted settings this combine tracks -- a plain replace here would
                // wipe it out on every unrelated DataStore write.
                _uiState.update { current ->
                    computed.copy(
                        aiError = current.aiError,
                        isResolvingLocation = current.isResolvingLocation,
                        locationStatus = current.locationStatus
                    )
                }
            }
        }
    }

    /**
     * A plain opt-in toggle now that AI is a cloud call with nothing to download -- just
     * persists the flag, after checking a provider key is actually configured for this build so
     * turning it on doesn't silently do nothing.
     */
    fun onAiEnabledToggled(enabled: Boolean) {
        if (enabled && !aiClient.isConfigured()) {
            _uiState.update { it.copy(aiError = "AI isn't configured for this build (missing API key).") }
            return
        }
        viewModelScope.launch {
            runCatching { settingsRepository.setAiEnabled(enabled) }
                .onFailure { setError("Couldn't save AI setting") }
        }
    }

    fun clearAiError() {
        _uiState.update { it.copy(aiError = null) }
    }

    fun onDarkModeToggled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                context.dataStore.edit { it[PrefKeys.DARK_MODE] = enabled }
            }.onFailure { setError("Couldn't save theme") }
        }
    }

    fun onUnitSystemSelected(unit: UnitSystem) {
        viewModelScope.launch {
            runCatching {
                context.dataStore.edit { it[PrefKeys.UNIT_SYSTEM] = unit.name }
            }.onFailure { setError("Couldn't save units") }
        }
    }

    /**
     * Resolves the typed city to coordinates via geocoding and persists it, so the outfit
     * generator and calendar fetch weather for the user's actual location instead of the
     * hardcoded default.
     */
    fun onLocationChanged(location: String) {
        val query = location.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingLocation = true, locationStatus = null) }
            runCatching { weatherRepository.geocodeCity(query) }
                .onSuccess { resolved ->
                    if (resolved == null) {
                        _uiState.update {
                            it.copy(
                                isResolvingLocation = false,
                                locationStatus = "Couldn't find \"$query\" — check the spelling."
                            )
                        }
                    } else {
                        runCatching { settingsRepository.setLocation(resolved) }
                            .onSuccess {
                                _uiState.update {
                                    it.copy(
                                        isResolvingLocation = false,
                                        locationStatus = "Weather location set to ${resolved.name}"
                                    )
                                }
                            }
                            .onFailure {
                                _uiState.update { it.copy(isResolvingLocation = false) }
                                setError("Couldn't save location")
                            }
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isResolvingLocation = false,
                            locationStatus = "Couldn't reach the location service — check your connection."
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

        companion object {
            fun provideFactory(context: Context): ViewModelProvider.Factory =
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        // Get the repository from the AppContainer
                        val container = (context.applicationContext as WardrobeApplication).container
                        return SettingsViewModel(
                            settingsRepository = container.settingsRepository,
                            weatherRepository = container.weatherRepository,
                            aiClient = container.aiClient,
                            context = context.applicationContext
                        ) as T
                }
        }
    }
}