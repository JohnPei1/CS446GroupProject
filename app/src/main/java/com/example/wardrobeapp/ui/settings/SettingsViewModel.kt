package com.example.wardrobeapp.ui.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wardrobeapp.data.local.ai.LlmModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.example.wardrobeapp.dataStore
import com.example.wardrobeapp.data.repository.SettingsRepository
import com.example.wardrobeapp.WardrobeApplication



private object PrefKeys {
    val DARK_MODE    = booleanPreferencesKey("dark_mode")
    val UNIT_SYSTEM  = stringPreferencesKey("unit_system")
    val LOCATION     = stringPreferencesKey("location")
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val llmModelManager: LlmModelManager,
    private val context: Context
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState(isLoading = true))
        private val _isAiModelAvailable = MutableStateFlow(llmModelManager.isModelAvailable())

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

            val locationFlow = context.dataStore.data
                .map { it[PrefKeys.LOCATION] ?: "" }

            combine(darkModeFlow, unitFlow, locationFlow, settingsRepository.isAiEnabled, _isAiModelAvailable) {
                dark, unit, loc, aiEnabled, aiModelAvailable ->
                SettingsUiState(
                    isDarkMode = dark,
                    unitSystem = unit,
                    location = loc,
                    isAiEnabled = aiEnabled,
                    isAiModelAvailable = aiModelAvailable,
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    fun onAiEnabledToggled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching { settingsRepository.setAiEnabled(enabled) }
                .onFailure { setError("Couldn't save AI setting") }
        }
    }

    /** Copies a user-picked .task model file into app storage. No network access. */
    fun importAiModel(uri: Uri) {
        viewModelScope.launch {
            llmModelManager.importModel(uri)
                .onSuccess { _isAiModelAvailable.value = true }
                .onFailure {
                    _isAiModelAvailable.value = false
                    setError(it.message ?: "Couldn't import the AI model file")
                }
        }
    }

    fun deleteAiModel() {
        llmModelManager.deleteModel()
        _isAiModelAvailable.value = false
        onAiEnabledToggled(false)
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

    fun onLocationChanged(location: String) {
        viewModelScope.launch {
            runCatching {
                context.dataStore.edit { it[PrefKeys.LOCATION] = location }
            }.onFailure { setError("Couldn't save location") }
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
                            llmModelManager = container.llmModelManager,
                            context = context.applicationContext
                        ) as T
                }
        }
    }
}