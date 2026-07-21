package com.example.wardrobeapp.data.local.ai

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream

/**
 * Owns the on-device LLM (.task) file and the MediaPipe [LlmInference] engine built from it.
 * The model is imported manually by the user (Settings > AI Outfit Suggestions) via a file
 * picker -- there is no network download here, so no credentials/auth flow is needed and no
 * wardrobe data is ever sent anywhere. Inference runs entirely on-device.
 */
class LlmModelManager(private val context: Context) {

    private val modelDir: File get() = File(context.filesDir, "llm").apply { mkdirs() }
    fun modelFile(): File = File(modelDir, "model.task")
    fun isModelAvailable(): Boolean = modelFile().exists() && modelFile().length() > 0

    @Volatile private var engine: LlmInference? = null

    /** Copies the user-picked .task file into app storage. Does not touch the network. */
    suspend fun importModel(sourceUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val freeBytes = StatFs(context.filesDir.path).availableBytes
            if (freeBytes < MIN_FREE_BYTES) {
                error("Not enough free storage to import the model (need ~${MIN_FREE_BYTES / (1024 * 1024)}MB free)")
            }
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: error("Could not open the selected file")
            closeEngine() // release any file lock on the previous model before overwriting it
            input.use { stream ->
                FileOutputStream(modelFile()).use { output -> stream.copyTo(output) }
            }
            Unit
        }.onFailure { modelFile().delete() }
    }

    fun deleteModel() {
        closeEngine()
        modelFile().delete()
    }

    private fun closeEngine() {
        engine?.close()
        engine = null
    }

    private fun engine(): LlmInference = engine ?: synchronized(this) {
        engine ?: LlmInference.createFromOptions(
            context,
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile().absolutePath)
                .setMaxTokens(1024)
                .build()
        ).also { engine = it }
    }

    /**
     * Runs a prompt through the local model. Always returns [Result] rather than throwing, so
     * callers can fall back to the deterministic strategy on any failure. Note: [generateResponse]
     * is a blocking call with no native cancellation hook, so a timeout abandons the coroutine
     * but the underlying inference call may keep running briefly in the background.
     */
    suspend fun generate(prompt: String, timeoutMs: Long = 15_000): Result<String> =
        withContext(Dispatchers.Default) {
            runCatching {
                withTimeoutOrNull(timeoutMs) { engine().generateResponse(prompt) }
                    ?: error("AI response timed out")
            }
        }

    companion object {
        private const val MIN_FREE_BYTES = 200L * 1024 * 1024 // headroom beyond the model's own size
    }
}
