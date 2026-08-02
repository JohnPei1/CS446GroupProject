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
import com.example.wardrobeapp.domain.model.WeatherInfo
import com.example.wardrobeapp.domain.strategy.AiOutfitStrategy
import com.example.wardrobeapp.domain.strategy.IncompleteOutfitException
import com.example.wardrobeapp.domain.strategy.OutfitStrategy
import com.example.wardrobeapp.domain.strategy.SimpleOutfitStrategy
import com.example.wardrobeapp.domain.strategy.WardrobeGapChecker
import com.example.wardrobeapp.domain.strategy.WardrobeGapException
import com.example.wardrobeapp.domain.strategy.WeatherAwareOutfitStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    // Session-only (not persisted): the previous generation's item ids, so repeatedly hitting
    // "Generate Another" actually rotates through different combinations instead of re-picking
    // the same top-scoring items every time when there's no other distinguishing signal.
    private var recentItemIds: Set<Long> = emptySet()

    /**
     * Generates a new outfit, factors in the weather, occasion, and free-text prompt if given.
     * When [date] is a future day (planning from the calendar), that day's forecast is used
     * instead of the current conditions; beyond the forecast window, weather is skipped.
     */
    fun generate(
        weatherAware: Boolean = true,
        occasion: String? = null,
        userPrompt: String? = null,
        date: Long? = null
    ) {
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
                runCatching { fetchWeatherFor(date) }.getOrNull()
            } else null
            val constraints = OutfitConstraints(
                weather = weather,
                occasion = occasion,
                userPrompt = userPrompt,
                recentItemIds = recentItemIds
            )

            // Refuse honestly instead of dressing the user in something unsuitable: a Swim
            // occasion with no swimwear, or -30°C with only summer clothes, is a wardrobe gap
            // to fix, not something to paper over with a "normal" outfit. This check is
            // deterministic so it protects users without the AI model too.
            val gaps = WardrobeGapChecker.findGaps(items, constraints)
            if (gaps.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    generatedOutfit = null,
                    isLoading = false,
                    error = null,
                    wardrobeGap = gaps.joinToString("\n\n"),
                    lastWeatherUsed = weather,
                    weatherUnavailable = weatherAware && weather == null
                )
                return@launch
            }

            val fallbackStrategy = if (weatherAware) weatherAwareStrategy else simpleStrategy

            try {
                val outfit = tryAiOutfit(items, constraints) ?: fallbackStrategy.generateOutfit(items, constraints)
                recentItemIds = outfit.items.map { it.id }.toSet()
                _uiState.value = _uiState.value.copy(
                    generatedOutfit = outfit,
                    isLoading = false,
                    error = null,
                    wardrobeGap = null,
                    lastWeatherUsed = weather,
                    weatherUnavailable = weatherAware && weather == null,
                    saved = false,
                    scheduledFor = null,
                    pendingSchedule = null
                )
            } catch (e: WardrobeGapException) {
                // The AI judged the request unsatisfiable (e.g. a typed "going swimming" that
                // the deterministic pre-check can't understand).
                _uiState.value = _uiState.value.copy(
                    generatedOutfit = null,
                    isLoading = false,
                    error = null,
                    wardrobeGap = e.gaps.joinToString("\n\n"),
                    lastWeatherUsed = weather
                )
            } catch (e: IncompleteOutfitException) {
                _uiState.value = _uiState.value.copy(
                    generatedOutfit = null,
                    isLoading = false,
                    wardrobeGap = null,
                    error = "Add at least one item to: ${e.missingCategories.joinToString(" and ")}. " +
                        "A complete outfit needs a top and a bottom."
                )
            }
        } }

    /**
     * Current weather for today (or no date), or the target day's forecast when planning ahead.
     * Uses the location saved in Settings, falling back to the app default. Returns null when
     * the date is past the 7-day forecast window -- generation then runs without weather.
     */
    private suspend fun fetchWeatherFor(date: Long?): WeatherInfo? {
        val location = settingsRepository.savedLocation.first()
        val lat = location?.latitude ?: DEFAULT_LAT
        val lon = location?.longitude ?: DEFAULT_LON
        val target = date?.let(::normalizeToUtcDay)
        val today = normalizeToUtcDay(System.currentTimeMillis())
        return if (target == null || target == today) {
            weatherRepository.getCurrentWeather(lat, lon)
        } else {
            weatherRepository.getForecastOneWeek(lat, lon)[target]
        }
    }

    /**
     * Attempts an on-device AI outfit when the user has opted in and imported a model. AI
     * failures/timeouts are swallowed here -- generation always falls back to the deterministic
     * strategy rather than blocking on AI availability.
     */
    private suspend fun tryAiOutfit(items: List<ClothingItem>, constraints: OutfitConstraints): Outfit? {
        val aiEnabled = settingsRepository.isAiEnabled.first()
        if (!aiEnabled || !llmModelManager.isModelAvailable()) return null
        val outfit = try {
            withTimeoutOrNull(AI_TIMEOUT_MS) { aiStrategy.generateOutfit(items, constraints) }
        } catch (e: WardrobeGapException) {
            // A deliberate "the wardrobe can't satisfy this" verdict, not an AI failure --
            // let it surface instead of silently falling back to a normal outfit.
            throw e
        } catch (e: Exception) {
            null
        }
        return outfit?.takeIf { it.items.isNotEmpty() }
    }

    /** Saves the currently shown outfit to My Outfits. */
    fun save() {
        val outfit = _uiState.value.generatedOutfit ?: return
        if (_uiState.value.saved) return
        viewModelScope.launch {
            runCatching { ensureSaved(outfit) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(userMessage = "Saved to My Outfits.")
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(userMessage = "Couldn't save the outfit.")
                }
        }
    }

    /**
     * Plans the current outfit for [date]. If that day already has an outfit, the request is
     * parked in [OutfitUiState.pendingSchedule] for the user to confirm the replacement.
     */
    fun requestSchedule(date: Long) {
        if (_uiState.value.generatedOutfit == null) return
        viewModelScope.launch {
            val day = normalizeToUtcDay(date)
            val existing = runCatching { outfitRepository.getScheduledOutfit(day) }.getOrNull()
            if (existing != null) {
                _uiState.value = _uiState.value.copy(
                    pendingSchedule = PendingSchedule(day, existing.name)
                )
            } else {
                performSchedule(day)
            }
        }
    }

    fun confirmPendingSchedule() {
        val pending = _uiState.value.pendingSchedule ?: return
        _uiState.value = _uiState.value.copy(pendingSchedule = null)
        viewModelScope.launch { performSchedule(pending.date) }
    }

    fun dismissPendingSchedule() {
        _uiState.value = _uiState.value.copy(pendingSchedule = null)
    }

    /**
     * Scheduling implies saving (a plan references a saved outfit). Wearing it today also
     * records the items as worn, which feeds the anti-repetition scoring.
     */
    private suspend fun performSchedule(day: Long) {
        val outfit = _uiState.value.generatedOutfit ?: return
        runCatching {
            val id = ensureSaved(outfit)
            outfitRepository.scheduleOutfit(id, day)
            val isToday = day == normalizeToUtcDay(System.currentTimeMillis())
            if (isToday) wardrobeRepository.markItemsWorn(outfit.items.map { it.id })
            isToday
        }.onSuccess { isToday ->
            _uiState.value = _uiState.value.copy(
                scheduledFor = day,
                userMessage = if (isToday) {
                    "Planned for today — items marked as worn."
                } else {
                    "Planned for ${planDateLabel(day)}."
                }
            )
        }.onFailure {
            _uiState.value = _uiState.value.copy(userMessage = "Couldn't plan the outfit.")
        }
    }

    /** Saves the outfit if it isn't persisted yet, updating state with the assigned id. */
    private suspend fun ensureSaved(outfit: Outfit): Long {
        if (outfit.id != 0L && _uiState.value.saved) return outfit.id
        val id = outfitRepository.saveOutfit(outfit)
        _uiState.value = _uiState.value.copy(
            generatedOutfit = outfit.copy(id = id),
            saved = true
        )
        return id
    }

    /** Clears the one-shot snackbar message after the UI has shown it. */
    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }

    companion object {
        // Fallback coordinates (Waterloo, ON) when no location is saved in Settings
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
