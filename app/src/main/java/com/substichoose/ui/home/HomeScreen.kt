package com.substichoose.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.substichoose.AppContainer
import com.substichoose.R
import com.substichoose.ai.ModelState
import com.substichoose.ui.appViewModel
import com.substichoose.ui.components.Badge
import com.substichoose.ui.components.LoadingPill
import com.substichoose.ui.components.ModelStatusBanner
import com.substichoose.ui.theme.SC

@Composable
fun HomeScreen(
    container: AppContainer,
    onOpenAllergies: () -> Unit,
    onScan: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onOpenReview: (Long) -> Unit,
) {
    val vm = appViewModel { HomeViewModel(container) }
    val scans by container.scanQueue.scans.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val modelState by vm.modelState.collectAsStateWithLifecycle()
    val downloadState by vm.downloadState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(SC.Background)) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Logo()
                    Spacer(Modifier.weight(1f))
                    AllergyChip(
                        summary = state.allergySummary.ifEmpty { "Set allergies" },
                        hasAllergies = state.hasAllergies,
                        onClick = onOpenAllergies,
                    )
                }
                ModelStatusBanner(modelState, downloadState, onDownload = vm::downloadModel)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("My Recipes", style = MaterialTheme.typography.headlineLarge, color = SC.Ink)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                !state.loaded -> " "
                                state.totalCount == 0 -> "Nothing saved yet"
                                else -> "${state.totalCount} saved · works offline"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = SC.Muted,
                        )
                        if (modelState is ModelState.Loading) {
                            Spacer(Modifier.width(10.dp))
                            LoadingPill()
                        }
                    }
                }
                if (state.totalCount > 0) {
                    SearchField(value = query, onValueChange = { query = it; vm.setQuery(it) })
                }
            }

            if (state.loaded && state.totalCount == 0) {
                EmptyState(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = if (scans.isEmpty()) 150.dp else 230.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.cards, key = { it.id }) { card ->
                        RecipeCard(card, onClick = { onOpenRecipe(card.id) })
                    }
                    if (state.loaded && state.cards.isEmpty() && query.isNotBlank()) {
                        item {
                            Text(
                                "No recipes match \"${query.trim()}\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SC.Muted,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            ScanProgressTab(
                scans = scans,
                onOpenReview = onOpenReview,
                onDismiss = container.scanQueue::dismiss,
                onScanAgain = onScan,
                // Clears the camera button, which sticks 30dp up out of the bar.
                modifier = Modifier.padding(bottom = 42.dp),
            )
            ScanBar(onScan = onScan)
        }
    }
}

@Composable
private fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.ic_logo),
            contentDescription = null,
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text("SubstiChoose", style = MaterialTheme.typography.titleMedium, color = SC.Ink)
    }
}

@Composable
private fun AllergyChip(summary: String, hasAllergies: Boolean, onClick: () -> Unit) {
    val description = if (hasAllergies) "My allergies: $summary" else "Set my allergies"
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(BorderStroke(1.dp, SC.BorderStrong), RoundedCornerShape(24.dp))
            .background(SC.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Shield,
            contentDescription = null,
            tint = if (hasAllergies) SC.AllergenText else SC.Muted,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = SC.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp),
        )
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, SC.Border, RoundedCornerShape(14.dp))
            .background(SC.Card)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = SC.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text("Search recipes or ingredients", style = MaterialTheme.typography.bodyLarge, color = SC.Muted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = SC.Ink),
                cursorBrush = SolidColor(SC.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Search recipes" },
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = SC.Muted)
            }
        }
    }
}

private val TilePalette = listOf(
    Color(0xFFF3E6CF) to Color(0xFF7A5420),
    Color(0xFFE9DCD3) to Color(0xFF6B3F2A),
    Color(0xFFF5E7B8) to Color(0xFF7A5E12),
    Color(0xFFF6E1C6) to Color(0xFF8A4B12),
    Color(0xFFDDEBD3) to Color(0xFF3B5E22),
)

private val SweetWords = listOf("bread", "cake", "cookie", "flan", "pie", "muffin", "brownie", "dessert", "pudding", "tart", "pancake", "waffle")

private fun iconFor(title: String): ImageVector {
    val t = title.lowercase()
    return when {
        SweetWords.any { it in t } -> Icons.Filled.Cake
        t.length % 2 == 0 -> Icons.Filled.Restaurant
        else -> Icons.Filled.LocalDining
    }
}

@Composable
private fun RecipeCard(card: RecipeCardUi, onClick: () -> Unit) {
    val (tileBg, tileFg) = TilePalette[((card.title.hashCode() % TilePalette.size) + TilePalette.size) % TilePalette.size]
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SC.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(tileBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconFor(card.title), contentDescription = null, tint = tileFg, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                card.title,
                style = MaterialTheme.typography.titleMedium,
                color = SC.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(card.subtitle, style = MaterialTheme.typography.bodySmall, color = SC.Muted)
        }
        Spacer(Modifier.width(8.dp))
        when (card.badge) {
            BadgeKind.ALLERGENS -> Badge(
                if (card.badgeCount == 1) "1 allergen" else "${card.badgeCount} allergens",
                SC.AllergenBg, SC.AllergenText,
            )
            BadgeKind.SWAPPED -> Badge("${card.badgeCount} swapped", SC.SwapBg, SC.SwapText)
            BadgeKind.SAFE -> Badge("Safe for you", SC.SafeBg, SC.SafeText)
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 40.dp, bottom = 150.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(SC.SafeBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = SC.SafeText, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Scan your first recipe", style = MaterialTheme.typography.headlineSmall, color = SC.Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(
            "Point the camera at a cookbook page or a recipe on a screen. We'll list the ingredients and flag anything you're allergic to.",
            style = MaterialTheme.typography.bodyLarge,
            color = SC.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

/** White bottom bar with the 72dp green camera button poking out of its top edge. */
@Composable
private fun ScanBar(onScan: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(SC.Card)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(SC.Border))
            Spacer(Modifier.height(103.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-30).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(SC.Accent)
                    .border(5.dp, SC.Background, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Scan a recipe", onClick = onScan)
                    .semantics { contentDescription = "Scan a recipe" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text("Scan recipe", style = MaterialTheme.typography.labelMedium, color = SC.Ink)
        }
    }
}
