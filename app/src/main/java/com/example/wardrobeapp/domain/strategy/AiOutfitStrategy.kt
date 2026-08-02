package com.example.wardrobeapp.domain.strategy

import com.example.wardrobeapp.data.local.ai.LlmModelManager
import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.OutfitConstraints
import com.example.wardrobeapp.domain.model.WarmthLevels
import com.example.wardrobeapp.domain.model.WeatherInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class AiCandidate(
    val id: Long,
    val category: String,
    val tags: List<String>,
    val colorFamily: String,
    val warmthLevel: Int,
    val isWaterResistant: Boolean
)

@Serializable
internal data class AiOutfitPick(
    val topId: Long? = null,
    val bottomId: Long? = null,
    val footwearId: Long? = null,
    val outerwearId: Long? = null,
    val accessoryId: Long? = null,
    val reasoning: String? = null,
    /** Set by the model when the request needs items the wardrobe doesn't have. */
    val wardrobeGap: String? = null
)

internal data class AiPickResult(
    val items: List<ClothingItem>,
    val reasoning: String?,
    val wardrobeGap: String? = null
)

private val LenientJson = Json { ignoreUnknownKeys = true }

/**
 * Outfit strategy backed by a local MediaPipe LlmInference model. Only structured metadata
 * (tags/color family/warmth/water-resistance) is sent to the model -- no images, no name/brand/
 * description text -- keeping the prompt small and the input strictly non-identifying. Candidates
 * are pre-shortlisted by [OutfitScorer] so the model only ever chooses among a handful of already
 * weather/occasion-appropriate items per category, rather than the whole wardrobe.
 */
class AiOutfitStrategy(private val modelManager: LlmModelManager) : OutfitStrategy {

    override suspend fun generateOutfit(items: List<ClothingItem>, constraints: OutfitConstraints): Outfit {
        fun shortlist(category: String, required: Boolean): List<ClothingItem> =
            weatherAppropriateShortlist(
                OutfitScorer.topCandidates(items, category, constraints, emptyList(), CANDIDATE_LIMIT),
                constraints.weather,
                required
            )

        val candidatesByCategory = linkedMapOf(
            Category.TOPS to shortlist(Category.TOPS, required = true),
            Category.BOTTOMS to shortlist(Category.BOTTOMS, required = true),
            Category.FOOTWEAR to shortlist(Category.FOOTWEAR, required = false),
            Category.OUTERWEAR to shortlist(Category.OUTERWEAR, required = false),
            Category.ACCESSORIES to shortlist(Category.ACCESSORIES, required = false)
        )
        val missing = buildList {
            if (candidatesByCategory[Category.TOPS].isNullOrEmpty()) add(Category.TOPS)
            if (candidatesByCategory[Category.BOTTOMS].isNullOrEmpty()) add(Category.BOTTOMS)
        }
        if (missing.isNotEmpty()) throw IncompleteOutfitException(missing)

        val prompt = buildPrompt(candidatesByCategory, constraints)
        val raw = modelManager.generate(prompt).getOrThrow()
        // Only trust a null top/bottom as a deliberate choice when the user actually typed
        // something that could justify it -- otherwise a truncated/flaky reply from a small
        // on-device model would silently produce the same "pants only" bug this replaces.
        val allowRequiredOmission = !constraints.userPrompt.isNullOrBlank()
        val result = parseAndValidate(raw, candidatesByCategory, allowRequiredOmission)
        // The model judged the request unsatisfiable with this wardrobe (e.g. "going swimming"
        // typed but no swimwear exists) -- surface that instead of forcing a normal outfit.
        result.wardrobeGap?.let { throw WardrobeGapException(listOf(it)) }
        val reasoning = result.reasoning?.takeIf { it.isNotBlank() }
            ?: "Picked by the on-device AI from your wardrobe, weighing the occasion, weather, " +
                "and your request to put together a combination it judged worked well together."
        return Outfit(name = "AI Pick", items = result.items, note = reasoning, isAiGenerated = true)
    }

