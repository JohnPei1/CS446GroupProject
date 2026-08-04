package com.example.wardrobeapp.data.remote.ai

import android.util.Log
import com.example.wardrobeapp.BuildConfig
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Calls Google's Gemini API for outfit suggestions. Replaces the old on-device MediaPipe model
 * (see git history) -- that approach hit a hard ceiling where every model small enough to not
 * risk crashing a budget phone (SIGABRT/OOM, fought at length before) was also too weak to
 * produce reliably coherent output. A cloud call trades "no internet required" for a model that
 * actually understands the prompt, at the cost of wardrobe metadata (tags/colors/warmth -- never
 * images or names) leaving the device when this is used; the Settings screen discloses that.
 */
class GeminiAiClient : AiClient {

    private val api: GeminiApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApi::class.java)
    }

    override fun isConfigured(): Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    override suspend fun generate(prompt: String): Result<String> = runCatching {
        check(isConfigured()) { "No Gemini API key configured for this build (see local.properties)" }
        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))),
            generationConfig = GeminiGenerationConfig()
        )
        val response = withTimeoutOrNull(TIMEOUT_MS) {
            api.generateContent(MODEL, BuildConfig.GEMINI_API_KEY, request)
        } ?: error("AI response timed out after ${TIMEOUT_MS}ms")

        response.promptFeedback?.blockReason?.let { reason ->
            error("Request blocked by Gemini's safety filters: $reason")
        }
        val text = response.candidates.orEmpty()
            .firstOrNull()?.content?.parts.orEmpty()
            .firstOrNull()?.text
        text?.takeIf { it.isNotBlank() } ?: error("Empty reply from Gemini")
    }.onFailure { logFailure(it) }

    /**
     * Mirrors the debug-only failure logging the old on-device manager had -- proved essential
     * for diagnosing AI issues from real device logs rather than guessing.
     */
    private fun logFailure(t: Throwable) {
        if (BuildConfig.DEBUG) Log.w(TAG, "Gemini generate failed: ${t.message}", t)
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/"
        // gemini-2.0-flash (the obvious first choice) turned out to have a hard 0 free-tier quota
        // on this account by this point -- confirmed via the account's own rate-limit dashboard
        // and a direct curl call, independent of this app's code. gemini-3.5-flash-lite is what
        // that dashboard showed real headroom for (15 RPM / 500 RPD on the free tier) and was
        // confirmed working with a real 200 response. If quota errors show up again later, check
        // https://ai.dev/rate-limit for which models currently have non-zero free-tier limits.
        private const val MODEL = "gemini-3.5-flash-lite"
        private const val TIMEOUT_MS = 20_000L
        private const val TAG = "GeminiAiClient"
    }
}
