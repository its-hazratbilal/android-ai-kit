package com.hazratbilal.aikit.core.manager

import com.hazratbilal.aikit.core.engine.LlmEngine
import com.hazratbilal.aikit.core.engine.isModelLoaded
import com.hazratbilal.aikit.core.model.LlmRequest
import com.hazratbilal.aikit.core.model.LlmResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.fold


class ModelManager(
    private val llmEngine: LlmEngine
) {

    private var loadedModelPath: String? = null
    private var systemPromptAppliedForCurrentLoad: String? = null

    private var hasGeneratedSinceLoad = false

    suspend fun loadModel(modelFilePath: String) {
        if (loadedModelPath == modelFilePath && llmEngine.state.value.isModelLoaded) return

        val currentState = llmEngine.state.value

        if (currentState is LlmEngine.State.Uninitialized || currentState is LlmEngine.State.Initializing) {
            val readyState = llmEngine.state.first {
                it is LlmEngine.State.Initialized || it is LlmEngine.State.Error
            }
            if (readyState is LlmEngine.State.Error) {
                throw readyState.exception
            }
        }

        if (llmEngine.state.value.isModelLoaded || llmEngine.state.value is LlmEngine.State.Error) {
            llmEngine.cleanUp()
        }

        llmEngine.loadModel(modelFilePath)
        loadedModelPath = modelFilePath
        systemPromptAppliedForCurrentLoad = null
        hasGeneratedSinceLoad = false
    }

    suspend fun resetConversation() {
        if (!hasGeneratedSinceLoad) return
        val path = loadedModelPath ?: return

        if (llmEngine.state.value.isModelLoaded) {
            llmEngine.cleanUp()
        }

        val readyState = llmEngine.state.first {
            it is LlmEngine.State.Initialized || it is LlmEngine.State.Error
        }
        if (readyState is LlmEngine.State.Error) {
            throw readyState.exception
        }

        llmEngine.loadModel(path)
        systemPromptAppliedForCurrentLoad = null
        hasGeneratedSinceLoad = false
    }

    suspend fun generate(request: LlmRequest): LlmResponse {
        require(llmEngine.state.value.isModelLoaded) {
            "No model loaded — call loadModel() before generate()"
        }

        if (request.systemPrompt != null && systemPromptAppliedForCurrentLoad == null) {
            llmEngine.setSystemPrompt(request.systemPrompt)
            systemPromptAppliedForCurrentLoad = request.systemPrompt
        }

        val result = try {
            llmEngine.sendUserPrompt(
                request.prompt,
                request.maxTokens
            ).fold(StringBuilder()) { acc, token ->
                acc.append(token)
            }.toString()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        }

        hasGeneratedSinceLoad = true
        return LlmResponse(generatedText = result)
    }

    suspend fun generateStreaming(request: LlmRequest): Flow<String> {
        require(llmEngine.state.value.isModelLoaded) {
            "No model loaded — call loadModel() before generateStreaming()"
        }

        if (request.systemPrompt != null && systemPromptAppliedForCurrentLoad == null) {
            llmEngine.setSystemPrompt(request.systemPrompt)
            systemPromptAppliedForCurrentLoad = request.systemPrompt
        }

        hasGeneratedSinceLoad = true
        return llmEngine.sendUserPrompt(request.prompt, request.maxTokens)
    }

    fun unload() {
        llmEngine.cleanUp()
        loadedModelPath = null
        systemPromptAppliedForCurrentLoad = null
        hasGeneratedSinceLoad = false
    }

    fun isLoaded(): Boolean = llmEngine.state.value.isModelLoaded

    fun currentModelPath(): String? = loadedModelPath
}