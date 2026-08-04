package com.example.wardrobeapp.domain.strategy

import android.util.Log
import com.example.wardrobeapp.BuildConfig
import com.example.wardrobeapp.data.remote.ai.AiClient
import com.example.wardrobeapp.domain.model.ClothingItem
import com.example.wardrobeapp.domain.model.Outfit
import com.example.wardrobeapp.domain.model.OutfitConstraints
import com.example.wardrobeapp.domain.model.WarmthLevels
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class AiCandidate(
    val id: Long,
    val category: String,
    val name: String,
    /** Brand, when set -- e.g. "Levi's" or "Nike" implies things about style/formality/fit
     *  that structured tags alone don't capture. */
    val brand: String? = null,
    val color: String,
    val colorFamily: String,
    val tags: List<String>,
    val warmthLevel: Int,
    val isWaterResistant: Boolean,
    /** The item's own notes, when present -- e.g. "can be worn over another top". */
    val notes: String? = null
)

@Serializable
internal data class AiOutfitPick(
    val topId: Long? = null,
    val bottomId: Long? = null,
    val footwearId: Long? = null,
    val outerwearId: Long? = null,
    val accessoryIds: List<Long>? = null,
    val reasoning: String? = null,
    /** Set by the model when the request needs items the wardrobe doesn't have. */
    val wardrobeGap: String? = null,
    /** A short weather heads-up (e.g. "rain is expected") when the pick honors the request
     *  despite an imperfect weather match. Never blocks the pick -- see buildPrompt's rule. */
    val weatherWarning: String? = null
)

internal data class AiPickResult(
    val items: List<ClothingItem>,
    val reasoning: String?,
    val wardrobeGap: String? = null,
    val weatherWarning: String? = null
)

private val LenientJson = Json { ignoreUnknownKeys = true }

/**
 * Outfit strategy backed by a cloud AI call (see [AiClient]). Sends each candidate's name,
 * brand, and structured metadata (tags/color/warmth/water-resistance/notes) -- never a photo.
 * Name/brand are included deliberately: "Levi's 501" or "Nike Air Force 1" tells the model far
 * more about what an item actually is than tags/color/warmth alone, and there's no real privacy
 * line between that and the free-text notes field, which was already being sent. Candidates are
 * pre-shortlisted by [OutfitScorer] so the model only ever chooses among a handful of already
 * weather/occasion-appropriate items per category, rather than the whole wardrobe.
 */
class AiOutfitStrategy(private val aiClient: AiClient) : OutfitStrategy {

