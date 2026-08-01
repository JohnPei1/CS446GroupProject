package com.example.wardrobeapp

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.wardrobeapp.data.local.AppDatabase
import com.example.wardrobeapp.data.local.ai.LlmModelManager
import com.example.wardrobeapp.data.repository.*
import kotlinx.coroutines.flow.map

/**
 * Dependency Injection container for the app.
 */
interface AppContainer {
    val wardrobeRepository: WardrobeRepository
    val outfitRepository: OutfitRepository
    val weatherRepository: WeatherRepository
    val settingsRepository: SettingsRepository
    val llmModelManager: LlmModelManager
}

/**
 * Implementation of [AppContainer] that provides repository instances.
 */

internal val Context.dataStore by preferencesDataStore(name = "settings")
class AppDataContainer(private val context: Context) : AppContainer {

    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    override val wardrobeRepository: WardrobeRepository by lazy {
        OfflineWardrobeRepository(database.clothingItemDao())
    }

    override val outfitRepository: OutfitRepository by lazy {
        OfflineOutfitRepository(
            database.outfitDao(),
            database.scheduledOutfitDao(),
            database.clothingItemDao()
        )
    }

    override val weatherRepository: WeatherRepository by lazy {
        WeatherRepository()
    }

    override val settingsRepository: SettingsRepository by lazy {
        object : SettingsRepository {
            private val DARK_MODE = booleanPreferencesKey("dark_mode")
            private val AI_ENABLED = booleanPreferencesKey("ai_enabled")
            override val isDarkMode = context.dataStore.data.map {
                it[DARK_MODE] ?: false
            }
            override suspend fun setDarkMode(enabled: Boolean) {
                context.dataStore.edit { it[DARK_MODE] = enabled }
            }
            override val isAiEnabled = context.dataStore.data.map {
                it[AI_ENABLED] ?: false
            }
            override suspend fun setAiEnabled(enabled: Boolean) {
                context.dataStore.edit { it[AI_ENABLED] = enabled }
            }
        }
    }

    override val llmModelManager: LlmModelManager by lazy {
        LlmModelManager(context.applicationContext)
    }
}
