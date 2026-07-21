package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.OutfitConstraints
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
            val idealWarmth = idealWarmthFor(weather.temperature)
            score += 10 - abs(item.warmthLevel - idealWarmth) * 4
            if (weather.condition.contains("rain", ignoreCase = true) && item.isWaterResistant) {
                score += 15
            }
        }

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

    /** Bucketed target warmth (1-5) for a given temperature. */
    private fun idealWarmthFor(temperatureCelsius: Double): Int = when {
        temperatureCelsius <= 0 -> 5
        temperatureCelsius <= 8 -> 4
        temperatureCelsius <= 15 -> 3
        temperatureCelsius <= 22 -> 2
        else -> 1
    }

    private fun colorsCompatible(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        return COMPATIBLE_FAMILIES[a]?.contains(b) ?: true
    }
}
