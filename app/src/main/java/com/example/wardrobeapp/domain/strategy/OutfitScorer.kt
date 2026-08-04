package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.OutfitConstraints
import com.example.wardrobeapp.domain.model.WarmthLevels
import com.example.wardrobeapp.domain.model.WeatherInfo
import java.util.Calendar
import kotlin.math.abs

/**
 * Pure, stateless scoring for outfit candidates. Used by [SimpleOutfitStrategy] and
 * [WeatherAwareOutfitStrategy] as their picking logic, and by [AiOutfitStrategy] to shortlist
 * candidates before handing them to the on-device model.
 */
object OutfitScorer {

    private const val RECENT_WORN_WINDOW_MS = 3L * 24 * 60 * 60 * 1000 // 3 days
    private const val VARIETY_SCORE_MARGIN = 6 // ties within this margin are randomized; a clear winner is not
    private const val PROMPT_MATCH_BONUS = 20
    private const val RECENT_GENERATION_PENALTY = 10 // discourages repeating last generation's picks
    private const val EXTREME_WARMTH_MISMATCH = 3 // levels off the ideal beyond which an item is just wrong
    private const val EXTREME_WARMTH_PENALTY = 20
    // A typed request (see score()) keeps the original, softer weather weight above -- once the
    // user has said what they want, weather should mostly defer to it (AiOutfitStrategy's prompt
    // tells the AI the same thing). A bare "Generate" tap with nothing specific typed uses these
    // stronger values instead: reported bug was shorts recommended at 15°C and NOT recommended
    // at 32°C, meaning the old weight (a 2-level mismatch netted only a small +2) was too weak to
    // reliably beat recency/variety/season noise in either direction.
    private const val GENERIC_WARMTH_PENALTY_PER_LEVEL = 8 // was effectively 4
    private const val GENERIC_EXTREME_WARMTH_MISMATCH = 2 // was effectively 3
    private const val SEASON_MATCH_BONUS = 6
    private const val SEASON_MISMATCH_PENALTY = 8

    private val PROMPT_STOPWORDS = setOf(
        "and", "the", "for", "with", "your", "need", "want", "just", "some",
        "this", "that", "outfit", "look", "please", "wear", "wearing"
    )

    // Common free-text phrasings (including the app's own prompt placeholder examples) that
    // imply one of TagOptions.OCCASIONS without using that exact word.
    private val OCCASION_SYNONYMS: Map<String, String> = mapOf(
        "funeral" to "Formal",
        "wedding" to "Formal",
        "gala" to "Formal",
        "graduation" to "Formal",
        "interview" to "Business Casual",
        "gym" to "Workout",
        "workout" to "Workout",
        "beach" to "Swim",
        "pool" to "Swim",
        "swimming" to "Swim",
        "hike" to "Outdoor",
        "hiking" to "Outdoor",
        "pajama" to "Sleepwear",
        "pajamas" to "Sleepwear",
        "bed" to "Sleepwear"
    )

    private val COMPATIBLE_FAMILIES: Map<String, Set<String>> = mapOf(
        "Warm" to setOf("Warm", "Neutral", "Multicolor"),
        "Cool" to setOf("Cool", "Neutral", "Multicolor"),
        "Bright" to setOf("Bright", "Neutral", "Multicolor"),
        "Pastel" to setOf("Pastel", "Neutral", "Multicolor"),
        "Neutral" to setOf("Warm", "Cool", "Bright", "Pastel", "Neutral", "Multicolor"),
        "Multicolor" to setOf("Warm", "Cool", "Bright", "Pastel", "Neutral", "Multicolor")
    )

    /**
     * Picks the highest-scoring candidate. When several candidates are within
     * [VARIETY_SCORE_MARGIN] of each other (a genuine tie), one is picked at random so "Try
     * Again" still has an effect -- but a candidate that clearly wins on score (e.g. a strong
     * user-prompt keyword match) is never overridden by that randomness.
     */
    fun pickBest(
        items: List<ClothingItem>,
        category: String,
        constraints: OutfitConstraints,
        alreadyPicked: List<ClothingItem>
    ): ClothingItem? {
        val candidates = items.filter { it.category.equals(category, ignoreCase = true) }
        if (candidates.isEmpty()) return null
        val scored = candidates.map { it to score(it, constraints, alreadyPicked) }
        val topScore = scored.maxOf { it.second }
        return scored.filter { it.second >= topScore - VARIETY_SCORE_MARGIN }
            .map { it.first }
            .random()
    }

