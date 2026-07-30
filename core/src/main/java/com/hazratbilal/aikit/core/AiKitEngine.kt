package com.hazratbilal.aikit.core

import android.content.Context
import com.hazratbilal.aikit.core.engine.LlamaCppEngine
import com.hazratbilal.aikit.core.manager.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Public entry point for loading and managing on-device LLM models.
 *
 * Usage:
 * ```
 * val engine = AiKitEngine.create(context)
 * engine.loadModel(modelPath, object : AiKitEngine.ModelStateListener {
 *     override fun onModelLoading() { }
 *     override fun onModelLoaded() { }
 *     override fun onModelError(error: Throwable) { }
 *     override fun onModelUnloaded() { }
 * })
 * ```
 */
class AiKitEngine private constructor(
    private val appContext: Context
) {

    /** Callback interface for observing model lifecycle events. */
    interface ModelStateListener {
        fun onModelLoading()
        fun onModelLoaded()
        fun onModelError(error: Throwable)
        fun onModelUnloaded()
    }

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loadJob: Job? = null

    // LlamaCppEngine.getInstance() takes a Context directly and manages its
    // own singleton internally — safe to call multiple times, always returns
    // the same instance.
    private val llmEngine = LlamaCppEngine.getInstance(appContext)

    // Internal so :chat module can reach it via `api(project(":core"))`,
    // without exposing ModelManager to external consumers of the library.
    val modelManager = ModelManager(llmEngine)

    /**
     * Loads a GGUF model from [modelPath]. Safe to call again with a different
     * path to switch models — [ModelManager] handles unloading the previous
     * model internally before loading the new one.
     */
    fun loadModel(modelPath: String, listener: ModelStateListener) {
        loadJob?.cancel()
        loadJob = engineScope.launch {
            try {
                listener.onModelLoading()
                modelManager.loadModel(modelPath)
                listener.onModelLoaded()
            } catch (t: Throwable) {
                listener.onModelError(t)
            }
        }
    }

    /** Unloads the currently loaded model, freeing native memory. */
    fun unloadModel() {
        loadJob?.cancel()
        modelManager.unload()
    }

    /** Returns true if a model is currently loaded and ready for inference. */
    fun isModelLoaded(): Boolean = modelManager.isLoaded()

    /** Returns the file path of the currently loaded model, or null if none is loaded. */
    fun currentModelPath(): String? = modelManager.currentModelPath()

    companion object {
        fun create(context: Context): AiKitEngine = AiKitEngine(context.applicationContext)
    }
}