package com.substichoose.ui.scan

import androidx.lifecycle.ViewModel
import com.substichoose.pipeline.ScanStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The scan screen only takes the photo; reading it happens in the background (ScanQueue). */
sealed interface ScanUiState {
    data object Camera : ScanUiState
    data class Failed(val message: String) : ScanUiState
}

class ScanViewModel : ViewModel() {

    private val _state = MutableStateFlow<ScanUiState>(ScanUiState.Camera)
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun showError(message: String) {
        _state.value = ScanUiState.Failed(message)
    }

    fun retry() {
        _state.value = ScanUiState.Camera
    }
}

/** Friendly text for each pipeline stage. */
fun ScanStage.friendlyText(): String = when (this) {
    ScanStage.COPYING -> "Getting the photo ready…"
    ScanStage.READING_TEXT -> "Reading the recipe…"
    ScanStage.UNDERSTANDING -> "Understanding ingredients with Gemma…"
    ScanStage.SAVING -> "Saving…"
}
