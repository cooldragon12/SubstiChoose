package com.substichoose.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Where the Gemma download is, for the Home banner. */
sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    /** Queued but not moving: [onWifiOnly] = waiting for Wi-Fi (mobile data not allowed). */
    data class Waiting(val onWifiOnly: Boolean) : ModelDownloadState
    data class Running(val downloaded: Long, val total: Long) : ModelDownloadState
    /** Checking the finished file's size and SHA-256 before using it. */
    data object Verifying : ModelDownloadState
    data class NotEnoughSpace(val neededBytes: Long) : ModelDownloadState
    data class Failed(val message: String) : ModelDownloadState
}

/**
 * Downloads Gemma 4 E2B (2.6 GB, Apache 2.0, public on Hugging Face) into the folder [GemmaService]
 * loads from. Android's DownloadManager does the work, so it continues outside the app, survives
 * restarts and shows progress in the notification shade. By default it waits for Wi-Fi.
 *
 * The file arrives as "<name>.download" (GemmaService ignores it), is checked against the known
 * size and SHA-256, then renamed to "<name>.litertlm" and loaded.
 */
class ModelDownloader(
    context: Context,
    private val gemma: GemmaService,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(DownloadManager::class.java)
    private val prefs = appContext.getSharedPreferences("model_download", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    private var watchJob: Job? = null

    private val dir: File? get() = appContext.getExternalFilesDir(null)
    private val partFile: File? get() = dir?.let { File(it, "$FILE_NAME.download") }

    /** Starts the Wi-Fi-only download when there is no model, or resumes watching one already queued. */
    fun autoStart() {
        if (resumeWatching()) return
        if (_state.value is ModelDownloadState.Failed) return // the user retries from the banner
        start(allowMobileData = false)
    }

    /** (Re)starts the download. [allowMobileData] = also download on mobile data. */
    fun start(allowMobileData: Boolean) {
        val target = dir ?: run {
            _state.value = ModelDownloadState.Failed("Phone storage isn't available right now.")
            return
        }
        cancelQueued()
        val free = target.usableSpace
        if (free < MODEL_BYTES + SPARE_BYTES) {
            _state.value = ModelDownloadState.NotEnoughSpace(MODEL_BYTES + SPARE_BYTES - free)
            return
        }
        partFile?.delete()
        val request = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("Gemma AI model for SubstiChoose")
            .setDescription("2.6 GB, one time")
            .setDestinationInExternalFilesDir(appContext, null, "$FILE_NAME.download")
            .setAllowedOverMetered(allowMobileData)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        val id = try {
            manager.enqueue(request)
        } catch (e: Exception) {
            Log.w(TAG, "enqueue failed", e)
            _state.value = ModelDownloadState.Failed("Couldn't start the download.")
            return
        }
        prefs.edit().putLong(KEY_ID, id).putBoolean(KEY_MOBILE, allowMobileData).apply()
        Log.i(TAG, "Download queued (id=$id, mobileData=$allowMobileData)")
        _state.value = ModelDownloadState.Waiting(onWifiOnly = !allowMobileData)
        watch(id)
    }

    /** True when a download from an earlier run is still queued; watches it again. */
    private fun resumeWatching(): Boolean {
        val id = prefs.getLong(KEY_ID, -1L)
        if (id < 0 || watchJob?.isActive == true) return watchJob?.isActive == true
        if (query(id) == null) {
            prefs.edit().remove(KEY_ID).apply()
            return false
        }
        watch(id)
        return true
    }

    private fun watch(id: Long) {
        watchJob?.cancel()
        watchJob = scope.launch {
            val wifiOnly = !prefs.getBoolean(KEY_MOBILE, false)
            while (isActive) {
                val row = query(id)
                if (row == null) {
                    _state.value = ModelDownloadState.Failed("The download was cancelled.")
                    prefs.edit().remove(KEY_ID).apply()
                    return@launch
                }
                when (row.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        prefs.edit().remove(KEY_ID).apply()
                        finish()
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        Log.w(TAG, "Download failed, reason=${row.reason}")
                        prefs.edit().remove(KEY_ID).apply()
                        manager.remove(id)
                        partFile?.delete()
                        _state.value = ModelDownloadState.Failed(failureText(row.reason))
                        return@launch
                    }
                    DownloadManager.STATUS_RUNNING ->
                        _state.value = ModelDownloadState.Running(row.downloaded, row.total.takeIf { it > 0 } ?: MODEL_BYTES)
                    else -> // PENDING or PAUSED (waiting for Wi-Fi or a network, retrying)
                        _state.value = when {
                            row.status == DownloadManager.STATUS_PAUSED &&
                                row.reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI -> ModelDownloadState.Waiting(onWifiOnly = true)
                            row.downloaded > 0 -> ModelDownloadState.Running(row.downloaded, row.total.takeIf { it > 0 } ?: MODEL_BYTES)
                            else -> ModelDownloadState.Waiting(onWifiOnly = wifiOnly)
                        }
                }
                delay(POLL_MS)
            }
        }
    }

    /** Checks the downloaded file, moves it into place and loads Gemma. */
    private suspend fun finish() {
        _state.value = ModelDownloadState.Verifying
        val part = partFile
        val ok = part != null && withContext(Dispatchers.IO) { part.length() == MODEL_BYTES && sha256(part) == MODEL_SHA256 }
        if (!ok) {
            Log.w(TAG, "Downloaded file failed the check (size=${part?.length()})")
            part?.delete()
            _state.value = ModelDownloadState.Failed("The download was damaged. Please try again.")
            return
        }
        val final = File(part!!.parentFile, FILE_NAME)
        if (!part.renameTo(final)) {
            _state.value = ModelDownloadState.Failed("Couldn't save the model file.")
            return
        }
        Log.i(TAG, "Model ready at ${final.absolutePath}")
        _state.value = ModelDownloadState.Idle
        gemma.ensureLoaded()
    }

    private fun cancelQueued() {
        watchJob?.cancel()
        val id = prefs.getLong(KEY_ID, -1L)
        if (id >= 0) runCatching { manager.remove(id) }
        prefs.edit().remove(KEY_ID).apply()
    }

    private data class Row(val status: Int, val reason: Int, val downloaded: Long, val total: Long)

    private fun query(id: Long): Row? = try {
        manager.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) null
            else Row(
                status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            )
        }
    } catch (e: Exception) {
        Log.w(TAG, "query failed", e)
        null
    }

    private fun failureText(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough free space on the phone."
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_CANNOT_RESUME -> "The connection dropped. Please try again."
        else -> "The download didn't finish. Please try again."
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(1 shl 20).use { input ->
            val buf = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "ModelDownloader"
        const val FILE_NAME = "gemma-4-E2B-it.litertlm"
        private const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        const val MODEL_BYTES = 2_588_147_712L
        private const val MODEL_SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
        /** Room left over after the download, so the phone isn't filled to the brim. */
        private const val SPARE_BYTES = 300L * 1024 * 1024
        private const val POLL_MS = 1000L
        private const val KEY_ID = "id"
        private const val KEY_MOBILE = "mobile"
    }
}
