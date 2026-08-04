package com.example.wardrobeapp.ui.outfit

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wardrobeapp.WardrobeApplication
import com.example.wardrobeapp.data.remote.ai.AiClient
import com.example.wardrobeapp.data.repository.OutfitRepository
import com.example.wardrobeapp.data.repository.RecentPicksRepository
import com.example.wardrobeapp.data.repository.SettingsRepository
import com.example.wardrobeapp.data.repository.WardrobeRepository
import com.example.wardrobeapp.data.repository.WeatherRepository
import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.OutfitConstraints
import com.example.wardrobeapp.domain.model.WeatherInfo
import com.example.wardrobeapp.domain.model.floorToUtcMidnight
import com.example.wardrobeapp.domain.model.normalizeToUtcDay
import com.example.wardrobeapp.domain.strategy.AiOutfitStrategy
import com.example.wardrobeapp.domain.strategy.Category
import com.example.wardrobeapp.domain.strategy.IncompleteOutfitException
import com.example.wardrobeapp.domain.strategy.OutfitScorer
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class OutfitViewModel(
    private val outfitRepository: OutfitRepository,
    private val weatherRepository: WeatherRepository,
    private val wardrobeRepository: WardrobeRepository,
    private val settingsRepository: SettingsRepository,
    private val recentPicksRepository: RecentPicksRepository,
    private val aiClient: AiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(OutfitUiState())
    val uiState: StateFlow<OutfitUiState> = _uiState.asStateFlow()

    private val simpleStrategy: OutfitStrategy = SimpleOutfitStrategy()
    private val weatherAwareStrategy: OutfitStrategy = WeatherAwareOutfitStrategy()
    private val aiStrategy: OutfitStrategy = AiOutfitStrategy(aiClient)

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

            // Don't recommend a piece that's already committed to another day soon, or one
            // that's shown up repeatedly in recent generations -- see excludeUnavailableItems.
            val availableItems = excludeUnavailableItems(items, date, constraints)

            val fallbackStrategy = if (weatherAware) weatherAwareStrategy else simpleStrategy

            try {
                val outfit = tryAiOutfit(availableItems, constraints) ?: fallbackStrategy.generateOutfit(availableItems, constraints)
                recentItemIds = outfit.items.map { it.id }.toSet()
                // Recorded regardless of whether this outfit ever gets saved/scheduled -- the
                // point is to avoid repeating the same couple of items across generations, not
                // just across accepted ones.
                outfit.items.groupBy { it.category }.forEach { (category, picked) ->
                    recentPicksRepository.recordPicks(category, picked.map { it.id })
                }
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
     * Combines two reasons an item shouldn't be recommended right now: it's scheduled between
     * [date] (or today) and [SCHEDULE_LOOKAHEAD_DAYS] days after it, or it's in a category's
     * recent-picks history (up to the last 5 distinct ids shown per category, recorded after
     * every generation regardless of whether it's saved -- see [RecentPicksRepository]).
     *
     * The recent-picks half is skipped entirely once the user has a specific request -- a typed
     * prompt, or a chosen occasion. A generic "surprise me" generation has plenty of equally
     * valid candidates to rotate through, but a specific ask (e.g. "St Patrick's Day" against two
     * green tops, or the Formal chip against one blazer) can have only one or two genuinely
     * matching items; excluding the one just shown can leave nothing left that actually fits,
     * and the AI would rather decline the request than substitute something off-request. The
     * scheduling half still applies regardless -- that's a real physical conflict (already
     * committed to another day), not a staleness heuristic, so a specific request shouldn't
     * override it.
     */
    private suspend fun excludeUnavailableItems(
        items: List<ClothingItem>,
        date: Long?,
        constraints: OutfitConstraints
    ): List<ClothingItem> {
        // date is always an already-resolved day-key when non-null (from a date picker via the
        // calendar/generator) -- floor it, don't re-run local-time-zone interpretation, or it
        // shifts a day backward. Only System.currentTimeMillis() (a genuine wall-clock instant)
        // uses normalizeToUtcDay. See domain.model.DateUtils.
        val start = date?.let(::floorToUtcMidnight) ?: normalizeToUtcDay(System.currentTimeMillis())
        val end = start + (SCHEDULE_LOOKAHEAD_DAYS - 1) * DAY_MILLIS
        val scheduledIds = outfitRepository.observeScheduledOutfits().first()
            .filterKeys { it in start..end }
            .values
            .flatMap { it.items }
            .map { it.id }
            .toSet()
        val hasSpecificRequest = !constraints.userPrompt.isNullOrBlank() || constraints.occasion != null
        val recentPickIds = if (hasSpecificRequest) {
            emptySet()
        } else {
            Category.ALL.flatMap { category -> recentPicksRepository.getRecentIds(category) }.toSet()
        }
        return OutfitScorer.excludeRecentlyUsedItems(items, scheduledIds + recentPickIds)
    }

    /**
     * Current weather for today (or no date), or the target day's forecast when planning ahead.
     * Uses the location saved in Settings, falling back to the app default. Returns null when
     * the date is past the 7-day forecast window -- generation then runs without weather.
     */
    private suspend fun fetchWeatherFor(date: Long?): WeatherInfo? {
        val location = settingsRepository.savedLocation.first()
        val lat = location?.latitude ?: DEFAULT_LAT
        val lon = location?.longitude ?: DEFAULT_LON
        // date is already a resolved day-key -- floor, don't re-interpret locally, or a planned
        // day (e.g. August 8) silently looks up the previous day's forecast instead (a real,
        // reported bug: warm-weather clothes recommended for a planned hot day).
        val target = date?.let(::floorToUtcMidnight)
        val today = normalizeToUtcDay(System.currentTimeMillis())
        return if (target == null || target == today) {
            weatherRepository.getCurrentWeather(lat, lon)
        } else {
            weatherRepository.getForecastOneWeek(lat, lon)[target]
        }
    }

    /**
     * Attempts a cloud AI outfit when the user has opted in and a provider key is configured. AI
     * failures/timeouts are swallowed here -- generation always falls back to the deterministic
     * strategy rather than blocking on AI availability.
     */
    private suspend fun tryAiOutfit(items: List<ClothingItem>, constraints: OutfitConstraints): Outfit? {
        val aiEnabled = settingsRepository.isAiEnabled.first()
        if (!aiEnabled || !aiClient.isConfigured()) return null
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

    /**
     * Builds a shareable image of the current outfit and hands its content:// Uri to [onReady]
     * (the caller launches the actual share/save intent -- this stays Android-Intent-free).
     */
    fun exportOutfitImage(context: Context, onReady: (Uri) -> Unit) {
        val outfit = _uiState.value.generatedOutfit ?: return
        viewModelScope.launch {
            runCatching { OutfitImageExporter.export(context, outfit) }
                .onSuccess { uri ->
                    if (uri != null) onReady(uri)
                    else _uiState.value = _uiState.value.copy(userMessage = "Couldn't create the image.")
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(userMessage = "Couldn't create the image.")
                }
        }
    }

    /**
     * Saves the currently shown outfit to My Outfits without scheduling it. A freshly-generated
     * outfit only has a generic strategy name at this point ("AI Pick", "Everyday Outfit") --
     * scheduling gives it a real name automatically (the date), but saving on its own has no
     * date to fall back to, so this asks the user for a name instead of saving with the generic
     * one. See [confirmSaveName].
     */
    fun save() {
        if (_uiState.value.generatedOutfit == null || _uiState.value.saved) return
        _uiState.value = _uiState.value.copy(promptForSaveName = true)
    }

    /** Saves the current outfit with a user-provided [name] after [save] prompted for one. */
    fun confirmSaveName(name: String) {
        val outfit = _uiState.value.generatedOutfit ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _uiState.value.saved) return
        viewModelScope.launch {
            runCatching { ensureSaved(outfit.copy(name = trimmed)) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(promptForSaveName = false, userMessage = "Saved to My Outfits.")
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(promptForSaveName = false, userMessage = "Couldn't save the outfit.")
                }
        }
    }

    fun dismissSaveNamePrompt() {
        _uiState.value = _uiState.value.copy(promptForSaveName = false)
    }

    /**
     * Plans the current outfit for [date]. If that day already has an outfit, the request is
     * parked in [OutfitUiState.pendingSchedule] for the user to confirm the replacement.
     * [date] must already be a resolved day-key (from [normalizeToUtcDay] if the caller has a
     * raw wall-clock instant, or straight from a date picker) -- this only floors it.
     */
    fun requestSchedule(date: Long) {
        if (_uiState.value.generatedOutfit == null) return
        viewModelScope.launch {
            val day = floorToUtcMidnight(date)
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
     * Scheduling implies saving (a plan references a saved outfit). A freshly-generated outfit
     * (still carrying its generic strategy name, never renamed by the user via [confirmSaveName])
     * is named after the date it's scheduled for here, rather than being saved as "AI Pick"
     * forever -- an outfit already saved with a real name (whether typed by the user or built
     * from scratch in the manual builder) keeps it. Wearing it today also records the items as
     * worn, which feeds the anti-repetition scoring.
     */
    private suspend fun performSchedule(day: Long) {
        val outfit = _uiState.value.generatedOutfit ?: return
        val named = if (_uiState.value.saved) outfit else outfit.copy(name = dateOutfitName(day))
        runCatching {
            val id = ensureSaved(named)
            outfitRepository.scheduleOutfit(id, day)
            val isToday = day == normalizeToUtcDay(System.currentTimeMillis())
            if (isToday) wardrobeRepository.markItemsWorn(named.items.map { it.id })
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

    /**
     * "August 8", even when [day] is today -- unlike [planDateLabel], this never returns the
     * word "today", since it's used to actually name the saved outfit.
     */
    private fun dateOutfitName(day: Long): String {
        val sdf = SimpleDateFormat("MMMM d", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(day))
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
        // Must exceed GeminiAiClient's own internal timeout so that inner timeout is the one that
        // actually governs and produces a clear "AI response timed out" -- otherwise this wrapper
        // cancels the whole generateOutfit() call first, with no reason attached.
        private const val AI_TIMEOUT_MS = 30_000L
        // "Up to 3 days" per the reported request: an item scheduled for today, tomorrow, or the
        // day after is off-limits to a new generation; beyond that it's fair game again.
        private const val SCHEDULE_LOOKAHEAD_DAYS = 3
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

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
                        recentPicksRepository = container.recentPicksRepository,
                        aiClient = container.aiClient
                    ) as T } } } }