    override suspend fun generateOutfit(items: List<ClothingItem>, constraints: OutfitConstraints): Outfit {
        fun candidates(category: String) =
            OutfitScorer.topCandidates(items, category, constraints, emptyList(), CANDIDATE_LIMIT)

        // Tops whose own notes describe layering (e.g. "can be worn over another top") are
        // offered as extra outerwear candidates alongside real outerwear -- see buildPrompt's
        // layering rule. Scored/limited separately from the main Tops shortlist so a piece that
        // wouldn't otherwise make the cut as a *top* pick (but is exactly what's wanted as a
        // layer) still gets a chance to surface.
        val layerableTops = items
            .filter { it.category.equals(Category.TOPS, ignoreCase = true) && it.mightLayerAsOuterwear() }
            .sortedByDescending { OutfitScorer.score(it, constraints, emptyList()) }
            .take(LAYERABLE_TOP_LIMIT)

        // No hard weather-based exclusion here (there used to be one, via a dedicated
        // weatherAppropriateShortlist filter) -- it caused real, repeated bad picks. When *some*
        // candidates in a category matched the weather and others didn't, the mismatched ones
        // (which were often the occasion-appropriate ones, e.g. formal pants or a blazer) were
        // silently dropped from what the AI could even see, regardless of occasion. That's how a
        // funeral request ended up in shorts with weather-aware on, while turning it off (and so
        // never filtering at all) picked correctly. OutfitScorer.topCandidates already soft-scores
        // warmth mismatch into the ranking (see OutfitScorer.score's occasion vs. warmth weights,
        // occasion match is worth far more than a warmth mismatch penalty), so formal/occasion
        // items still surface as candidates; buildPrompt's rules tell the model itself to prefer
        // the occasion/request over strict warmth and only defer to weather when it's extreme.
        val candidatesByCategory = linkedMapOf(
            Category.TOPS to candidates(Category.TOPS),
            Category.BOTTOMS to candidates(Category.BOTTOMS),
            Category.FOOTWEAR to candidates(Category.FOOTWEAR),
            Category.OUTERWEAR to (candidates(Category.OUTERWEAR) + layerableTops).distinctBy { it.id },
            Category.ACCESSORIES to candidates(Category.ACCESSORIES)
        )
        val missing = buildList {
            if (candidatesByCategory[Category.TOPS].isNullOrEmpty()) add(Category.TOPS)
            if (candidatesByCategory[Category.BOTTOMS].isNullOrEmpty()) add(Category.BOTTOMS)
        }
        if (missing.isNotEmpty()) throw IncompleteOutfitException(missing)

        val prompt = buildPrompt(candidatesByCategory, constraints)
        logDebug("PROMPT", prompt)
        val raw = aiClient.generate(prompt).getOrThrow()
        logDebug("REPLY", raw)
        // Only trust a null top/bottom as a deliberate choice when the user actually typed
        // something that could justify it -- otherwise a flaky reply would silently produce the
        // same "pants only" bug this replaces.
        val allowRequiredOmission = !constraints.userPrompt.isNullOrBlank()
        val result = try {
            parseAndValidate(raw, candidatesByCategory, allowRequiredOmission)
        } catch (e: Exception) {
            // The reply above is already logged, but a dedicated failure line makes this
            // instantly greppable instead of having to notice "no result after REPLY" -- found
            // the hard way when the model returned incoherent, non-JSON text.
            logDebug("PARSE_FAILURE", e.message ?: e.toString())
            throw e
        }
        // The model judged the request unsatisfiable with this wardrobe (e.g. "going swimming"
        // typed but no swimwear exists) -- surface that instead of forcing a normal outfit.
        result.wardrobeGap?.let { throw WardrobeGapException(listOf(it)) }
        val note = composeNote(result.items, constraints) + (result.weatherWarning?.let { " $it" } ?: "")
        return Outfit(name = "AI Pick", items = result.items, note = note, isAiGenerated = true)
    }

    /**
     * The model's own free-text "reasoning" is logged (see [logDebug]) but deliberately never
     * shown to the user -- on a model this small it regularly describes a different outfit than
     * the one it actually returned (e.g. mentioning items or reasons that don't match the ids in
     * the same reply). Building the note from the resolved items instead guarantees it always
     * matches what's on screen, regardless of how coherent the model's prose is. The narrower
     * [AiPickResult.weatherWarning] field is trusted and appended separately (see caller) -- it's
     * a short, scoped heads-up rather than a full description of the outfit, so it doesn't carry
     * the same risk of contradicting what's actually shown.
     */
    private fun composeNote(items: List<ClothingItem>, constraints: OutfitConstraints): String {
        val pieces = items.joinToString(", ") { "${it.color.ifBlank { it.colorFamily }.lowercase()} ${it.category.lowercase()}" }
        return OutfitScorer.buildNote(constraints, extra = listOf("combining $pieces"))
    }

