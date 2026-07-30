package com.hazratbilal.aikit.state

import com.hazratbilal.aikit.model.ChatMessage

data class ChatUiState(
    val modelState: ModelState = ModelState.NotLoaded,
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val streamingText: String = ""
)