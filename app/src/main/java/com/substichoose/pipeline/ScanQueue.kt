package com.substichoose.pipeline

import android.net.Uri
import com.substichoose.data.RecipeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/** One scan running (or finished) in the background. */
data class BackgroundScan(
    val id: Long,
    val status: Status,
) {
    sealed interface Status {
        /** Waiting for an earlier scan to finish (Gemma runs one request at a time). */
        data object Queued : Status
        data class Running(val stage: ScanStage) : Status
        data class Done(val recipeId: Long, val title: String) : Status
        data class Failed(val message: String) : Status
    }

    val isActive: Boolean get() = status is Status.Queued || status is Status.Running
}

/**
 * Runs scans in the app's scope so they keep going while the user moves around the app.
 * Scans run one at a time, in order. Finished and failed scans stay in [scans] until dismissed.
 */
class ScanQueue(
    private val scope: CoroutineScope,
    private val pipeline: ScanPipeline,
    private val recipes: RecipeRepository,
) {
    private val _scans = MutableStateFlow<List<BackgroundScan>>(emptyList())
    val scans: StateFlow<List<BackgroundScan>> = _scans.asStateFlow()

    private val nextId = AtomicLong(1)
    private val runLock = Mutex()

    /** Queues a photo for scanning and returns at once. */
    fun start(uri: Uri) {
        val id = nextId.getAndIncrement()
        _scans.update { it + BackgroundScan(id, BackgroundScan.Status.Queued) }
        scope.launch {
            runLock.withLock {
                set(id, BackgroundScan.Status.Running(ScanStage.COPYING))
                val result = try {
                    pipeline.process(uri) { stage -> set(id, BackgroundScan.Status.Running(stage)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
                set(id, result.fold(
                    onSuccess = { recipeId ->
                        val title = recipes.observeRecipe(recipeId).firstOrNull()?.recipe?.title.orEmpty()
                        BackgroundScan.Status.Done(recipeId, title.ifBlank { "Your recipe" })
                    },
                    onFailure = {
                        BackgroundScan.Status.Failed(
                            it.message?.takeIf(String::isNotBlank) ?: "Couldn't read a recipe from that photo."
                        )
                    },
                ))
            }
        }
    }

    /** Removes a finished or failed scan from the list. Running scans can't be dismissed. */
    fun dismiss(id: Long) {
        _scans.update { list -> list.filterNot { it.id == id && !it.isActive } }
    }

    private fun set(id: Long, status: BackgroundScan.Status) {
        _scans.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }
    }
}
