package com.hazratbilal.aikit.state

sealed class ModelState {
    data object NotLoaded : ModelState()
    data object Loading : ModelState()
    data class Loaded(val displayName: String) : ModelState()
    data class Error(val message: String) : ModelState()
}