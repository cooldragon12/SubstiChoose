package com.substichoose.ui.recipe

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.substichoose.AppContainer
import com.substichoose.data.NutritionEntity
import com.substichoose.model.AdaptedStep
import com.substichoose.ui.appViewModel
import com.substichoose.ui.components.BackHeader
import com.substichoose.ui.components.Badge
import com.substichoose.ui.components.Disclaimer
import com.substichoose.ui.components.SectionLabel
import com.substichoose.ui.theme.SC
import kotlin.math.roundToInt

@Composable
fun RecipeScreen(
    container: AppContainer,
    recipeId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
) {
    val vm = appViewModel(key = "recipe-$recipeId") { RecipeViewModel(container, recipeId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val adapt by vm.adapt.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showOriginal by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(SC.Background)) {
        Box(Modifier.fillMaxWidth().background(SC.Background).statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            BackHeader(caption = "Recipe", onBack = onBack) {
                if (state.exists && state.loaded) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit ingredients", tint = SC.Ink)
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete recipe", tint = SC.Ink)
                    }
                }
            }
        }

        when {
            !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SC.Accent)
            }
            !state.exists -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This recipe was deleted.", style = MaterialTheme.typography.bodyLarge, color = SC.Muted)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = SC.Ink,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                item { SafetySummary(state.unsafeLabels, state.swapCount) }

                item { SectionLabel("Ingredients", Modifier.padding(start = 4.dp, top = 8.dp)) }
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SC.Card).padding(vertical = 4.dp),
                    ) {
                        state.ingredients.forEachIndexed { index, ing ->
                            IngredientLine(ing)
                            if (index < state.ingredients.lastIndex) {
                                Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(SC.Divider))
                            }
                        }
                        if (state.ingredients.isEmpty()) {
                            Text("No ingredients.", color = SC.Muted, modifier = Modifier.padding(14.dp))
                        }
                    }
                }
                if (state.ingredients.any { it.nutrition != null }) {
                    item {
                        Text(
                            "Nutrition per 100 g from USDA FoodData Central, loaded when you're online.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SC.Muted,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                item {
                    Row(Modifier.padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Steps", Modifier.weight(1f))
                        if (state.adapted != null) {
                            TextButton(onClick = { showOriginal = !showOriginal }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(
                                    if (showOriginal) "Show adapted" else "Show original",
                                    color = SC.Accent,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                    }
                }

                val adapted = state.adapted
                val steps: List<AdaptedStep> =
                    if (adapted != null && !showOriginal) adapted.steps
                    else state.steps.map { AdaptedStep(it, changed = false) }
                if (steps.isEmpty()) {
                    item {
                        Text(
                            "No steps were found in the photo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SC.Muted,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                } else {
                    itemsIndexed(steps) { index, step -> StepCard(index + 1, step) }
                }

                if (state.steps.isNotEmpty() && state.swapCount > 0) {
                    item {
                        AdaptButton(
                            working = adapt is AdaptState.Working,
                            again = state.adapted != null,
                            onClick = { showOriginal = false; vm.adaptSteps() },
                        )
                    }
                }
                (adapt as? AdaptState.Error)?.let { err ->
                    item {
                        Text(
                            err.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SC.AllergenText,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                item { Disclaimer(Modifier.padding(top = 8.dp)) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this recipe?") },
            text = { Text("\"${state.title}\" and its swaps will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete()
                    onDeleted()
                }) { Text("Delete", color = SC.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = SC.Ink) }
            },
            containerColor = SC.Card,
        )
    }
}

@Composable
private fun SafetySummary(unsafeLabels: List<String>, swapCount: Int) {
    val unsafe = unsafeLabels.isNotEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (unsafe) SC.AllergenBg else if (swapCount > 0) SC.SwapBg else SC.SafeBg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (unsafe) SC.AllergenText else if (swapCount > 0) SC.SwapText else SC.SafeText
        Icon(if (unsafe) Icons.Filled.Warning else Icons.Filled.Check, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                when {
                    unsafe -> "Still contains ${unsafeLabels.joinToString(", ")}"
                    swapCount > 0 -> "Adapted for you · $swapCount swapped"
                    else -> "Safe for you"
                },
                style = MaterialTheme.typography.titleSmall,
                color = tint,
            )
            if (unsafe) {
                Text("Tap Edit to choose a swap.", style = MaterialTheme.typography.bodySmall, color = SC.Ink)
            }
        }
    }
}

@Composable
private fun IngredientLine(ing: RecipeIngredientUi) {
    // Three levels: the ingredient (titleMedium), its details (bodyMedium, muted) and nutrition (bodySmall, faint).
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val sub = ing.substitute
        if (sub != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "replaced with", tint = SC.SwapText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(sub.name, style = MaterialTheme.typography.titleMedium, color = SC.SwapText)
            }
            Text(
                ing.line,
                style = MaterialTheme.typography.bodyMedium,
                color = SC.Muted,
                textDecoration = TextDecoration.LineThrough,
            )
            if (sub.ratio.isNotBlank()) {
                Text(sub.ratio, style = MaterialTheme.typography.bodyMedium, color = SC.Muted)
            }
        } else {
            Text(
                ing.line,
                style = MaterialTheme.typography.titleMedium,
                color = if (ing.stillUnsafe) SC.Danger else SC.Ink,
            )
            if (ing.stillUnsafe) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = SC.AllergenText, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Contains ${ing.conflictLabels.joinToString(", ")} · no swap chosen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SC.AllergenText,
                    )
                }
            } else if (ing.missing) {
                Text("You marked this as missing · no swap chosen", style = MaterialTheme.typography.bodyMedium, color = SC.Muted)
            }
        }
        // Fades in whenever the background lookup finishes; nothing shows offline or before then.
        AnimatedVisibility(visible = ing.nutrition != null, enter = fadeIn() + expandVertically()) {
            ing.nutrition?.let { NutritionLine(it) }
        }
    }
}

