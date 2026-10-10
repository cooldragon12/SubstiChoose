package com.substichoose.ui.allergies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.substichoose.AppContainer
import com.substichoose.safety.AllergyProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommonAllergen(val id: String, val label: String, val hint: String)

data class CustomItem(val label: String, val checked: Boolean = true)

data class AllergiesUiState(
    val loaded: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val custom: List<CustomItem> = emptyList(),
    val adding: Boolean = false,
    val draft: String = "",
    val saving: Boolean = false,
) {
    val count: Int get() = selectedIds.size + custom.count { it.checked }
}

class AllergiesViewModel(private val container: AppContainer) : ViewModel() {

    /** The 9 fixed allergen ids, with hints from the approved mockup. */
    val common: List<CommonAllergen> = listOf(
        "peanut" to "peanut butter, peanut oil",
        "treenuts" to "almond, cashew, walnut",
        "dairy" to "butter, cheese, cream",
        "egg" to "mayonnaise, meringue",
        "gluten" to "flour, soy sauce, breadcrumbs",
        "soy" to "tofu, soy sauce, miso",
        "fish" to "patis (fish sauce), tuna",
        "shellfish" to "shrimp, bagoong, crab",
        "sesame" to "sesame oil, tahini",
    ).map { (id, hint) -> CommonAllergen(id, container.checker.labels[id] ?: FALLBACK_LABELS.getValue(id), hint) }

    private val _state = MutableStateFlow(AllergiesUiState())
    val state: StateFlow<AllergiesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = runCatching { container.settings.profile.first() }
                .getOrDefault(AllergyProfile(emptySet()))
            _state.update {
                it.copy(
                    loaded = true,
                    selectedIds = profile.allergenIds,
                    custom = profile.customTerms.map { term -> CustomItem(term, true) },
                )
            }
        }
    }

    fun toggle(id: String) = _state.update {
        it.copy(selectedIds = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id)
    }

    fun toggleCustom(label: String) = _state.update { s ->
        s.copy(custom = s.custom.map { if (it.label == label) it.copy(checked = !it.checked) else it })
    }

    fun removeCustom(label: String) = _state.update { s -> s.copy(custom = s.custom.filterNot { it.label == label }) }

    fun openAdd() = _state.update { it.copy(adding = true) }

    fun setDraft(text: String) = _state.update { it.copy(draft = text) }

    fun cancelAdd() = _state.update { it.copy(adding = false, draft = "") }

    fun addDraft() = _state.update { s ->
        val label = s.draft.trim()
        when {
            label.isEmpty() -> s.copy(adding = false, draft = "")
            s.custom.any { it.label.equals(label, ignoreCase = true) } ->
                s.copy(
                    custom = s.custom.map { if (it.label.equals(label, ignoreCase = true)) it.copy(checked = true) else it },
                    adding = false,
                    draft = "",
                )
            else -> s.copy(custom = listOf(CustomItem(label, true)) + s.custom, adding = false, draft = "")
        }
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.saving) return
        // Anything still typed in the field counts too.
        val pending = s.draft.trim().takeIf { s.adding && it.isNotEmpty() }
        val terms = (s.custom.filter { it.checked }.map { it.label } + listOfNotNull(pending)).toSet()
        val profile = AllergyProfile(allergenIds = s.selectedIds, customTerms = terms)
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                container.settings.saveProfile(profile)
            } finally {
                _state.update { it.copy(saving = false) }
            }
            onDone()
        }
    }

    private companion object {
        val FALLBACK_LABELS = mapOf(
            "peanut" to "Peanut", "treenuts" to "Tree nuts", "dairy" to "Milk / dairy",
            "egg" to "Egg", "gluten" to "Wheat / gluten", "soy" to "Soy", "fish" to "Fish",
            "shellfish" to "Shellfish", "sesame" to "Sesame",
        )
    }
}
