package com.example.wardrobeapp.ui.outfit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wardrobeapp.WardrobeApplication
import com.example.wardrobeapp.data.repository.OutfitRepository
import com.example.wardrobeapp.data.repository.WardrobeRepository
import com.example.wardrobeapp.data.repository.WeatherRepository
import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.OutfitConstraints
import com.example.wardrobeapp.domain.strategy.OutfitStrategy
import com.example.wardrobeapp.domain.strategy.SimpleOutfitStrategy
import com.example.wardrobeapp.domain.strategy.WeatherAwareOutfitStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OutfitViewModel(
    private val outfitRepository: OutfitRepository,
    private val weatherRepository: WeatherRepository,
    private val wardrobeRepository: WardrobeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OutfitUiState())
    val uiState: StateFlow<OutfitUiState> = _uiState.asStateFlow()

    private val simpleStrategy: OutfitStrategy = SimpleOutfitStrategy()
    private val weatherAwareStrategy: OutfitStrategy = WeatherAwareOutfitStrategy()

    /** Generates a new outfit, factors in the weather if given. */
    fun generate(weatherAware: Boolean = true) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val fetched = wardrobeRepository.getAllItems().first()
            val items = if (fetched.isEmpty()) sampleWardrobe else fetched
            val weather = if (weatherAware) {
                runCatching { weatherRepository.getCurrentWeather(DEFAULT_LAT, DEFAULT_LON) }.getOrNull()
            } else null
            val strategy = if (weatherAware) weatherAwareStrategy else simpleStrategy
            val outfit = strategy.generateOutfit(items, OutfitConstraints(weather = weather))
            _uiState.value = _uiState.value.copy(generatedOutfit = outfit, isLoading = false)
        } }

    /** Re-try. */
    fun retry(weatherAware: Boolean = true) = generate(weatherAware)

    /** Pushes current outfit. */
    fun save() {
        val outfit = _uiState.value.generatedOutfit ?: return
        viewModelScope.launch { outfitRepository.saveOutfit(outfit) }
    }

    companion object {
        // Placeholder coordinates
        private const val DEFAULT_LAT = 43.46
        private const val DEFAULT_LON = -80.52

        // Placeholder items
        private val sampleWardrobe = listOf(
            ClothingItem(1, "White T-Shirt", "Tops", ""),
            ClothingItem(2, "Black Long Sleeve", "Tops", ""),
            ClothingItem(3, "Black Jeans", "Bottoms", ""),
            ClothingItem(4, "Grey Sweatpants", "Bottoms", ""),
            ClothingItem(5, "Running Shoes", "Footwear", ""),
            ClothingItem(6, "Rain Boots", "Footwear", ""),
            ClothingItem(7, "Winter Jacket", "Outerwear", ""),
            ClothingItem(8, "Light Jacket", "Outerwear", "")
        )

        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (context.applicationContext as WardrobeApplication).container
                    return OutfitViewModel(
                        outfitRepository = container.outfitRepository,
                        weatherRepository = container.weatherRepository,
                        wardrobeRepository = container.wardrobeRepository
                    ) as T } } } }