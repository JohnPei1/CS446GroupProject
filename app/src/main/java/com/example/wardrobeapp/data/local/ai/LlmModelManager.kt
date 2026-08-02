package com.example.wardrobeapp.data.local.ai

import android.content.Context
import android.os.StatFs
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Owns the on-device LLM (.task) file and the MediaPipe [LlmInference] engine built from it.
 * The model is fetched automatically from a free, ungated Hugging Face repo (verified to require
 * no login/license click-through) the first time the user opts in -- one toggle, no browser
 * hand-off, no file picker. Only this generic model file is ever downloaded; wardrobe data is
 * never sent anywhere, and inference runs entirely on-device.
 */
class LlmModelManager(private val context: Context) {

    private val modelDir: File get() = File(context.filesDir, "llm").apply { mkdirs() }
    fun modelFile(): File = File(modelDir, "model.task")
    fun isModelAvailable(): Boolean = modelFile().exists() && modelFile().length() > 0

    @Volatile private var engine: LlmInference? = null
    private val httpClient by lazy { OkHttpClient() }

    /**
     * Downloads the model to app-private storage, reporting progress via [onProgress]
     * (bytesDownloaded, totalBytes -- totalBytes is 0 if the server didn't report a length).
     * Writes to a temp file and only replaces [modelFile] on full success, so an interrupted
     * download never leaves a corrupt "available" model.
     */
    suspend fun downloadModel(onProgress: (downloaded: Long, total: Long) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            val tempFile = File(modelDir, "model.task.download")
            runCatching {
                val freeBytes = StatFs(context.filesDir.path).availableBytes
                if (freeBytes < MIN_FREE_BYTES) {
                    error("Not enough free storage to download the AI model (need ~${MIN_FREE_BYTES / (1024 * 1024)}MB free)")
                }
                closeEngine() // release any file lock on the previous model before replacing it

                val request = Request.Builder().url(MODEL_DOWNLOAD_URL).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Download failed (HTTP ${response.code})")
                    val body = response.body ?: error("Empty response body")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var downloaded = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                onProgress(downloaded, total)
                            }
                        }
                    }
                }
                if (!tempFile.renameTo(modelFile())) error("Couldn't finalize the downloaded model file")
            }.onFailure { tempFile.delete() }
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
        // litert (community) function-calling fine-tune of Gemma-3 270M -- confirmed via a direct
        // HTTP request to require no Hugging Face login/license acceptance (unlike the official
        // google/* and litert-community/Gemma3-* repos, which are gated). Small (~270MB) for a
        // fast one-time download, and its function-calling tuning suits our strict-JSON output need.
        private const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/2796gauravc/artha-functiongemma-270m-mediapipe/resolve/main/artha_functiongemma_v9_0_0.task"
        private const val MIN_FREE_BYTES = 400L * 1024 * 1024 // headroom beyond the model's own size
    }
}
