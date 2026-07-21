package com.example.wardrobeapp.ui.settings

enum class UnitSystem { METRIC, IMPERIAL }

data class SettingsUiState(
    val isDarkMode: Boolean = false,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val location: String = "",
    val isAiEnabled: Boolean = false,
    val isAiModelAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