/** Quiet supporting line: lighter and smaller than everything above it. */
@Composable
private fun NutritionLine(n: NutritionEntity) {
    val parts = listOfNotNull(
        n.kcal?.let { "${it.roundToInt()} kcal" },
        n.proteinG?.let { "Protein ${grams(it)}" },
        n.carbsG?.let { "Carbs ${grams(it)}" },
        n.fatG?.let { "Fat ${grams(it)}" },
    )
    Text(
        parts.joinToString("  ·  ") + "  per 100 g",
        style = MaterialTheme.typography.bodySmall,
        color = SC.Muted.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** 0.9 g, 12.6 g, 81 g. */
private fun grams(v: Double): String =
    if (v >= 10) "${v.roundToInt()} g" else "${(v * 10).roundToInt() / 10.0} g".replace(".0 g", " g")

@Composable
private fun StepCard(number: Int, step: AdaptedStep) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(18.dp))
            .background(SC.Card),
    ) {
        if (step.changed) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(SC.Accent))
        }
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (step.changed) SC.SafeBg else SC.Background),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", style = MaterialTheme.typography.labelMedium, color = if (step.changed) SC.SafeText else SC.Ink)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(step.text, style = MaterialTheme.typography.bodyLarge, color = SC.Ink)
                if (step.changed) Badge("Changed for your swap", SC.SafeBg, SC.SafeText)
            }
        }
    }
}

@Composable
private fun AdaptButton(working: Boolean, again: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !working,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SC.Accent,
            contentColor = Color.White,
            disabledContainerColor = SC.SafeBg,
            disabledContentColor = SC.SafeText,
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (working) {
            CircularProgressIndicator(color = SC.SafeText, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Gemma is rewriting the steps…", style = MaterialTheme.typography.titleSmall)
        } else {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (again) "Adapt steps again with Gemma" else "Adapt steps with Gemma", style = MaterialTheme.typography.titleSmall)
        }
    }
}