    fun topCandidates(
        items: List<ClothingItem>,
        category: String,
        constraints: OutfitConstraints,
        alreadyPicked: List<ClothingItem>,
        limit: Int
    ): List<ClothingItem> {
        val candidates = items.filter { it.category.equals(category, ignoreCase = true) }
        if (candidates.isEmpty()) return emptyList()
        return candidates
            .sortedByDescending { score(it, constraints, alreadyPicked) }
            .take(limit)
    }

    fun score(item: ClothingItem, constraints: OutfitConstraints, alreadyPicked: List<ClothingItem>): Int {
        var score = 0

        constraints.occasion?.let { occasion ->
            score += if (item.tags.any { it.equals(occasion, ignoreCase = true) }) 30 else -10
        }

        constraints.weather?.let { weather ->
            val warmthMismatch = abs(item.warmthLevel - WarmthLevels.idealFor(weather.temperature))
            // Weather is the primary signal for a bare "Generate" with no occasion and nothing
            // typed -- it should reliably reach for shorts on a hot day and avoid them on a cool
            // one. The moment there's any guidance (an occasion chip, or typed text), this reverts
            // to the original, softer weight, since weather is meant to defer to what was actually
            // asked for -- an occasion alone (e.g. just tapping "Formal") must still be able to
            // outrank an ordinary warmth mismatch, the same way a typed request does.
            val genericRequest = constraints.occasion == null && constraints.userPrompt.isNullOrBlank()
            val perLevelPenalty = if (genericRequest) GENERIC_WARMTH_PENALTY_PER_LEVEL else 4
            val extremeThreshold = if (genericRequest) GENERIC_EXTREME_WARMTH_MISMATCH else EXTREME_WARMTH_MISMATCH
            score += 10 - warmthMismatch * perLevelPenalty
            // An item this far off (a heavy parka on a hot day) is never a reasonable "variety"
            // pick -- push it well outside the tie margin so other bonuses can't rescue it.
            if (warmthMismatch >= extremeThreshold) score -= EXTREME_WARMTH_PENALTY
            if (weather.condition.contains("rain", ignoreCase = true) && item.isWaterResistant) {
                score += 15
            }
        }

        score += seasonScore(item.season)

        alreadyPicked.forEach { picked ->
            score += if (colorsCompatible(item.colorFamily, picked.colorFamily)) 8 else -6
        }

        score -= item.timesWorn * 2
        item.lastWornDate?.let { lastWorn ->
            if (System.currentTimeMillis() - lastWorn < RECENT_WORN_WINDOW_MS) score -= 12
        }

        if (item.isFavorite) score += 5

        score += promptMatchBonus(item, constraints.userPrompt)

        if (item.id in constraints.recentItemIds) score -= RECENT_GENERATION_PENALTY

        return score
    }

    /**
     * Drops items that shouldn't be handed back right now -- either because they're already
     * committed to another day within the next few days (see
     * [com.example.wardrobeapp.ui.outfit.OutfitViewModel]'s scheduling lookahead), or because
     * they're in a category's recent-picks history (see [RecentPicksRepository]) and a fresher
     * option exists. A small scoring penalty alone was too weak for either case -- a strong
     * occasion/color match could easily outscore it -- so this is a real exclusion from the
     * candidate pool, not just a nudge. Applied per-category: if excluding would leave a category
     * with nothing at all (e.g. a 4-item Accessories wardrobe where all 4 are "recent"), that
     * category's exclusion is skipped rather than forcing an impossible or oddly-empty outfit --
     * the goal is variety when the wardrobe has slack, never blocking a suggestion when it doesn't.
     */
    fun excludeRecentlyUsedItems(items: List<ClothingItem>, excludedIds: Set<Long>): List<ClothingItem> {
        if (excludedIds.isEmpty()) return items
        return Category.ALL.flatMap { category ->
            val inCategory = items.filter { it.category.equals(category, ignoreCase = true) }
            val filtered = inCategory.filterNot { it.id in excludedIds }
            filtered.ifEmpty { inCategory }
        }
    }

    /**
     * True when the wardrobe has an outerwear piece actually tagged for [occasion] -- used to
     * include outerwear for formality (e.g. a blazer for a Formal occasion) even when
     * temperature alone wouldn't call for a layer. Shared by [SimpleOutfitStrategy] and
     * [WeatherAwareOutfitStrategy]; [AiOutfitStrategy] handles this itself via its prompt.
     */
    fun occasionMatchesOuterwear(items: List<ClothingItem>, occasion: String?): Boolean {
        if (occasion == null) return false
        return items.any {
            it.category.equals(Category.OUTERWEAR, ignoreCase = true) &&
                it.tags.any { tag -> tag.equals(occasion, ignoreCase = true) }
        }
    }