    private fun buildPrompt(candidatesByCategory: Map<String, List<ClothingItem>>, constraints: OutfitConstraints): String {
        val candidateJson = Json.encodeToString(
            candidatesByCategory.values.flatten().map { it.toAiCandidate() }
        )
        val occasionLine = constraints.occasion?.let { "Occasion: $it" } ?: "Occasion: any"
        val weatherLine = constraints.weather?.let { "Weather: ${it.temperature}°C, ${it.condition}" } ?: "Weather: unknown"
        val userLine = constraints.userPrompt?.takeIf { it.isNotBlank() }?.let { "User's request: \"$it\"" }
            ?: "User's request: none"
        val warmthLegend = (1..5).joinToString("; ") { level ->
            "$level = best for ${WarmthLevels.temperatureRangeLabel(level)}"
        }
        return """
            You are a fashion assistant picking one outfit from a closet. $occasionLine. $weatherLine. $userLine.
            Candidate items (grouped by category; ids are unique across the whole list):
            $candidateJson
            warmthLevel meaning: $warmthLegend.
            Rules:
            - Match each pick's warmthLevel to the given temperature. Never pick a heavy item
              (warmthLevel 4-5) in warm weather or a very light item (warmthLevel 1) in freezing
              weather, unless the user's request explicitly asks for it.
            - By default you MUST pick a topId and a bottomId from the candidates -- a complete outfit
              needs a socially acceptable top and bottom for public wear.
            - Only set topId or bottomId to null if the user's request clearly calls for it (e.g. "swimming",
              "just the jacket", "no shirt needed") -- and briefly say why in reasoning.
            - If the occasion or the user's request needs items that are simply not among the candidates
              (e.g. swimming but no swimwear, skiing but no ski gear, freezing weather but nothing warm),
              do NOT force a regular outfit. Set every id to null and write one short sentence in
              "wardrobeGap" telling the user what kind of items to add to their wardrobe. Use wardrobeGap
              ONLY when the request clearly cannot be satisfied; otherwise leave it null.
            - footwearId, outerwearId, and accessoryId are always optional; use null to skip them.
            - Include an accessoryId ONLY if it clearly matches the user's request or occasion (e.g. the
              user mentioned jewelry, a bag, a hat) -- do not add one just because one is available.
            - Prefer candidates whose tags/colorFamily fit the occasion, weather, and user's request.
            - Always fill in "reasoning" with 1-2 full sentences (about 30-50 words) explaining the
              pick: mention which items you chose, why their colors/tags work together, and how the
              occasion, weather, or user's request shaped the choice. Do not just repeat the rules.
            Reply with ONLY strict JSON, no markdown, no extra text, in exactly this shape:
            {"topId": <id or null>, "bottomId": <id or null>, "footwearId": <id or null>, "outerwearId": <id or null>, "accessoryId": <id or null>, "reasoning": "1-2 sentences (about 30-50 words) explaining the pick", "wardrobeGap": <null, or one short sentence saying what items the user should add>}
        """.trimIndent()
    }

    companion object {
        private const val CANDIDATE_LIMIT = 3
    }
}

private fun ClothingItem.toAiCandidate() = AiCandidate(id, category, tags, colorFamily, warmthLevel, isWaterResistant)

/**
 * Drops candidates whose warmth is wildly wrong for the weather (e.g. a parka in the heat) so
 * the on-device model can't even see them -- prompt instructions alone aren't reliable on small
 * models. For required categories (top/bottom) the unfiltered list is kept when everything would
 * be dropped: the user has to wear something. Optional categories are simply emptied, which is
 * how outerwear disappears entirely on a hot day.
 */
internal fun weatherAppropriateShortlist(
    candidates: List<ClothingItem>,
    weather: WeatherInfo?,
    required: Boolean
): List<ClothingItem> {
    if (weather == null) return candidates
    val fitting = candidates.filterNot { OutfitScorer.isExtremeWarmthMismatch(it, weather) }
    return if (fitting.isEmpty() && required) candidates else fitting
}

/**
 * Standalone, pure parsing of the model's reply, validated against the shortlisted candidate ids
 * (grouped by category, so an id from the wrong slot is rejected rather than silently accepted).
 * An id that doesn't match any candidate in its category is treated as a model mistake and
 * corrected to that category's top-scored candidate. Footwear/outerwear are always optional, so a
 * `null` there is honored as-is. Top/bottom are required by default: a `null` is only honored when
 * [allowRequiredOmission] is true (i.e. the user typed a prompt that could justify skipping one);
 * otherwise it's corrected to the top-scored candidate, same as an invalid id.
 */
internal fun parseAndValidate(
    raw: String,
    candidatesByCategory: Map<String, List<ClothingItem>>,
    allowRequiredOmission: Boolean = false
): AiPickResult {
    val jsonText = extractJsonObject(raw)
    val pick = LenientJson.decodeFromString<AiOutfitPick>(jsonText)

    // A declared wardrobe gap means "don't dress the user in something unsuitable" -- skip id
    // resolution entirely (the required-slot force-fill would otherwise build an outfit anyway).
    pick.wardrobeGap?.takeIf { it.isNotBlank() }?.let { gap ->
        return AiPickResult(items = emptyList(), reasoning = null, wardrobeGap = gap.trim())
    }

    fun resolve(category: String, id: Long?, required: Boolean): ClothingItem? {
        val candidates = candidatesByCategory[category].orEmpty()
        if (id == null) {
            return if (required && !allowRequiredOmission) candidates.firstOrNull() else null
        }
        return candidates.find { it.id == id } ?: candidates.firstOrNull()
    }

    val items = listOfNotNull(
        resolve(Category.TOPS, pick.topId, required = true),
        resolve(Category.BOTTOMS, pick.bottomId, required = true),
        resolve(Category.FOOTWEAR, pick.footwearId, required = false),
        resolve(Category.OUTERWEAR, pick.outerwearId, required = false),
        resolve(Category.ACCESSORIES, pick.accessoryId, required = false)
    )
    return AiPickResult(items, pick.reasoning)
}

private fun extractJsonObject(raw: String): String {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    require(start >= 0 && end > start) { "No JSON object found in AI response" }
    return raw.substring(start, end + 1)
}