    private fun buildPrompt(candidatesByCategory: Map<String, List<ClothingItem>>, constraints: OutfitConstraints): String {
        val candidateJson = Json.encodeToString(
            candidatesByCategory.values.flatten().distinctBy { it.id }.map { it.toAiCandidate() }
        )
        val occasionLine = constraints.occasion?.let { "Occasion: $it" } ?: "Occasion: any"
        val weatherLine = constraints.weather?.let { "Weather: ${it.temperature}°C, ${it.condition}" } ?: "Weather: unknown"
        val userLine = constraints.userPrompt?.takeIf { it.isNotBlank() }?.let { "User's request: \"$it\"" }
            ?: "User's request: none"
        val warmthLegend = (1..5).joinToString("; ") { level ->
            "$level = best for ${WarmthLevels.temperatureRangeLabel(level)}"
        }
        // Any guidance at all -- an occasion chip, or typed text -- gets a much stronger "weather
        // can't override this" instruction than a completely blank generate. Once the user has
        // indicated anything about what they want, weather should almost never change the answer
        // -- but a bare Generate tap with no occasion and nothing typed should treat weather as
        // the primary signal, strongly enough to reliably reach for shorts on a hot day and avoid
        // them on a cool one (a real reported bug: the softer wording here previously wasn't
        // decisive enough in either direction). This must stay in sync with OutfitScorer.score's
        // own genericRequest check, which gates the same distinction in the actual candidate
        // ranking -- an occasion alone (e.g. just tapping "Formal") already has to reliably
        // outrank an ordinary warmth mismatch there too, or this instruction would be fighting
        // candidates that never even reach the model.
        val hasGuidance = constraints.occasion != null || constraints.userPrompt?.isNotBlank() == true
        val weatherPriorityRule = if (hasGuidance) {
            """
            - The user specified something above -- a typed request and/or an occasion -- honor it
              as directly as possible regardless of weather. Never silently swap in different items
              just because of temperature (e.g. do not replace formal pants with shorts, or a
              blazer with something lighter, just because it's warm). Use intuition about what it
              implies: a wedding, funeral, job interview, gala, or office day is usually indoor and
              formal, so warmth is nearly irrelevant even if the request doesn't say "regardless of
              weather" outright -- a blazer is a normal, expected choice for those even on a hot
              day. Only refuse via "wardrobeGap" when conditions are genuinely extreme (a blizzard,
              severe storm, dangerous heat/cold) AND the request is truly incompatible with them --
              e.g. a beach outfit in a blizzard, a heavy coat in a heatwave. Ordinary weather, even
              quite hot or cool, is never by itself a reason to change or refuse what was asked for.
            - If honoring the request means the weather isn't a great match (e.g. rain is
              expected and the pick isn't water-resistant, or it's cold and something light was
              specifically requested), still make the pick -- but add one short, plain sentence to
              "weatherWarning" giving the user a heads-up (e.g. "Heads up: rain is expected, so
              you may want a water-resistant layer."). Leave weatherWarning null when there's no
              real weather concern worth mentioning.
            """.trimIndent()
        } else {
            """
            - No occasion and no request were given, so weather is the primary factor here: match
              warmthLevel closely to the temperature. On a hot day, prefer warmthLevel 1-2 and
              avoid 4-5 entirely -- shorts and light pieces are exactly what's wanted, not long
              sleeves or heavy layers. On a cold day, prefer warmthLevel 4-5 and avoid 1-2 entirely
              -- shorts or very light pieces are wrong here. Leave "weatherWarning" null; there's
              nothing to warn about when the pick already matches the weather.
            """.trimIndent()
        }
        val instruction = """
            You are a fashion assistant. Reply with ONLY a single strict JSON object -- no markdown,
            no extra text, no other conversation.
            $occasionLine. $weatherLine. $userLine.
            Candidates (grouped by category, ids unique across all):
            $candidateJson
            warmthLevel: $warmthLegend.
            Rules:
            $weatherPriorityRule
            - topId and bottomId are required. Only null one if the request clearly calls for it (e.g.
              swimming) and say why in reasoning.
            - If the request needs a whole category of items the wardrobe simply doesn't have (e.g.
              swimming or skiing with no swimwear/ski gear among the candidates), set ALL ids null
              and put a short suggestion in "wardrobeGap" -- this is about missing items, not
              weather; see the weather rule above for when weather itself can trigger a wardrobeGap.
            - footwearId/outerwearId are optional (null to skip). Outerwear also adds formality, not
              just warmth -- for Formal/funeral/interview/wedding requests, always include a formal
              outerwear piece (e.g. a blazer) if one exists among the candidates, regardless of
              temperature, since it's a single removable layer; only skip it if nothing formal-enough
              exists or conditions are genuinely extreme.
            - Some Tops candidates double as outerwear -- check each candidate's "notes". If a Tops
              item's notes describe layering (worn over another top, or something worn underneath
              it), you may use its id for outerwearId too when that fits. Never use the same id for
              both topId and outerwearId.
            - accessoryIds is a list of zero or more ids -- you may combine multiple compatible
              accessories (e.g. a watch and a tie together), but don't pick two of the same
              conflicting type (e.g. two belts) unless that's clearly intended. Only include ids that
              clearly match the request/occasion; use [] to skip.
            - Prefer isWaterResistant items for outerwear/footwear/accessories when the weather
              condition involves rain, showers, or snow.
            - Use each candidate's "name" and "brand" (when present) for what they imply about the
              actual item -- e.g. "Levi's 501" suggests classic straight-leg denim, "Nike Air Force
              1" suggests a casual/athletic sneaker -- not just its structured tags/color/warmth.
            - Use each candidate's specific "color" (not just its broader colorFamily) to judge real
              color pairing and to vary the combination -- most wardrobes have many candidates in the
              same colorFamily (e.g. "Neutral") that still differ meaningfully by color (white vs.
              charcoal vs. navy). Don't default to the same one or two items every time when other
              candidates would pair just as well.
            - Prefer candidates matching the occasion/weather/request. Fill "reasoning" with 1-2
              sentences (30-50 words) explaining the pick.
            Reply with ONLY strict JSON, no markdown, no extra text, in exactly this shape:
            {"topId": <id or null>, "bottomId": <id or null>, "footwearId": <id or null>, "outerwearId": <id or null>, "accessoryIds": [<id>, ...] or [], "reasoning": "1-2 sentences (about 30-50 words) explaining the pick", "wardrobeGap": <null, or one short sentence saying what items the user should add>, "weatherWarning": <null, or one short sentence>}
        """.trimIndent()
        return instruction
    }

