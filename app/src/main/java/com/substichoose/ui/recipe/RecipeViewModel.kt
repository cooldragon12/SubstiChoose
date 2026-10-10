package com.substichoose.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.substichoose.AppContainer
import com.substichoose.data.NutritionEntity
import com.substichoose.nutrition.NutritionRepository
import com.substichoose.model.AdaptedSteps
import com.substichoose.model.ModelJson
import com.substichoose.model.SubstituteOption
import com.substichoose.ui.checkText
import com.substichoose.ui.displayLine
import com.substichoose.ui.shortLabelFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipeIngredientUi(
    val id: Long,
    /** Name used as the key when asking Gemma to adapt steps. */
    val name: String,
    val line: String,
    val substitute: SubstituteOption?,
    val conflictLabels: List<String>,
    val missing: Boolean,
    /** Per 100 g of what is actually used (the swap if chosen); null until fetched, or offline. */
    val nutrition: NutritionEntity? = null,
) {
    val stillUnsafe: Boolean get() = substitute == null && conflictLabels.isNotEmpty()
}

data class RecipeUiState(
    val loaded: Boolean = false,
    val exists: Boolean = true,
    val title: String = "",
    val ingredients: List<RecipeIngredientUi> = emptyList(),
    val steps: List<String> = emptyList(),
    val adapted: AdaptedSteps? = null,
) {
    val swapCount: Int get() = ingredients.count { it.substitute != null }
    val unsafeLabels: List<String> get() = ingredients.filter { it.stillUnsafe }.flatMap { it.conflictLabels }.distinct()
}

sealed interface AdaptState {
    data object Idle : AdaptState
    data object Working : AdaptState
    data class Error(val message: String) : AdaptState
}

class RecipeViewModel(
    private val container: AppContainer,
    private val recipeId: Long,
) : ViewModel() {

    val state: StateFlow<RecipeUiState> = combine(
        container.recipes.observeRecipe(recipeId),
        container.settings.profile,
        container.nutrition.all,
    ) { recipe, profile, nutrition ->
        if (recipe == null) {
            RecipeUiState(loaded = true, exists = false)
        } else {
            val ingredients = recipe.sortedIngredients.map { ing ->
                val substitute = recipe.chosenSubstitute(ing)
                RecipeIngredientUi(
                    id = ing.id,
                    name = ing.name.ifBlank { ing.raw },
                    line = ing.displayLine(),
                    substitute = substitute,
                    conflictLabels = container.checker.conflicts(ing.checkText(), profile).map { container.checker.shortLabelFor(it) },
                    missing = ing.isMissing,
                    nutrition = nutrition[NutritionRepository.key(nutritionName(ing.name.ifBlank { ing.raw }, substitute))]
                        ?.takeIf { it.found && it.kcal != null },
                )
            }
            val adapted = recipe.recipe.adaptedStepsJson?.let { json ->
                runCatching { ModelJson.json.decodeFromString(AdaptedSteps.serializer(), json) }.getOrNull()
            }?.takeIf { it.steps.isNotEmpty() }
            RecipeUiState(
                loaded = true,
                exists = true,
                title = recipe.recipe.title.ifBlank { "Untitled recipe" },
                ingredients = ingredients,
                steps = recipe.steps,
                adapted = adapted,
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeUiState())

    init {
        // Ask for nutrition of anything new in this recipe; it fills in quietly when online.
        viewModelScope.launch {
            container.recipes.observeRecipe(recipeId)
                .map { r -> r?.sortedIngredients?.map { nutritionName(it.name.ifBlank { it.raw }, r.chosenSubstitute(it)) }.orEmpty() }
                .distinctUntilChanged()
                .collect { names -> container.nutrition.prefetch(names) }
        }
    }

    private val _adapt = MutableStateFlow<AdaptState>(AdaptState.Idle)
    val adapt: StateFlow<AdaptState> = _adapt.asStateFlow()

    fun adaptSteps() {
        val s = state.value
        if (_adapt.value is AdaptState.Working || s.steps.isEmpty()) return
        val swaps = s.ingredients
            .mapNotNull { ing -> ing.substitute?.let { ing.name to it.name } }
            .toMap()
        if (swaps.isEmpty()) {
            _adapt.value = AdaptState.Error("Choose a swap first, then Gemma can rewrite the steps.")
            return
        }
        _adapt.value = AdaptState.Working
        viewModelScope.launch {
            val result = try {
                container.gemma.adaptSteps(s.steps, swaps)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (result == null || result.steps.isEmpty()) {
                _adapt.value = AdaptState.Error("Gemma couldn't adapt the steps right now.")
            } else {
                container.recipes.saveAdaptedSteps(recipeId, result)
                _adapt.value = AdaptState.Idle
            }
        }
    }

    fun dismissAdaptError() {
        if (_adapt.value is AdaptState.Error) _adapt.value = AdaptState.Idle
    }

    /** Deletes on the app scope so it finishes even though the screen is leaving. */
    fun delete() {
        container.appScope.launch { container.recipes.deleteRecipe(recipeId) }
    }
}

/** Nutrition describes what actually goes in: the chosen swap, else the ingredient. */
private fun nutritionName(ingredient: String, substitute: SubstituteOption?): String = substitute?.name ?: ingredient
