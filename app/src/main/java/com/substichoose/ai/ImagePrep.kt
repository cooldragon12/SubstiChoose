package com.substichoose.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import java.io.File

/**
 * Prepares a photo for Gemma vision: camera photos are often stored sideways with an EXIF
 * rotation flag that the model ignores, and full-size photos waste time. Writes an upright
 * JPEG no larger than [MAX_SIDE] pixels on its long side.
 */
internal object ImagePrep {

    private const val TAG = "ImagePrep"
    private const val MAX_SIDE = 2048

    /** Upright, downscaled copy of [source] in [dir], or null if the photo can't be decoded. Caller deletes it. */
    fun uprightCopy(source: File, dir: File): File? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        if (decoded == null) {
            null
        } else {
            val matrix = Matrix()
            val long = maxOf(decoded.width, decoded.height)
            if (long > MAX_SIDE) (MAX_SIDE.toFloat() / long).let { matrix.postScale(it, it) }
            val degrees = when (ExifInterface(source.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) matrix.postRotate(degrees)
            val upright = if (matrix.isIdentity) decoded
            else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val out = File(dir, "gemma-read-${System.nanoTime()}.jpg")
            out.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            if (upright !== decoded) upright.recycle()
            decoded.recycle()
            out
        }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't prepare $source", e)
        null
    }
}
