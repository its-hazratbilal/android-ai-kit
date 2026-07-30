package com.hazratbilal.aikit

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hazratbilal.aikit.chat.AiKitChat
import com.hazratbilal.aikit.chat.chat
import com.hazratbilal.aikit.core.AiKitEngine
import com.hazratbilal.aikit.model.ChatMessage
import com.hazratbilal.aikit.state.ChatUiState
import com.hazratbilal.aikit.state.ModelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = AiKitEngine.create(application)
    private val chat = engine.chat()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /**
     * Native inference requires a real filesystem path — content:// URIs from
     * the system file picker can't be opened directly by llama.cpp. Copy the
     * picked file into app-internal storage first, then load from that path.
     *
     * Always copies to a fixed filename since this sample only supports one
     * loaded model at a time — a new pick simply replaces the previous file.
     */
    fun onModelFilePicked(uri: Uri) {
        val displayName = queryDisplayName(uri)

        if (displayName == null || !displayName.lowercase().endsWith(".gguf")) {
            _uiState.update {
                it.copy(modelState = ModelState.Error("Please select a .gguf model"))
            }
            return
        }

        _uiState.update { it.copy(modelState = ModelState.Loading) }

        viewModelScope.launch {
            val destFile = try {
                withContext(Dispatchers.IO) {
                    copyUriToInternalStorage(uri)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(modelState = ModelState.Error("Failed to read file: ${e.message}"))
                }
                return@launch
            }

            engine.loadModel(destFile.absolutePath, object : AiKitEngine.ModelStateListener {
                override fun onModelLoading() {
                    _uiState.update { it.copy(modelState = ModelState.Loading) }
                }

                override fun onModelLoaded() {
                    _uiState.update {
                        it.copy(modelState = ModelState.Loaded(displayName))
                    }
                }

                override fun onModelError(error: Throwable) {
                    _uiState.update {
                        it.copy(modelState = ModelState.Error(error.message ?: "Unknown error"))
                    }
                }

                override fun onModelUnloaded() {
                    _uiState.update { it.copy(modelState = ModelState.NotLoaded) }
                }
            })
        }
    }

    /**
     * SAF content:// URIs don't expose a reliable filename via lastPathSegment
     * across all providers — querying OpenableColumns.DISPLAY_NAME is the
     * correct way to get the real, user-facing file name.
     */
    private fun queryDisplayName(uri: Uri): String? {
        val context = getApplication<Application>()
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }

    private fun copyUriToInternalStorage(uri: Uri): File {
        val context = getApplication<Application>()
        val destFile = File(context.filesDir, "model_${System.currentTimeMillis()}.gguf")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to open selected file")

        return destFile
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val prompt = state.inputText.trim()

        if (prompt.isEmpty() || state.isGenerating || state.modelState !is ModelState.Loaded) return

        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage(text = prompt, isUser = true),
                inputText = "",
                streamingText = ""
            )
        }

        chat.sendMessage(prompt, listener = object : AiKitChat.ChatListener {
            override fun onGenerationStarted() {
                _uiState.update { it.copy(isGenerating = true, streamingText = "") }
            }

            override fun onToken(token: String) {
                _uiState.update { it.copy(streamingText = it.streamingText + token) }
            }

            override fun onGenerationComplete(fullResponse: String) {
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage(text = fullResponse, isUser = false),
                        isGenerating = false,
                        streamingText = ""
                    )
                }
            }

            override fun onGenerationError(error: Throwable) {
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage(
                            text = "Error: ${error.message}",
                            isUser = false
                        ),
                        isGenerating = false,
                        streamingText = ""
                    )
                }
            }

            override fun onCancelled() {
                _uiState.update { it.copy(isGenerating = false, streamingText = "") }
            }
        })
    }

    fun cancelGeneration() {
        chat.cancelGeneration()
    }

    override fun onCleared() {
        engine.unloadModel()
        super.onCleared()
    }
}