package com.substichoose.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.substichoose.data.IngredientEntity
import com.substichoose.safety.AllergenChecker
import com.substichoose.safety.AllergyProfile

/** viewModel() with a one-line factory, so screens can pass the AppContainer to their ViewModel. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: () -> VM): VM =
    viewModel(key = key, factory = viewModelFactory { initializer { create() } })

/** The text we run the allergen check on for one ingredient row. */
fun IngredientEntity.checkText(): String = raw.ifBlank { name }

/** "1 1/2 cup flour" from the parsed parts, falling back to the raw line. */
fun IngredientEntity.displayLine(): String {
    // Gemma sometimes puts the name in the unit ("ripe bananas"); don't repeat it.
    val n = name.trim()
    val u = unit.trim()
    val words = if (n.isNotEmpty() && u.contains(n, ignoreCase = true)) listOf(qty.trim(), u) else listOf(qty.trim(), u, n)
    val parts = words.filter { it.isNotEmpty() }
    return if (parts.isEmpty()) raw else parts.joinToString(" ")
}

/** Full label for an allergen id ("dairy" -> "Milk / dairy"); custom terms come back as typed. */
fun AllergenChecker.labelFor(idOrTerm: String): String = labels[idOrTerm] ?: idOrTerm

/** Short label for chips: "Milk / dairy" -> "Dairy", "Wheat / gluten" -> "Gluten". */
fun AllergenChecker.shortLabelFor(idOrTerm: String): String {
    val full = labelFor(idOrTerm)
    val short = full.substringAfterLast('/').trim().ifEmpty { full }
    return short.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

/** "Egg · Dairy", "Egg · Dairy +2", or "Set allergies". */
fun AllergenChecker.profileSummary(profile: AllergyProfile, maxShown: Int = 2): String {
    val all = profile.allergenIds.map { shortLabelFor(it) } + profile.customTerms.map { shortLabelFor(it) }
    if (all.isEmpty()) return "Set allergies"
    val shown = all.take(maxShown).joinToString(" · ")
    return if (all.size > maxShown) "$shown +${all.size - maxShown}" else shown
}

fun plural(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"
