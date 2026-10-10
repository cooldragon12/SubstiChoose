package com.substichoose.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device text recognition (ML Kit, Latin script). No network needed: the model ships
 * inside the text-recognition library.
 */
class OcrService(context: Context) {

    private val appContext = context.applicationContext

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** ML Kit Latin text recognition, on-device. Returns "" when no text. Throws on unreadable image. */
    suspend fun readText(imageUri: Uri): String = withContext(Dispatchers.IO) {
        // Reads the file and applies its EXIF rotation. Throws IOException if the image can't be opened.
        val image = InputImage.fromFilePath(appContext, imageUri)
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) cont.resume(result.text.orEmpty())
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
                .addOnCanceledListener {
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("Text recognition was cancelled"))
                }
        }
    }

    /** Releases the ML Kit recognizer. Optional; the app normally keeps it for its whole life. */
    fun close() {
        runCatching { recognizer.close() }
    }
}
