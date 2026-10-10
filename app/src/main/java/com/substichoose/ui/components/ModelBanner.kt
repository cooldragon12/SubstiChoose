package com.substichoose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.substichoose.ai.ModelDownloadState
import com.substichoose.ai.ModelDownloader
import com.substichoose.ai.ModelState
import com.substichoose.ui.theme.SC
import java.util.Locale

/**
 * Home banner while Gemma isn't usable: the download's progress, or what went wrong.
 * Shows nothing when the model is ready or loading.
 */
@Composable
fun ModelStatusBanner(
    state: ModelState,
    download: ModelDownloadState,
    onDownload: (allowMobileData: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is ModelState.Failed -> Banner(
            warning = true,
            title = "Gemma couldn't start",
            body = state.message,
            modifier = modifier,
        )
        is ModelState.Missing -> MissingBanner(download, onDownload, modifier)
        else -> Unit
    }
}

@Composable
private fun MissingBanner(
    download: ModelDownloadState,
    onDownload: (allowMobileData: Boolean) -> Unit,
    modifier: Modifier,
) {
    val size = gb(ModelDownloader.MODEL_BYTES)
    val stillWorks = "Quick swaps and scanning still work meanwhile."
    when (download) {
        is ModelDownloadState.Running -> Banner(
            warning = false,
            title = "Downloading Gemma AI…",
            body = "${gb(download.downloaded)} of ${gb(download.total)}. You can keep using the app.",
            progress = (download.downloaded.toFloat() / download.total).coerceIn(0f, 1f),
            modifier = modifier,
        )
        is ModelDownloadState.Waiting -> Banner(
            warning = false,
            title = if (download.onWifiOnly) "Gemma AI will download on Wi-Fi" else "Gemma AI download is waiting",
            body = if (download.onWifiOnly) "$size, one time. Connect to Wi-Fi, or use mobile data now. $stillWorks"
            else "Waiting for an internet connection. $stillWorks",
            action = if (download.onWifiOnly) "Use mobile data" to { onDownload(true) } else null,
            modifier = modifier,
        )
        ModelDownloadState.Verifying -> Banner(
            warning = false,
            title = "Almost ready…",
            body = "Checking the downloaded Gemma file.",
            progress = null,
            indeterminate = true,
            modifier = modifier,
        )
        is ModelDownloadState.NotEnoughSpace -> Banner(
            warning = true,
            title = "Not enough space for Gemma AI",
            body = "Free up ${gb(download.neededBytes)} more, then try again. $stillWorks",
            action = "Try again" to { onDownload(false) },
            modifier = modifier,
        )
        is ModelDownloadState.Failed -> Banner(
            warning = true,
            title = "Gemma AI didn't download",
            body = "${download.message} $stillWorks",
            action = "Try again" to { onDownload(false) },
            modifier = modifier,
        )
        ModelDownloadState.Idle -> Banner(
            warning = false,
            title = "Get Gemma AI",
            body = "Smarter scans and more swap ideas. $size, one time, runs fully on your phone.",
            action = "Download on Wi-Fi" to { onDownload(false) },
            modifier = modifier,
        )
    }
}

@Composable
private fun Banner(
    warning: Boolean,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    indeterminate: Boolean = false,
    action: Pair<String, () -> Unit>? = null,
) {
    val bg = if (warning) SC.AllergenBg else SC.SafeBg
    val fg = if (warning) SC.AllergenText else SC.SafeText
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = if (action != null) 4.dp else 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (warning) Icons.Filled.Warning else Icons.Filled.CloudDownload,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = fg)
            when {
                progress != null -> LinearProgressIndicator(
                    progress = { progress },
                    color = SC.Accent,
                    trackColor = Color.White,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                )
                indeterminate -> LinearProgressIndicator(
                    color = SC.Accent,
                    trackColor = Color.White,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                )
            }
            Text(body, style = MaterialTheme.typography.bodySmall, color = SC.Ink)
            if (action != null) {
                TextButton(onClick = action.second, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(action.first, style = MaterialTheme.typography.titleSmall, color = SC.Accent)
                }
            }
        }
    }
}

/** "1.2 GB" */
private fun gb(bytes: Long): String = String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
