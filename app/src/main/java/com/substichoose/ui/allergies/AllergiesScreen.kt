package com.substichoose.ui.allergies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.substichoose.AppContainer
import com.substichoose.ui.appViewModel
import com.substichoose.ui.components.BackHeader
import com.substichoose.ui.components.BottomActionBar
import com.substichoose.ui.components.PrimaryButton
import com.substichoose.ui.components.SectionLabel
import com.substichoose.ui.theme.SC

/**
 * My Allergies. Used for onboarding ([onBack] == null) and from the Home allergy chip.
 */
@Composable
fun AllergiesScreen(
    container: AppContainer,
    onboarding: Boolean,
    onBack: (() -> Unit)?,
    onSaved: () -> Unit,
) {
    val vm = appViewModel { AllergiesViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(SC.Background)
            .imePadding()
    ) {
        // Header
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onBack != null) {
                BackHeader(caption = "Preferences", onBack = onBack)
            } else {
                Text(
                    "Welcome to SubstiChoose",
                    style = MaterialTheme.typography.titleSmall,
                    color = SC.Muted,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                )
            }
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("My Allergies", style = MaterialTheme.typography.headlineLarge, color = SC.Ink)
                Text(
                    if (onboarding) "Pick what you avoid. We flag these in every recipe and never suggest them as substitutes."
                    else "We flag these in every recipe and never suggest them as substitutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SC.Muted,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionLabel("Common allergens", Modifier.padding(start = 4.dp, top = 8.dp)) }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SC.Card)) {
                    vm.common.forEachIndexed { index, allergen ->
                        CheckRow(
                            label = allergen.label,
                            hint = allergen.hint,
                            checked = allergen.id in state.selectedIds,
                            onToggle = { vm.toggle(allergen.id) },
                        )
                        if (index < vm.common.lastIndex) RowDivider()
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel("Your own", Modifier.weight(1f))
                    IconButton(
                        onClick = { vm.openAdd() },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = SC.Accent, contentColor = Color.White),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add an ingredient to avoid")
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SC.Card)) {
                    if (state.adding) {
                        AddCustomRow(
                            draft = state.draft,
                            onDraftChange = vm::setDraft,
                            onAdd = vm::addDraft,
                        )
                        if (state.custom.isNotEmpty()) RowDivider()
                    }
                    state.custom.forEachIndexed { index, item ->
                        CustomRow(
                            item = item,
                            onToggle = { vm.toggleCustom(item.label) },
                            onRemove = { vm.removeCustom(item.label) },
                        )
                        if (index < state.custom.lastIndex) RowDivider()
                    }
                    if (state.custom.isEmpty() && !state.adding) {
                        Text(
                            "Tap + to add anything else you avoid, like a fruit, spice or additive.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SC.Muted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        )
                    }
                }
            }
        }

        BottomActionBar(Modifier.navigationBarsPadding()) {
            PrimaryButton(
                text = "Save · ${state.count} selected",
                enabled = state.loaded && !state.saving,
                onClick = { vm.save(onSaved) },
            )
        }
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SC.Divider))
}

@Composable
private fun CheckRow(label: String, hint: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = SC.Ink)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = SC.Muted)
        }
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = SC.Accent, uncheckedColor = SC.Muted),
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun CustomRow(item: CustomItem, onToggle: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 60.dp)
                .toggleable(value = item.checked, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(item.label, style = MaterialTheme.typography.titleMedium, color = SC.Ink, modifier = Modifier.weight(1f))
            Checkbox(
                checked = item.checked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = SC.Accent, uncheckedColor = SC.Muted),
                modifier = Modifier.padding(12.dp),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove ${item.label}", tint = SC.Muted)
        }
    }
}

@Composable
private fun AddCustomRow(draft: String, onDraftChange: (String) -> Unit, onAdd: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text("e.g. Coconut, mango, MSG") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SC.Accent,
                unfocusedBorderColor = SC.BorderStrong,
                cursorColor = SC.Accent,
            ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        Spacer(Modifier.size(10.dp))
        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SC.Accent, contentColor = Color.White),
            modifier = Modifier.height(48.dp),
        ) { Text("Add", style = MaterialTheme.typography.titleSmall) }
    }
}
