package com.substichoose.ui.substitutes

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.substichoose.ai.SwapReason
import com.substichoose.model.SubstituteOption
import com.substichoose.ui.components.AllergenBadge
import com.substichoose.ui.components.Badge
import com.substichoose.ui.components.Disclaimer
import com.substichoose.ui.components.SectionLabel
import com.substichoose.ui.theme.SC

/**
 * Bottom sheet listing safe substitutes for one ingredient.
 * The ViewModel is created by the caller (outside the sheet's window) and passed in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubstitutesSheet(
    vm: SubstitutesViewModel,
    conflictLabels: List<String>,
    current: SubstituteOption?,
    sheetState: SheetState,
    onSelect: (SubstituteOption) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ai by vm.ai.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SC.Background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SheetHeader(vm.ingredientName, vm.reason, conflictLabels) }

            item { SectionLabel("Quick swaps", Modifier.padding(start = 4.dp, top = 8.dp)) }
            if (vm.curated.isEmpty()) {
                item { InfoText("No hand-checked swaps for this one. See Gemma's ideas below.") }
            } else {
                items(vm.curated) { option ->
                    OptionCard(option, isAi = false, selected = isSame(option, current), onSelect = { onSelect(option) })
                }
            }

            item {
                Row(
                    Modifier.padding(start = 4.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = SC.Muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    SectionLabel("Gemma suggests")
                }
            }
            when (val s = ai) {
                AiSuggestions.NotRequested -> item {
                    Column {
                        InfoText("The swaps above are hand-checked. Gemma can suggest more, but its ideas may be less reliable.")
                        TextButton(onClick = { vm.loadAi() }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Ask Gemma for more ideas", color = SC.Accent, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                AiSuggestions.Loading -> {
                    item { ThinkingRow() }
                    item { PlaceholderCard() }
                    item { PlaceholderCard() }
                }
                AiSuggestions.Unavailable -> item {
                    Column {
                        InfoText("Gemma isn't available right now.")
                        TextButton(onClick = { vm.loadAi() }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Try again", color = SC.Accent, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                is AiSuggestions.Loaded -> {
                    if (s.options.isEmpty()) {
                        item { InfoText(
                                if (vm.curated.isEmpty()) "Gemma had no safe ideas for this one."
                                else "Gemma had no new ideas beyond the swaps above."
                            ) }
                    } else {
                        items(s.options) { option ->
                            OptionCard(option, isAi = true, selected = isSame(option, current), onSelect = { onSelect(option) })
                        }
                    }
                }
            }

            if (current != null) {
                item {
                    OutlinedButton(
                        onClick = onClear,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, SC.BorderStrong),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = SC.Ink, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Clear swap (use ${vm.ingredientName})", color = SC.Ink, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            item { Disclaimer(Modifier.padding(top = 4.dp)) }
        }
    }
}

private fun isSame(a: SubstituteOption, b: SubstituteOption?): Boolean =
    b != null && a.name.equals(b.name, ignoreCase = true) && a.ratio == b.ratio

@Composable
private fun SheetHeader(name: String, reason: SwapReason, conflictLabels: List<String>) {
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Swap for", style = MaterialTheme.typography.titleSmall, color = SC.Muted)
        Text(name.replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.headlineSmall, color = SC.Ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (reason == SwapReason.ALLERGY) {
                Text("Contains", style = MaterialTheme.typography.bodyMedium, color = SC.Muted)
                if (conflictLabels.isNotEmpty()) AllergenBadge(conflictLabels.joinToString(" · "))
            } else {
                Badge("You don't have it", SC.SwapBg, SC.SwapText)
            }
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = SC.Muted,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SC.Card)
            .padding(14.dp),
    )
}

@Composable
private fun OptionCard(option: SubstituteOption, isAi: Boolean, selected: Boolean, onSelect: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SC.Card)
            .then(if (selected) Modifier.border(2.dp, SC.Accent, RoundedCornerShape(18.dp)) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(option.name, style = MaterialTheme.typography.titleMedium, color = SC.Ink, modifier = Modifier.weight(1f))
            if (isAi) Badge("Gemma", SC.SwapBg, SC.SwapText) else Badge("Checked", SC.SafeBg, SC.SafeText)
        }
        if (option.ratio.isNotBlank()) {
            Text(option.ratio, style = MaterialTheme.typography.bodyMedium, color = SC.SwapText)
        }
        if (option.effect.isNotBlank()) {
            Text(option.effect, style = MaterialTheme.typography.bodyMedium, color = SC.Ink)
        }
        if (option.note.isNotBlank()) {
            Text(option.note, style = MaterialTheme.typography.bodySmall, color = SC.Muted)
        }
        Spacer(Modifier.height(2.dp))
        Button(
            onClick = onSelect,
            enabled = !selected,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SC.Accent,
                contentColor = Color.White,
                disabledContainerColor = SC.SafeBg,
                disabledContentColor = SC.SafeText,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Selected", style = MaterialTheme.typography.titleSmall)
            } else {
                Text("Select", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun ThinkingRow() {
    Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = SC.Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text("Gemma is thinking on your phone…", style = MaterialTheme.typography.bodyMedium, color = SC.Muted)
    }
}

@Composable
private fun PlaceholderCard() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val a by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "shimmerAlpha",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SC.Card)
            .padding(14.dp)
            .alpha(a),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Bar(0.5f, 18)
        Bar(0.8f, 12)
        Bar(0.65f, 12)
    }
}

@Composable
private fun Bar(fraction: Float, heightDp: Int) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(heightDp.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SC.Divider)
    )
}
