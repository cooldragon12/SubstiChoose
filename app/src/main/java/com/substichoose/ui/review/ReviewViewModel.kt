package com.substichoose.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.substichoose.AppContainer
import com.substichoose.data.IngredientEntity
import com.substichoose.model.SubstituteOption
import com.substichoose.safety.AllergyProfile
import com.substichoose.ui.checkText
import com.substichoose.ui.shortLabelFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class IngredientRowUi(
    val ingredient: IngredientEntity,
    /** Allergen ids / custom terms from the user's profile that this row hits. */
    val conflicts: List<String>,
    /** Short labels for [conflicts], e.g. "Egg", "Dairy", "Coconut". */
    val conflictLabels: List<String>,
    val substitute: SubstituteOption?,
) {
    val needsSwap: Boolean get() = substitute == null && (conflicts.isNotEmpty() || ingredient.isMissing)
}

data class ReviewUiState(
    val loaded: Boolean = false,
    val exists: Boolean = true,
    val title: String = "",
    val rows: List<IngredientRowUi> = emptyList(),
    val profile: AllergyProfile = AllergyProfile(emptySet()),
) {
    val needSwapCount: Int get() = rows.count { it.needsSwap }
}

class ReviewViewModel(
    private val container: AppContainer,
    private val recipeId: Long,
) : ViewModel() {

    private val repo = container.recipes

    /** What the title field shows; null until the recipe first loads. */
    private val _titleDraft = MutableStateFlow<String?>(null)
    val titleDraft: StateFlow<String?> = _titleDraft.asStateFlow()
    private var titleJob: Job? = null
    /** Typed but not yet written to the database. */
    @Volatile private var pendingTitle: String? = null

    val state: StateFlow<ReviewUiState> = combine(
        repo.observeRecipe(recipeId),
        container.settings.profile,
    ) { recipe, profile ->
        if (recipe == null) {
            ReviewUiState(loaded = true, exists = false, profile = profile)
        } else {
            val rows = recipe.sortedIngredients.map { ing ->
                val hits = container.checker.conflicts(ing.checkText(), profile).toList()
                IngredientRowUi(
                    ingredient = ing,
                    conflicts = hits,
                    conflictLabels = hits.map { container.checker.shortLabelFor(it) },
                    substitute = recipe.chosenSubstitute(ing),
                )
            }
            ReviewUiState(loaded = true, exists = true, title = recipe.recipe.title, rows = rows, profile = profile)
        }
    }
        .flowOn(Dispatchers.Default)
        .onEach { s -> if (_titleDraft.value == null && s.loaded && s.exists) _titleDraft.value = s.title }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    fun onTitleChange(text: String) {
        _titleDraft.value = text
        pendingTitle = text
        titleJob?.cancel()
        titleJob = viewModelScope.launch {
            delay(600)
            saveTitle(text)
        }
    }

    /** Saves the title right away (used by Done). */
    fun flushTitle(then: () -> Unit) {
        titleJob?.cancel()
        val text = _titleDraft.value
        viewModelScope.launch {
            if (text != null) saveTitle(text)
            then()
        }
    }

    override fun onCleared() {
        // Left with Back before the debounce fired: keep what the user typed.
        // (viewModelScope is already cancelled here, so write on the app scope.)
        val text = pendingTitle?.trim()
        if (!text.isNullOrEmpty()) {
            container.appScope.launch { repo.updateTitle(recipeId, text) }
        }
        super.onCleared()
    }

    private suspend fun saveTitle(text: String) {
        pendingTitle = null
        val clean = text.trim()
        if (clean.isNotEmpty() && clean != state.value.title) repo.updateTitle(recipeId, clean)
    }

    /** Turning "Don't have it" off drops a swap that was only there because the item was missing. */
    fun setMissing(row: IngredientRowUi, missing: Boolean) {
        val ing = row.ingredient
        val keepSwap = missing || row.conflicts.isNotEmpty()
        viewModelScope.launch {
            repo.updateIngredient(
                ing.copy(isMissing = missing, chosenSubstituteJson = ing.chosenSubstituteJson.takeIf { keepSwap })
            )
        }
    }

    /** Saves the edit-mode text boxes: only rows whose text changed, and never a blank one. */
    fun saveEdits(rows: List<IngredientRowUi>, drafts: Map<Long, String>) {
        val changed = rows.mapNotNull { row ->
            val text = drafts[row.ingredient.id]?.trim()
            if (text.isNullOrEmpty() || text == row.ingredient.raw.trim()) null else row.ingredient to text
        }
        if (changed.isEmpty()) return
        // App scope: the writes must survive the screen leaving (Done saves, then navigates).
        container.appScope.launch { changed.forEach { (ing, text) -> repo.editIngredientText(ing, text) } }
    }

    fun delete(ingredient: IngredientEntity) {
        viewModelScope.launch { repo.deleteIngredient(ingredient.id) }
    }

    /** Undo for [delete]. */
    fun restore(ingredient: IngredientEntity) {
        viewModelScope.launch { repo.restoreIngredient(ingredient) }
    }

    fun add(raw: String) {
        val clean = raw.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch { repo.addIngredient(recipeId, clean) }
    }

    fun chooseSubstitute(ingredientId: Long, option: SubstituteOption?) {
        // App scope: the write must survive the sheet closing and the screen leaving.
        container.appScope.launch { repo.chooseSubstitute(ingredientId, option) }
    }
}
