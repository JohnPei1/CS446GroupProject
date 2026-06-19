package com.example.wardrobeapp

import android.content.Context
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
 * Note: These are currently stubs and will need real implementations once DAOs and APIs are ready.
 */
class AppDataContainer(private val context: Context) : AppContainer {

    override val wardrobeRepository: WardrobeRepository by lazy {
        // This will eventually take a DAO
        object : WardrobeRepository {
            override fun getAllItems() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.wardrobeapp.domain.model.ClothingItem>())
            override suspend fun insertItem(item: com.example.wardrobeapp.domain.model.ClothingItem) {}
            override suspend fun deleteItem(item: com.example.wardrobeapp.domain.model.ClothingItem) {}
        }
    }

    override val outfitRepository: OutfitRepository by lazy {
        object : OutfitRepository {
            override fun getAllOutfits() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.wardrobeapp.domain.model.Outfit>())
            override suspend fun saveOutfit(outfit: com.example.wardrobeapp.domain.model.Outfit) {}
            override suspend fun scheduleOutfit(outfitId: Long, date: Long) {}
        }
    }

    override val weatherRepository: WeatherRepository by lazy {
        object : WeatherRepository {
            override suspend fun getCurrentWeather(lat: Double, lon: Double): com.example.wardrobeapp.domain.model.WeatherInfo {
                return com.example.wardrobeapp.domain.model.WeatherInfo(0.0, "Sunny")
            }
        }
    }

    override val settingsRepository: SettingsRepository by lazy {
        object : SettingsRepository {
            override val isDarkMode = kotlinx.coroutines.flow.flowOf(false)
            override suspend fun setDarkMode(enabled: Boolean) {}
        }
    }
}
