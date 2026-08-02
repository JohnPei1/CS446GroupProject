package com.example.wardrobeapp.ui.settings

enum class UnitSystem { METRIC, IMPERIAL }

data class SettingsUiState(
    val isDarkMode: Boolean = false,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    /** Display name of the saved weather location (e.g. "Waterloo, Ontario, Canada"). */
    val location: String = "",
    /** True while a typed city is being resolved to coordinates. */
    val isResolvingLocation: Boolean = false,
    /** Success/failure feedback for the last location search. */
    val locationStatus: String? = null,
    val isAiEnabled: Boolean = false,
    val isAiModelAvailable: Boolean = false,
    val aiDownloadProgress: Float? = null,
    val aiError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

