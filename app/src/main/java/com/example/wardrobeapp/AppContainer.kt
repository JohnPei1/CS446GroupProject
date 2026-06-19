package com.example.wardrobeapp

import android.content.Context
import com.example.wardrobeapp.data.local.AppDatabase
import com.example.wardrobeapp.data.repository.*

/**
 * Dependency Injection container for the app.
 */
interface AppContainer {
    val wardrobeRepository: WardrobeRepository
    val outfitRepository: OutfitRepository
    val weatherRepository: WeatherRepository
    val settingsRepository: SettingsRepository
}

/**
 * Implementation of [AppContainer] that provides repository instances.
 */
class AppDataContainer(private val context: Context) : AppContainer {

    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    override val wardrobeRepository: WardrobeRepository by lazy {
        // This will eventually take a DAO (Hermela's task)
        object : WardrobeRepository {
            override fun getAllItems() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.wardrobeapp.domain.model.ClothingItem>())
            override suspend fun insertItem(item: com.example.wardrobeapp.domain.model.ClothingItem) {}
            override suspend fun deleteItem(item: com.example.wardrobeapp.domain.model.ClothingItem) {}
        }
    }

    override val outfitRepository: OutfitRepository by lazy {
        OfflineOutfitRepository(database.outfitDao())
    }

    override val weatherRepository: WeatherRepository by lazy {
        OfflineWeatherRepository()
    }

    override val settingsRepository: SettingsRepository by lazy {
        object : SettingsRepository {
            override val isDarkMode = kotlinx.coroutines.flow.flowOf(false)
            override suspend fun setDarkMode(enabled: Boolean) {}
        }
    }
}