    /**
     * Returns an accessory only when there's a genuine reason to include one -- a user-prompt
     * keyword match or an occasion-tag match -- rather than tacking a random accessory onto every
     * outfit. This is how a typed request like "gold chain" actually surfaces an Accessories item;
     * none of the strategies previously considered that category at all.
     */
    fun matchedAccessory(items: List<ClothingItem>, constraints: OutfitConstraints): ClothingItem? {
        if (constraints.userPrompt.isNullOrBlank() && constraints.occasion == null) return null
        val candidates = items.filter { it.category.equals(Category.ACCESSORIES, ignoreCase = true) }
        if (candidates.isEmpty()) return null

        val best = candidates.maxByOrNull { score(it, constraints, emptyList()) } ?: return null
        val occasionMatch = constraints.occasion?.let { occasion ->
            best.tags.any { it.equals(occasion, ignoreCase = true) }
        } ?: false
        val promptMatch = promptMatchBonus(best, constraints.userPrompt) > 0
        return if (occasionMatch || promptMatch) best else null
    }

    /**
     * Lightweight, non-AI keyword matching between the user's free-text prompt and an item's
     * color/tags/name/brand, so the prompt has a real effect on the deterministic strategies too
     * (not just when the AI is enabled). Also checks [OCCASION_SYNONYMS] -- the app's own prompt
     * placeholder suggests phrasings like "job interview" that never literally match a tag
     * ("Business Casual"), which would otherwise leave an occasion-appropriate item with zero
     * signal and a real chance of not surfacing at all, the same class of bug as items getting
     * silently excluded by an over-eager filter.
     */
    private fun promptMatchBonus(item: ClothingItem, userPrompt: String?): Int {
        val words = userPrompt
            ?.lowercase()
            ?.split(Regex("[^a-z0-9]+"))
            ?.filter { it.length >= 3 && it !in PROMPT_STOPWORDS }
            .orEmpty()
        if (words.isEmpty()) return 0

        val haystack = buildString {
            append(item.color.lowercase()); append(' ')
            append(item.colorFamily.lowercase()); append(' ')
            append(item.name.lowercase()); append(' ')
            append(item.brand.lowercase()); append(' ')
            item.tags.forEach { append(it.lowercase()); append(' ') }
        }
        val directMatches = words.count { haystack.contains(it) }
        val synonymMatches = words.count { word ->
            OCCASION_SYNONYMS[word]?.let { impliedTag -> item.tags.any { it.equals(impliedTag, ignoreCase = true) } } == true
        }
        return (directMatches + synonymMatches) * PROMPT_MATCH_BONUS
    }

    /**
     * Builds a human-readable explanation of what influenced a deterministic pick, so an outfit
     * is never shown with no rationale at all -- falls back to a generic reason when nothing
     * specific (occasion/weather/prompt) was set.
     */
    fun buildNote(constraints: OutfitConstraints, extra: List<String> = emptyList()): String {
        val clauses = buildList {
            constraints.occasion?.let { add("matched to a $it occasion") }
            addAll(extra)
            constraints.userPrompt?.takeIf { it.isNotBlank() }?.let { add("shaped by your note \"$it\"") }
        }
        return if (clauses.isEmpty()) {
            "Picked from your wardrobe for a balanced color pairing, favoring pieces you haven't " +
                "worn as recently to keep things varied."
        } else {
            "Picked from your wardrobe, " + clauses.joinToString(", and ") + "."
        }
    }

    /**
     * True when the item's warmth is so far off what the temperature calls for (a heavy parka on
     * a hot day, a summer top in a deep freeze) that it should never be suggested if any
     * alternative exists. Shared with [AiOutfitStrategy] to keep such items out of the
     * candidates shown to the on-device model.
     */
    fun isExtremeWarmthMismatch(item: ClothingItem, weather: WeatherInfo): Boolean =
        abs(item.warmthLevel - WarmthLevels.idealFor(weather.temperature)) >= EXTREME_WARMTH_MISMATCH

    /**
     * Favors items tagged for the current season; "All-Season" is neutral. Items tagged for a
     * different season are penalized past the tie margin so a Winter piece doesn't surface in
     * July just because the weather fetch failed. Seasons follow the northern hemisphere.
     */
    private fun seasonScore(itemSeason: String): Int {
        if (itemSeason.equals("All-Season", ignoreCase = true)) return 0
        return if (itemSeason.equals(currentSeason(), ignoreCase = true)) {
            SEASON_MATCH_BONUS
        } else {
            -SEASON_MISMATCH_PENALTY
        }
    }

    private fun currentSeason(): String = when (Calendar.getInstance().get(Calendar.MONTH)) {
        Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> "Winter"
        Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> "Spring"
        Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> "Summer"
        else -> "Fall"
    }

    private fun colorsCompatible(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        return COMPATIBLE_FAMILIES[a]?.contains(b) ?: true
    }
}
