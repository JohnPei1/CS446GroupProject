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
    private const val SEASON_MATCH_BONUS = 6
    private const val SEASON_MISMATCH_PENALTY = 8

    private val PROMPT_STOPWORDS = setOf(
        "and", "the", "for", "with", "your", "need", "want", "just", "some",
        "this", "that", "outfit", "look", "please", "wear", "wearing"
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
            score += 10 - warmthMismatch * 4
            // An item 3+ levels off (a heavy parka on a hot day) is never a reasonable "variety"
            // pick -- push it well outside the tie margin so other bonuses can't rescue it.
            if (isExtremeWarmthMismatch(item, weather)) score -= EXTREME_WARMTH_PENALTY
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
     * (not just when an on-device AI model is imported and enabled).
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
        val matches = words.count { haystack.contains(it) }
        return matches * PROMPT_MATCH_BONUS
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
