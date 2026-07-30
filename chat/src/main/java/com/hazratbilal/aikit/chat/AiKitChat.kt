@file:OptIn(InternalAiKitApi::class)

package com.hazratbilal.aikit.chat

import com.hazratbilal.aikit.core.AiKitEngine
import com.hazratbilal.aikit.core.InternalAiKitApi
import com.hazratbilal.aikit.core.model.LlmRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * Public entry point for sending chat prompts to a loaded [AiKitEngine] model
 * and receiving streamed token callbacks.
 *
 * Stateless: no history or session tracking is performed here — each
 * [sendMessage] call is independent.
 */
class AiKitChat internal constructor(private val engine: AiKitEngine) {

    /** Callback interface for observing chat generation events. */
    interface ChatListener {
        fun onGenerationStarted()
        fun onToken(token: String)
        fun onGenerationComplete(fullResponse: String)
        fun onGenerationError(error: Throwable)
        fun onCancelled()
    }

    private val chatScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var generationJob: Job? = null

    /**
     * Sends [prompt] to the loaded model and streams the response token-by-token
     * via [listener]. Only one generation can run at a time — calling this again
     * while a generation is in progress cancels the previous one first.
     *
     * Note: [maxTokens] matches [LlmRequest]'s default of 512.
     */
    @OptIn(InternalAiKitApi::class)
    fun sendMessage(
        prompt: String,
        maxTokens: Int = 512,
        systemPrompt: String? = null,
        listener: ChatListener
    ) {
        if (!engine.isModelLoaded()) {
            listener.onGenerationError(
                IllegalStateException("No model is loaded. Call AiKitEngine.loadModel() first.")
            )
            return
        }

        generationJob?.cancel()
        generationJob = chatScope.launch {
            try {
                listener.onGenerationStarted()

                val request = LlmRequest(
                    prompt = prompt,
                    systemPrompt = systemPrompt,
                    maxTokens = maxTokens
                    // temperature intentionally omitted — not wired to the
                    // native layer yet (LlamaCppEngine.sendUserPrompt has no
                    // temperature param), so setting it here would be a no-op.
                )

                val fullResponse = StringBuilder()

                engine.modelManager.generateStreaming(request).collect { token ->
                    fullResponse.append(token)
                    listener.onToken(token)
                }

                listener.onGenerationComplete(fullResponse.toString())

            } catch (ce: CancellationException) {
                listener.onCancelled()
            } catch (t: Throwable) {
                listener.onGenerationError(t)
            }
        }
    }

    /** Cancels the current generation, if any. Triggers [ChatListener.onCancelled]. */
    fun cancelGeneration() {
        generationJob?.cancel()
    }
}