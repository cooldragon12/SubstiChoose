package com.substichoose.ui.substitutes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.substichoose.AppContainer
import com.substichoose.ai.SwapNames
import com.substichoose.ai.SwapReason
import com.substichoose.model.SubstituteOption
import com.substichoose.safety.AllergyProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AiSuggestions {
    /** Built-in swaps cover this ingredient, so Gemma only runs if the user asks. */
    data object NotRequested : AiSuggestions
    data object Loading : AiSuggestions
    data class Loaded(val options: List<SubstituteOption>) : AiSuggestions
    data object Unavailable : AiSuggestions
}

/**
 * Substitutes for one ingredient. Built-in (curated) swaps are preferred: they show immediately and,
 * when there are any, Gemma is only asked on request. Without them Gemma runs straight away.
 * Both lists come from SubstituteService, which already runs them through the allergen filter.
 */
class SubstitutesViewModel(
    private val container: AppContainer,
    val ingredientName: String,
    private val recipeTitle: String,
    val reason: SwapReason,
    private val profile: AllergyProfile,
) : ViewModel() {

    val curated: List<SubstituteOption> =
        runCatching { container.substitutes.curated(ingredientName, profile) }.getOrDefault(emptyList())

    private val _ai = MutableStateFlow<AiSuggestions>(
        if (curated.isEmpty()) AiSuggestions.Loading else AiSuggestions.NotRequested
    )
    val ai: StateFlow<AiSuggestions> = _ai.asStateFlow()

    init {
        if (curated.isEmpty()) loadAi()
    }

    fun loadAi() {
        _ai.value = AiSuggestions.Loading
        viewModelScope.launch {
            val options = try {
                container.substitutes.fromAi(ingredientName, recipeTitle, reason, profile, alreadyShown = curated.map { it.name })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _ai.value = if (options == null) {
                AiSuggestions.Unavailable
            } else {
                // Built-in swaps win: drop Gemma ideas that repeat one under another name.
                AiSuggestions.Loaded(options.filterNot { ai -> curated.any { SwapNames.isSameSwap(ai.name, it.name) } })
            }
        }
    }
}
