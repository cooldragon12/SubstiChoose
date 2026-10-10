package com.substichoose.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.substichoose.pipeline.BackgroundScan
import com.substichoose.pipeline.BackgroundScan.Status
import com.substichoose.ui.scan.ScanStageList
import com.substichoose.ui.scan.friendlyText
import com.substichoose.ui.theme.SC

/**
 * Small tab above the Scan button while a scan runs in the background. Tap or swipe up for the full
 * step-by-step progress. When a scan is ready, tapping the tab opens it.
 */
@Composable
fun ScanProgressTab(
    scans: List<BackgroundScan>,
    onOpenReview: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (scans.isEmpty()) return
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    // The scan the tab talks about: the one running now, else the most recent result.
    val focus = scans.firstOrNull { it.status is Status.Running }
        ?: scans.firstOrNull { it.isActive }
        ?: scans.last()
    val others = scans.size - 1

    val openReview: (BackgroundScan, Long) -> Unit = { scan, recipeId ->
        onDismiss(scan.id)
        onOpenReview(recipeId)
    }

    var dragged by remember { mutableStateOf(0f) }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(SC.Card)
            .border(1.dp, SC.Border, RoundedCornerShape(20.dp))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged < -40f) sheetOpen = true },
                    onVerticalDrag = { _, dy -> dragged += dy },
                )
            }
            .clickable(role = Role.Button) {
                val s = focus.status
                if (s is Status.Done) openReview(focus, s.recipeId) else sheetOpen = true
            }
            .semantics(mergeDescendants = true) { contentDescription = tabDescription(focus.status) }
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIcon(focus.status)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tabTitle(focus.status),
                style = MaterialTheme.typography.titleSmall,
                color = SC.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                tabSubtitle(focus.status) + if (others > 0) " · +$others more" else "",
                style = MaterialTheme.typography.bodySmall,
                color = SC.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = SC.Muted)
    }

    if (sheetOpen) {
        ScanProgressSheet(
            scans = scans,
            onOpenReview = { scan, id -> sheetOpen = false; openReview(scan, id) },
            onDismiss = onDismiss,
            onScanAgain = { scan -> sheetOpen = false; onDismiss(scan.id); onScanAgain() },
            onClose = { sheetOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanProgressSheet(
    scans: List<BackgroundScan>,
    onOpenReview: (BackgroundScan, Long) -> Unit,
    onDismiss: (Long) -> Unit,
    onScanAgain: (BackgroundScan) -> Unit,
    onClose: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState, containerColor = SC.Background) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Scanning in the background", style = MaterialTheme.typography.headlineSmall, color = SC.Ink)
                    Text(
                        "Everything stays on your phone. You can keep using the app; we'll let you know here when it's ready.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SC.Muted,
                    )
                }
            }
            items(scans, key = { it.id }) { scan ->
                ScanSection(
                    scan = scan,
                    onOpenReview = { id -> onOpenReview(scan, id) },
                    onDismiss = { onDismiss(scan.id) },
                    onScanAgain = { onScanAgain(scan) },
                )
            }
        }
    }
}

@Composable
private fun ScanSection(
    scan: BackgroundScan,
    onOpenReview: (Long) -> Unit,
    onDismiss: () -> Unit,
    onScanAgain: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(scan.status)
            Spacer(Modifier.width(10.dp))
            Text(tabTitle(scan.status), style = MaterialTheme.typography.titleMedium, color = SC.Ink)
        }
        when (val s = scan.status) {
            Status.Queued -> {
                Text("Waiting for the scan before it to finish.", style = MaterialTheme.typography.bodyMedium, color = SC.Muted)
                ScanStageList(stage = null)
            }
            is Status.Running -> ScanStageList(stage = s.stage)
            is Status.Done -> {
                Text("Check the ingredient list and pick your swaps.", style = MaterialTheme.typography.bodyMedium, color = SC.Muted)
                Button(
                    onClick = { onOpenReview(s.recipeId) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SC.Ink, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Review ingredients", style = MaterialTheme.typography.labelLarge) }
            }
            is Status.Failed -> {
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = SC.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onScanAgain, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Scan again", color = SC.Accent, style = MaterialTheme.typography.titleSmall)
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Dismiss", color = SC.Muted, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: Status) {
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        when (status) {
            Status.Queued, is Status.Running ->
                CircularProgressIndicator(color = SC.Accent, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
            is Status.Done -> Box(
                Modifier.size(32.dp).clip(CircleShape).background(SC.SafeBg),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SC.Accent, modifier = Modifier.size(22.dp)) }
            is Status.Failed -> Box(
                Modifier.size(32.dp).clip(CircleShape).background(SC.AllergenBg),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Warning, contentDescription = null, tint = SC.AllergenText, modifier = Modifier.size(18.dp)) }
        }
    }
}

private fun tabTitle(status: Status): String = when (status) {
    Status.Queued -> "Scan waiting…"
    is Status.Running -> "Processing your recipe…"
    is Status.Done -> "${status.title} is ready"
    is Status.Failed -> "We couldn't read that recipe"
}

private fun tabSubtitle(status: Status): String = when (status) {
    Status.Queued -> "Starts when the current scan finishes"
    is Status.Running -> status.stage.friendlyText()
    is Status.Done -> "Tap to review ingredients"
    is Status.Failed -> "Swipe up for details"
}

private fun tabDescription(status: Status): String = when (status) {
    is Status.Done -> "${tabTitle(status)}. Tap to review ingredients."
    else -> "${tabTitle(status)} ${tabSubtitle(status)}. Swipe up or tap for details."
}
