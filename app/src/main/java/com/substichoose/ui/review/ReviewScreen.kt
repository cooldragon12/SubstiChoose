package com.substichoose.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.substichoose.AppContainer
import com.substichoose.ai.SwapReason
import com.substichoose.data.IngredientEntity
import com.substichoose.ui.appViewModel
import com.substichoose.ui.components.BackHeader
import com.substichoose.ui.components.BottomActionBar
import com.substichoose.ui.components.PrimaryButton
import com.substichoose.ui.components.SectionLabel
import com.substichoose.ui.displayLine
import com.substichoose.ui.plural
import com.substichoose.ui.substitutes.SubstitutesSheet
import com.substichoose.ui.substitutes.SubstitutesViewModel
import com.substichoose.ui.theme.SC
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    container: AppContainer,
    recipeId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val vm = appViewModel(key = "review-$recipeId") { ReviewViewModel(container, recipeId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val titleDraft by vm.titleDraft.collectAsStateWithLifecycle()

    var sheetFor by rememberSaveable { mutableStateOf<Long?>(null) }

    // Edit mode: every ingredient becomes a text box; the header button turns into Save.
    var editMode by rememberSaveable { mutableStateOf(false) }
    val drafts = remember { mutableStateMapOf<Long, String>() }
    val startEdit = {
        drafts.clear()
        state.rows.forEach { drafts[it.ingredient.id] = it.ingredient.raw.ifBlank { it.ingredient.displayLine() } }
        editMode = true
    }
    val saveEdits = {
        vm.saveEdits(state.rows, drafts.toMap())
        editMode = false
    }
    // Back while editing cancels the edits instead of leaving the screen.
    BackHandler(enabled = editMode) { editMode = false }

    val snackbar = remember { SnackbarHostState() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val closeSheet: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { sheetFor = null }
    }
    val deleteWithUndo: (IngredientEntity) -> Unit = { ing ->
        vm.delete(ing)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Deleted ${ing.name}", actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) vm.restore(ing)
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = SC.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Box(Modifier.fillMaxWidth().background(SC.Background).statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                BackHeader(caption = "Review ingredients", onBack = { if (editMode) editMode = false else onBack() }) {
                    if (state.loaded && state.exists && state.rows.isNotEmpty()) {
                        TextButton(onClick = { if (editMode) saveEdits() else startEdit() }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(
                                if (editMode) Icons.Filled.Check else Icons.Filled.Edit,
                                contentDescription = null,
                                tint = SC.Accent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (editMode) "Save" else "Edit", color = SC.Accent, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Hidden while editing: Save is in the header, and the bar would ride up over the keyboard.
            if (state.exists && !editMode) {
                BottomActionBar(Modifier.navigationBarsPadding()) {
                    PrimaryButton(text = "Done", enabled = state.loaded, onClick = { vm.flushTitle(onDone) })
                }
            }
        },
    ) { padding ->
        when {
            !state.loaded -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SC.Accent)
            }
            !state.exists -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This recipe was deleted.", style = MaterialTheme.typography.bodyLarge, color = SC.Muted)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = titleDraft ?: state.title,
                        onValueChange = vm::onTitleChange,
                        label = { Text("Recipe title") },
                        placeholder = { Text("No title found. Add one", color = SC.Muted) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { SwapNotice(state.needSwapCount, state.rows.size) }
                item {
                    Column(Modifier.padding(start = 4.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SectionLabel("Ingredients")
                        Text(
                            if (editMode) "Fix anything the scan got wrong, then tap Save." else "Swipe left on an ingredient to delete it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SC.Muted,
                        )
                    }
                }
                items(state.rows, key = { it.ingredient.id }) { row ->
                    if (editMode) {
                        IngredientTextField(
                            value = drafts[row.ingredient.id].orEmpty(),
                            onValueChange = { drafts[row.ingredient.id] = it },
                        )
                    } else {
                        SwipeToDelete(label = row.ingredient.name, onDelete = { deleteWithUndo(row.ingredient) }) {
                            IngredientRow(
                                row = row,
                                onOpen = { sheetFor = row.ingredient.id },
                                onMissingChange = { missing ->
                                    vm.setMissing(row, missing)
                                    // Turning "Don't have it" on is the ask for alternatives: show them right away.
                                    if (missing) sheetFor = row.ingredient.id
                                },
                            )
                        }
                    }
                }
                if (!editMode) item { AddIngredientField(onAdd = vm::add) }
            }
        }
    }

    // Substitutes sheet for the tapped ingredient.
    val target = sheetFor?.let { id -> state.rows.firstOrNull { it.ingredient.id == id } }
    if (target != null) {
        val ing = target.ingredient
        val name = ing.name.ifBlank { ing.raw }
        val reason = if (target.conflicts.isNotEmpty()) SwapReason.ALLERGY else SwapReason.MISSING
        val subsVm = appViewModel(key = "subs-${ing.id}-$name-$reason-${state.profile.hashCode()}") {
            SubstitutesViewModel(container, name, state.title, reason, state.profile)
        }
        SubstitutesSheet(
            vm = subsVm,
            conflictLabels = target.conflictLabels,
            current = target.substitute,
            sheetState = sheetState,
            onSelect = { option ->
                vm.chooseSubstitute(ing.id, option)
                closeSheet()
            },
            onClear = {
                vm.chooseSubstitute(ing.id, null)
                closeSheet()
            },
            onDismiss = { sheetFor = null },
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SC.Accent,
    unfocusedBorderColor = SC.BorderStrong,
    focusedLabelColor = SC.Accent,
    cursorColor = SC.Accent,
    focusedContainerColor = SC.Card,
    unfocusedContainerColor = SC.Card,
)

@Composable
private fun SwapNotice(needSwap: Int, total: Int) {
    val ok = needSwap == 0
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (ok) SC.SafeBg else SC.AllergenBg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (ok) Icons.Filled.Check else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) SC.SafeText else SC.AllergenText,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                when {
                    total == 0 -> "No ingredients found yet"
                    ok -> "Safe for you"
                    needSwap == 1 -> "1 ingredient needs a swap"
                    else -> "$needSwap ingredients need a swap"
                },
                style = MaterialTheme.typography.titleSmall,
                color = if (ok) SC.SafeText else SC.AllergenText,
            )
            Text(
                when {
                    total == 0 -> "Add them below."
                    ok -> "${plural(total, "ingredient")} checked against your allergies."
                    else -> "Tap an ingredient to see safe substitutes."
                },
                style = MaterialTheme.typography.bodySmall,
                color = SC.Ink,
            )
        }
    }
}

@Composable
private fun IngredientRow(
    row: IngredientRowUi,
    onOpen: () -> Unit,
    onMissingChange: (Boolean) -> Unit,
) {
    val ing = row.ingredient
    val flagged = row.conflicts.isNotEmpty() && row.substitute == null
    // Alternatives are offered only for allergens or when "Don't have it" is on (or to change a chosen swap).
    val canSwap = row.conflicts.isNotEmpty() || ing.isMissing || row.substitute != null
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SC.Card)
            .then(if (flagged) Modifier.border(1.5.dp, SC.Danger, shape) else Modifier),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (canSwap) Modifier.clickable(role = Role.Button, onClickLabel = "Show substitutes", onClick = onOpen)
                    else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Same three levels as the Recipe screen: ingredient (titleMedium), details (bodyMedium, muted).
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val line = ing.displayLine()
                val sub = row.substitute
                if (sub != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "swapped for",
                            tint = SC.SwapText,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(sub.name, style = MaterialTheme.typography.titleMedium, color = SC.SwapText)
                    }
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SC.Muted,
                        textDecoration = TextDecoration.LineThrough,
                    )
                    if (sub.ratio.isNotBlank()) {
                        Text(sub.ratio, style = MaterialTheme.typography.bodyMedium, color = SC.Muted)
                    }
                } else {
                    Text(
                        line,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (flagged) SC.Danger else SC.Ink,
                    )
                    if (ing.raw.isNotBlank() && !ing.raw.equals(line, ignoreCase = true)) {
                        Text(
                            ing.raw,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SC.Muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (flagged) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = SC.AllergenText, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Contains ${row.conflictLabels.joinToString(", ")} · tap for safe swaps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SC.AllergenText,
                            )
                        }
                    } else if (ing.isMissing) {
                        Text(
                            "You don't have it · tap for alternatives",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SC.Muted,
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(SC.Divider))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(value = ing.isMissing, role = Role.Switch, onValueChange = onMissingChange)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Don't have it", style = MaterialTheme.typography.bodyMedium, color = SC.Muted, modifier = Modifier.weight(1f))
            Switch(
                checked = ing.isMissing,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SC.Accent,
                    uncheckedThumbColor = SC.Muted,
                    uncheckedTrackColor = SC.Divider,
                    uncheckedBorderColor = SC.BorderStrong,
                ),
            )
        }
    }
}

/** Swipe a row left to delete it; a red trash background shows underneath. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(label: String, onDelete: () -> Unit, content: @Composable () -> Unit) {
    // The swipe state outlives recompositions; always call the latest onDelete (the row may have been edited).
    val currentOnDelete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SC.Danger)
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White)
            }
        },
        // Swiping isn't possible with a screen reader, so offer Delete as an action too.
        modifier = Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction("Delete $label") { currentOnDelete(); true })
        },
    ) { content() }
}

/** One ingredient line as an editable text box (edit mode). */
@Composable
private fun IngredientTextField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        isError = value.isBlank(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AddIngredientField(onAdd: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text("Add ingredient, e.g. 2 tbsp honey") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            IconButton(onClick = submit, enabled = text.isNotBlank()) {
                Icon(Icons.Filled.Add, contentDescription = "Add ingredient", tint = if (text.isNotBlank()) SC.Accent else SC.Muted)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
}
