package com.example.wardrobeapp.data.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for app settings.
 */
interface SettingsRepository {
    val isDarkMode: Flow<Boolean>
    suspend fun setDarkMode(enabled: Boolean)
    val isAiEnabled: Flow<Boolean>
    suspend fun setAiEnabled(enabled: Boolean)
}
