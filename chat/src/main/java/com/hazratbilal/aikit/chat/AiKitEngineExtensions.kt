package com.hazratbilal.aikit.chat

import com.hazratbilal.aikit.core.AiKitEngine

fun AiKitEngine.chat(): AiKitChat = AiKitChat(this)