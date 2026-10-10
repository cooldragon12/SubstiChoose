package com.substichoose.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.substichoose.pipeline.ScanStage
import com.substichoose.ui.theme.SC

private val StageOrder = listOf(ScanStage.COPYING, ScanStage.READING_TEXT, ScanStage.UNDERSTANDING, ScanStage.SAVING)

/** The step list for a scan: done steps ticked, the current one spinning. stage = null means queued. */
@Composable
fun ScanStageList(stage: ScanStage?, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SC.Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val current = stage?.let { StageOrder.indexOf(it) } ?: -1
        StageOrder.forEachIndexed { index, s ->
            StageRow(text = s.friendlyText(), done = index < current, active = index == current)
        }
    }
}


@Composable
private fun StageRow(text: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (done) SC.Accent else if (active) SC.SafeBg else SC.Divider),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Icon(Icons.Filled.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(14.dp))
                active -> CircularProgressIndicator(color = SC.Accent, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done || active) SC.Ink else SC.Muted,
        )
    }
}