    /**
     * Debug-only visibility into exactly what the model was shown and what it replied, so
     * "why did the AI pick X" is answerable from logcat instead of guesswork. Never compiled
     * into a release build. Chunked because a single Log line is truncated by logcat around
     * ~4000 characters and the prompt can run longer than that with a large wardrobe.
     */
    private fun logDebug(label: String, text: String) {
        if (!BuildConfig.DEBUG) return
        text.chunked(3500).forEachIndexed { i, chunk ->
            Log.d(TAG, "[$label ${i + 1}] $chunk")
        }
    }

    companion object {
        // Was 4, from the old on-device model's tight token budget -- that constraint is gone
        // with a cloud model, and a small limit is a real risk on its own: an occasion-
        // appropriate item that the deterministic pre-scoring doesn't happen to rank highly
        // (e.g. a wardrobe with mostly casual items) could simply never reach the AI to be
        // considered at all. A generous limit costs a bit more prompt size, which is cheap here.
        private const val CANDIDATE_LIMIT = 10
        // Kept small -- these are extra candidates layered on top of the real outerwear
        // shortlist, not a replacement for it.
        private const val LAYERABLE_TOP_LIMIT = 2
        private const val TAG = "AiOutfitStrategy"
    }
}

private val LAYERING_KEYWORDS = listOf("outerwear", "layer", "underneath", "over a", "over your", "over another", "jacket", "cardigan")

/** True when this top's own notes suggest it can also work as outerwear (see buildPrompt). */
internal fun ClothingItem.mightLayerAsOuterwear(): Boolean {
    val text = description.lowercase()
    return text.isNotBlank() && LAYERING_KEYWORDS.any { text.contains(it) }
}

