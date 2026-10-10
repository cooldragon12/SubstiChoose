package com.substichoose.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

private val ScreenBg = Color(0xFFF5F6F1)
private val TextMain = Color(0xFF18201B)
private val TextSecondary = Color(0xFF55605A)
private val Accent = Color(0xFF2F6B3F)

/** Full-screen CameraX preview with a shutter button. Handles the CAMERA permission request itself.
 *  Saves a JPEG into context.cacheDir and returns its Uri. */
@Composable
fun CameraCapture(
    onCaptured: (Uri) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var deniedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) deniedOnce = true
    }

    // Ask once when the screen opens.
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Re-check when the user comes back from the system Settings screen.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = context.hasCameraPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasPermission) {
        CameraPreviewWithShutter(onCaptured = onCaptured, onError = onError, modifier = modifier)
    } else {
        PermissionNeeded(
            showSettings = deniedOnce,
            onAllow = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            modifier = modifier,
        )
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Back-camera preview filling the space, with a round white shutter at the bottom center. */
@Composable
private fun CameraPreviewWithShutter(
    onCaptured: (Uri) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCaptured by rememberUpdatedState(onCaptured)
    val currentOnError by rememberUpdatedState(onError)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY) // sharper text for OCR
            .build()
    }
    var capturing by remember { mutableStateOf(false) }

    // Bind preview + capture to the lifecycle; unbind when this leaves the composition.
    DisposableEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false
        val preview = Preview.Builder().build()

        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val provider = providerFuture.get()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            } catch (e: Exception) {
                currentOnError("Couldn't start the camera. You can pick a photo from your gallery instead.")
            }
        }, mainExecutor)

        onDispose {
            disposed = true
            runCatching {
                if (providerFuture.isDone) providerFuture.get().unbind(preview, imageCapture)
            }
        }
    }

    fun takePhoto() {
        if (capturing) return
        capturing = true
        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        try {
            imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        capturing = false
                        currentOnCaptured(Uri.fromFile(file))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        capturing = false
                        runCatching { file.delete() }
                        currentOnError("Couldn't take the photo. Please try again.")
                    }
                },
            )
        } catch (e: Exception) {
            capturing = false
            currentOnError("Couldn't take the photo. Please try again.")
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .size(72.dp)
                .clip(CircleShape)
                .background(if (capturing) Color.White.copy(alpha = 0.6f) else Color.White)
                .clickable(enabled = !capturing, role = Role.Button) { takePhoto() }
                .semantics { contentDescription = "Take photo" },
            contentAlignment = Alignment.Center,
        ) {
            // Inner ring, like a classic camera shutter.
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .border(width = 2.dp, color = Color.Black.copy(alpha = 0.25f), shape = CircleShape)
            )
            if (capturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Accent,
                    strokeWidth = 3.dp,
                )
            }
        }
    }
}

/** Friendly explanation + "Allow camera" button when the permission isn't granted. */
@Composable
private fun PermissionNeeded(
    showSettings: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().background(ScreenBg).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoCamera,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Camera access needed",
                color = TextMain,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "SubstiChoose uses the camera to read your recipe. Photos stay on your phone.",
                color = TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAllow,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
            ) {
                Text("Allow camera")
            }
            if (showSettings) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenSettings) {
                    Text("Open settings", color = Accent)
                }
            }
        }
    }
}
