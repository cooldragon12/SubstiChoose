package com.substichoose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.substichoose.ai.ModelState
import com.substichoose.ui.theme.SC

/** Small rounded badge, e.g. "2 allergens", "Safe for you", "1 swapped". */
@Composable
fun Badge(text: String, background: Color, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun AllergenBadge(text: String, modifier: Modifier = Modifier) =
    Badge(text, SC.DangerBg, SC.Danger, modifier)

@Composable
fun SwapBadge(text: String, modifier: Modifier = Modifier) =
    Badge(text, SC.SwapBg, SC.SwapText, modifier)

@Composable
fun SafeBadge(text: String, modifier: Modifier = Modifier) =
    Badge(text, SC.SafeBg, SC.SafeText, modifier)

/** Uppercase section label ("COMMON ALLERGENS"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = SC.Muted,
        modifier = modifier,
    )
}

/** Back arrow + small muted caption, as in the My Allergies mockup. */
@Composable
fun BackHeader(caption: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SC.Ink)
        }
        Text(caption, style = MaterialTheme.typography.titleSmall, color = SC.Muted, modifier = Modifier.weight(1f))
        actions()
    }
}

/** Big dark full-width button ("Save · 2 selected", "Done"). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SC.Ink, contentColor = Color.White),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** White bottom bar with a top border, holding one primary action. */
@Composable
fun BottomActionBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.fillMaxWidth().background(SC.Card)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(SC.Border))
        Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
            content()
        }
    }
}

@Composable
fun Disclaimer(modifier: Modifier = Modifier, text: String = "Check labels — AI suggestions may be wrong.") {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = SC.Muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = SC.Muted)
    }
}

/** Small "Loading Gemma…" pill. */
@Composable
fun LoadingPill(text: String = "Loading Gemma…", modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SC.SafeBg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = SC.SafeText)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = SC.SafeText, fontWeight = FontWeight.Bold)
    }
}
