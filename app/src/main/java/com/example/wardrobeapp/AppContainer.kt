package com.example.wardrobeapp

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.wardrobeapp.data.local.AppDatabase
import com.example.wardrobeapp.data.remote.ai.AiClient
import com.example.wardrobeapp.data.remote.ai.GeminiAiClient
import com.example.wardrobeapp.data.repository.*
import com.example.wardrobeapp.domain.model.SavedLocation
import kotlinx.coroutines.flow.map

/**
 * Dependency Injection container for the app.
 */
interface AppContainer {
    val wardrobeRepository: WardrobeRepository
    val outfitRepository: OutfitRepository
    val weatherRepository: WeatherRepository
    val settingsRepository: SettingsRepository
    val recentPicksRepository: RecentPicksRepository
    val aiClient: AiClient
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
            database.clothingItemDao(),
            database.scheduledOutfitDao()
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

            private val LOCATION_NAME = stringPreferencesKey("location")
            private val LOCATION_LAT = doublePreferencesKey("location_lat")
            private val LOCATION_LON = doublePreferencesKey("location_lon")
            override val savedLocation = context.dataStore.data.map { prefs ->
                val name = prefs[LOCATION_NAME]
                val lat = prefs[LOCATION_LAT]
                val lon = prefs[LOCATION_LON]
                if (name.isNullOrBlank() || lat == null || lon == null) null
                else SavedLocation(name, lat, lon)
            }
            override suspend fun setLocation(location: SavedLocation) {
                context.dataStore.edit {
                    it[LOCATION_NAME] = location.name
                    it[LOCATION_LAT] = location.latitude
                    it[LOCATION_LON] = location.longitude
                }
            }
        }
    }

    override val recentPicksRepository: RecentPicksRepository by lazy {
        OfflineRecentPicksRepository(context.applicationContext)
    }

    override val aiClient: AiClient by lazy {
        GeminiAiClient()
    }
}
