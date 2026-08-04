package com.example.wardrobeapp.data.remote.ai

/**
 * Abstraction over a cloud AI text-completion call, so
 * [com.example.wardrobeapp.domain.strategy.AiOutfitStrategy] doesn't depend on a specific
 * provider's request/response shape. [GeminiAiClient] is the only implementation today, but this
 * keeps a provider swap a one-file change if that's ever needed again.
 */
interface AiClient {
    /** True when this client has everything it needs (e.g. an API key) to attempt a call. */
    fun isConfigured(): Boolean

    /** Sends [prompt] and returns the raw text reply, or a failure on any network/API error. */
    suspend fun generate(prompt: String): Result<String>
}
