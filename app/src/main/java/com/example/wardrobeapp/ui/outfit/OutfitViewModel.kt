package com.example.wardrobeapp.ui.outfit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wardrobeapp.WardrobeApplication
import com.example.wardrobeapp.data.local.ai.LlmModelManager
import com.example.wardrobeapp.data.repository.OutfitRepository
import com.example.wardrobeapp.data.repository.SettingsRepository
import com.example.wardrobeapp.data.repository.WardrobeRepository
import com.example.wardrobeapp.data.repository.WeatherRepository
import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.OutfitConstraints
import com.example.wardrobeapp.domain.strategy.AiOutfitStrategy
import com.example.wardrobeapp.domain.strategy.IncompleteOutfitException
import com.example.wardrobeapp.domain.strategy.OutfitStrategy
import com.example.wardrobeapp.domain.strategy.SimpleOutfitStrategy
import com.example.wardrobeapp.domain.strategy.WeatherAwareOutfitStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.TimeZone

class OutfitViewModel(
    private val outfitRepository: OutfitRepository,
    private val weatherRepository: WeatherRepository,
    private val wardrobeRepository: WardrobeRepository,
    private val settingsRepository: SettingsRepository,
    private val llmModelManager: LlmModelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OutfitUiState())
    val uiState: StateFlow<OutfitUiState> = _uiState.asStateFlow()

    private val simpleStrategy: OutfitStrategy = SimpleOutfitStrategy()
    private val weatherAwareStrategy: OutfitStrategy = WeatherAwareOutfitStrategy()
    private val aiStrategy: OutfitStrategy = AiOutfitStrategy(llmModelManager)

    fun loadPlannedOutfit(date: Long) {
        viewModelScope.launch {
            val normalizedDate = normalizeDate(date)
            val planned = outfitRepository.getScheduledOutfit(normalizedDate)
            if (planned != null) {
                _uiState.value = _uiState.value.copy(generatedOutfit = planned)
            }
        }
    }

    private fun normalizeDate(timeInMillis: Long): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /** Generates a new outfit, factors in the weather, occasion, and free-text prompt if given. */
    fun generate(weatherAware: Boolean = true, occasion: String? = null, userPrompt: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val items = wardrobeRepository.getAllItems().first()

            if (items.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    generatedOutfit = null,
                    isLoading = false,
                    error = "Your wardrobe is empty! Add some items first."
                )
                return@launch
            }

            val weather = if (weatherAware) {
                runCatching { weatherRepository.getCurrentWeather(DEFAULT_LAT, DEFAULT_LON) }.getOrNull()
            } else null
            val constraints = OutfitConstraints(weather = weather, occasion = occasion, userPrompt = userPrompt)
            val fallbackStrategy = if (weatherAware) weatherAwareStrategy else simpleStrategy

            try {
                val outfit = tryAiOutfit(items, constraints) ?: fallbackStrategy.generateOutfit(items, constraints)
                _uiState.value = _uiState.value.copy(generatedOutfit = outfit, isLoading = false, error = null)
            } catch (e: IncompleteOutfitException) {
                _uiState.value = _uiState.value.copy(
                    generatedOutfit = null,
                    isLoading = false,
                    error = "Add at least one item to: ${e.missingCategories.joinToString(" and ")}. " +
                        "A complete outfit needs a top and a bottom."
                )
            }
        } }

    /**
     * Attempts an on-device AI outfit when the user has opted in and imported a model. AI
     * failures/timeouts are swallowed here -- generation always falls back to the deterministic
     * strategy rather than blocking on AI availability.
     */
    private suspend fun tryAiOutfit(items: List<ClothingItem>, constraints: OutfitConstraints): Outfit? {
        val aiEnabled = settingsRepository.isAiEnabled.first()
        if (!aiEnabled || !llmModelManager.isModelAvailable()) return null
        val outfit = runCatching {
            withTimeoutOrNull(AI_TIMEOUT_MS) { aiStrategy.generateOutfit(items, constraints) }
        }.getOrNull()
        return outfit?.takeIf { it.items.isNotEmpty() }
    }

    /** Re-try. */
    fun retry(weatherAware: Boolean = true, occasion: String? = null, userPrompt: String? = null) =
        generate(weatherAware, occasion, userPrompt)

    /** Pushes current outfit. */
    fun save() {
        // No-op for now as per user request
    }

    /** Records the currently generated outfit's items as worn, for anti-repetition scoring. */
    fun markWorn() {
        val outfit = _uiState.value.generatedOutfit ?: return
        viewModelScope.launch {
            wardrobeRepository.markItemsWorn(outfit.items.map { it.id })
        }
    }

    companion object {
        // Placeholder coordinates
        private const val DEFAULT_LAT = 43.46
        private const val DEFAULT_LON = -80.52
        private const val AI_TIMEOUT_MS = 20_000L

        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (context.applicationContext as WardrobeApplication).container
                    return OutfitViewModel(
                        outfitRepository = container.outfitRepository,
                        weatherRepository = container.weatherRepository,
                        wardrobeRepository = container.wardrobeRepository,
                        settingsRepository = container.settingsRepository,
                        llmModelManager = container.llmModelManager
                    ) as T } } } }
