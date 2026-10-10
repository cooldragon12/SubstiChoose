package com.substichoose.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.substichoose.AppContainer
import com.substichoose.ai.ModelDownloadState
import com.substichoose.ai.ModelState
import com.substichoose.data.RecipeWithIngredients
import com.substichoose.safety.AllergyProfile
import com.substichoose.ui.checkText
import com.substichoose.ui.profileSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BadgeKind { ALLERGENS, SWAPPED, SAFE }

data class RecipeCardUi(
    val id: Long,
    val title: String,
    val subtitle: String,
    val badge: BadgeKind,
    val badgeCount: Int,
)

data class HomeUiState(
    val loaded: Boolean = false,
    val totalCount: Int = 0,
    val cards: List<RecipeCardUi> = emptyList(),
    val allergySummary: String = "",
    val hasAllergies: Boolean = false,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val modelState: StateFlow<ModelState> = container.gemma.state
    val downloadState: StateFlow<ModelDownloadState> = container.modelDownloader.state

    fun downloadModel(allowMobileData: Boolean) = container.modelDownloader.start(allowMobileData)

    val state: StateFlow<HomeUiState> = combine(
        container.recipes.observeRecipes(),
        container.settings.profile,
        _query,
    ) { recipes, profile, query ->
        HomeUiState(
            loaded = true,
            totalCount = recipes.size,
            cards = recipes.filter { matches(it, query) }.map { toCard(it, profile) },
            allergySummary = container.checker.profileSummary(profile),
            hasAllergies = profile.allergenIds.isNotEmpty() || profile.customTerms.isNotEmpty(),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(text: String) {
        _query.value = text
    }

    private fun matches(r: RecipeWithIngredients, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return r.recipe.title.contains(q, ignoreCase = true) ||
            r.ingredients.any { it.name.contains(q, ignoreCase = true) || it.raw.contains(q, ignoreCase = true) }
    }

    private fun toCard(r: RecipeWithIngredients, profile: AllergyProfile): RecipeCardUi {
        val ingredients = r.ingredients
        val swapped = ingredients.count { r.chosenSubstitute(it) != null }
        val flagged = ingredients.count { ing ->
            r.chosenSubstitute(ing) == null && container.checker.conflicts(ing.checkText(), profile).isNotEmpty()
        }
        val (badge, count) = when {
            flagged > 0 -> BadgeKind.ALLERGENS to flagged
            swapped > 0 -> BadgeKind.SWAPPED to swapped
            else -> BadgeKind.SAFE to 0
        }
        val n = ingredients.size
        val date = dateFormat.format(Date(r.recipe.createdAt))
        return RecipeCardUi(
            id = r.recipe.id,
            title = r.recipe.title.ifBlank { "Untitled recipe" },
            subtitle = "$n ${if (n == 1) "ingredient" else "ingredients"} · $date",
            badge = badge,
            badgeCount = count,
        )
    }
}
