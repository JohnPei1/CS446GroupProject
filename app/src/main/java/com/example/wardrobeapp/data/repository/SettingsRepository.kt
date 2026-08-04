package com.example.wardrobeapp.data.repository

import com.example.wardrobeapp.domain.model.SavedLocation
import kotlinx.coroutines.flow.Flow

/**
 * Repository for app settings.
 */
interface SettingsRepository {
    val isDarkMode: Flow<Boolean>
    suspend fun setDarkMode(enabled: Boolean)
    val isAiEnabled: Flow<Boolean>
    suspend fun setAiEnabled(enabled: Boolean)

    /** Weather location resolved from the user's city search; null until one is saved. */
    val savedLocation: Flow<SavedLocation?>
    suspend fun setLocation(location: SavedLocation)
}