private fun ClothingItem.toAiCandidate() = AiCandidate(
    id = id,
    category = category,
    name = name,
    brand = brand.takeIf { it.isNotBlank() },
    color = color,
    colorFamily = colorFamily,
    tags = tags,
    warmthLevel = warmthLevel,
    isWaterResistant = isWaterResistant,
    notes = description.takeIf { it.isNotBlank() }
)

/**
 * Standalone, pure parsing of the model's reply, validated against the shortlisted candidate ids
 * (grouped by category, so an id from the wrong slot is rejected rather than silently accepted;
 * the exception is outerwearId, which is also checked against the Outerwear bucket's cross-listed
 * layerable Tops -- see [AiOutfitStrategy.generateOutfit]). An id that doesn't match any candidate
 * in its category is treated as a model mistake and corrected to that category's top-scored
 * candidate. Footwear/outerwear are always optional, so a `null` there is honored as-is.
 * Top/bottom are required by default: a `null` is only honored when [allowRequiredOmission] is
 * true (i.e. the user typed a prompt that could justify skipping one); otherwise it's corrected
 * to the top-scored candidate, same as an invalid id.
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
    // Only honored when topId/bottomId are ALSO both null, matching the instruction the model
    // was actually given ("set ALL ids null and put a short suggestion in wardrobeGap"). Found on
    // a real (if weak) model reply: it picked valid top/bottom ids from the candidates *and*
    // filled in wardrobeGap with unrelated commentary -- treating that as authoritative would
    // discard a perfectly good pick over a field the model clearly wasn't using as instructed.
    //
    // ALSO gated on allowRequiredOmission (i.e. the user actually typed a free-text request).
    // Found on real device logs: with no occasion and no prompt (a plain "Generate" tap, nothing
    // for the deterministic WardrobeGapChecker upstream to even evaluate), the model would still
    // occasionally hedge with a nonsense non-blank wardrobeGap instead of just picking from
    // perfectly good candidates -- with no specific ask, nothing about the request can
    // legitimately be "unsatisfiable", so a refusal-shaped output in that situation is model
    // confusion, not a real gap.
    pick.wardrobeGap
        ?.takeIf { it.isNotBlank() && pick.topId == null && pick.bottomId == null && allowRequiredOmission }
        ?.let { gap ->
            return AiPickResult(items = emptyList(), reasoning = null, wardrobeGap = gap.trim())
        }

    fun resolve(category: String, id: Long?, required: Boolean): ClothingItem? {
        val candidates = candidatesByCategory[category].orEmpty()
        if (id == null) {
            return if (required && !allowRequiredOmission) candidates.firstOrNull() else null
        }
        return candidates.find { it.id == id } ?: candidates.firstOrNull()
    }

    val topItem = resolve(Category.TOPS, pick.topId, required = true)
    val bottomItem = resolve(Category.BOTTOMS, pick.bottomId, required = true)
    val footwearItem = resolve(Category.FOOTWEAR, pick.footwearId, required = false)
    // A layerable top cross-listed as outerwear could, in principle, get echoed back as the same
    // id for both slots -- wearing one physical garment as both your top and your "layer" over it
    // makes no sense, so drop the duplicate rather than show the same item twice.
    val outerwearItem = resolve(Category.OUTERWEAR, pick.outerwearId, required = false)
        ?.takeIf { it.id != topItem?.id }
    val accessoryItems = pick.accessoryIds.orEmpty()
        .distinct()
        .mapNotNull { id -> candidatesByCategory[Category.ACCESSORIES].orEmpty().find { it.id == id } }

    val items = listOfNotNull(topItem, bottomItem, footwearItem, outerwearItem) + accessoryItems
    return AiPickResult(items, pick.reasoning, weatherWarning = pick.weatherWarning?.trim()?.takeIf { it.isNotBlank() })
}

private fun extractJsonObject(raw: String): String {
    val start = raw.indexOf('{')
    val end = raw.lastIndexOf('}')
    require(start >= 0 && end > start) { "No JSON object found in AI response" }
    return raw.substring(start, end + 1)
}
