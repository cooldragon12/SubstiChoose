package com.substichoose.ai

/** Lifecycle of the on-device Gemma model, exposed by [GemmaService.state]. */
sealed interface ModelState {
    data object NotLoaded : ModelState
    data object Loading : ModelState
    data object Ready : ModelState
    /** No .litertlm file found. folderPath = where the user must adb push it. */
    data class Missing(val folderPath: String) : ModelState
    data class Failed(val message: String) : ModelState
}

/** Why the user wants a substitute; changes the wording of the substitutes prompt. */
enum class SwapReason { ALLERGY, MISSING }
