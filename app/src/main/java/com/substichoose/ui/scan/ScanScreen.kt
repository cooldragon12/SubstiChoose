package com.substichoose.ui.scan

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.substichoose.AppContainer
import com.substichoose.camera.CameraCapture
import com.substichoose.ui.appViewModel
import com.substichoose.ui.theme.SC

@Composable
fun ScanScreen(
    container: AppContainer,
    onClose: () -> Unit,
) {
    val vm = appViewModel { ScanViewModel() }
    val state by vm.state.collectAsStateWithLifecycle()

    // Hand the photo to the background queue and go straight back; Home shows the progress tab.
    val startScan: (Uri) -> Unit = { uri ->
        container.scanQueue.start(uri)
        onClose()
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) startScan(uri)
    }

    val openGallery = {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            ScanUiState.Camera -> {
                CameraCapture(
                    onCaptured = startScan,
                    onError = { message -> vm.showError(message) },
                    modifier = Modifier.fillMaxSize(),
                )
                ScanTopBar(
                    onClose = onClose,
                    onGallery = openGallery,
                )
            }
            is ScanUiState.Failed -> FailedOverlay(
                message = s.message,
                onRetry = { vm.retry() },
                onGallery = openGallery,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun ScanTopBar(onClose: () -> Unit, onGallery: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onClose,
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close camera")
        }
        Text(
            "Scan recipe",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        Button(
            onClick = onGallery,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = SC.Ink),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Gallery", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun FailedOverlay(message: String, onRetry: () -> Unit, onGallery: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(SC.Background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(SC.AllergenBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = SC.AllergenText, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("We couldn't read that recipe", style = MaterialTheme.typography.headlineSmall, color = SC.Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = SC.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tip: fill the frame with the ingredient list, in good light.",
                style = MaterialTheme.typography.bodySmall,
                color = SC.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SC.Ink, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Try again", style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onGallery, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = SC.Accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pick a photo from the gallery", color = SC.Accent, style = MaterialTheme.typography.titleSmall)
            }
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Back to my recipes", color = SC.Accent, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
