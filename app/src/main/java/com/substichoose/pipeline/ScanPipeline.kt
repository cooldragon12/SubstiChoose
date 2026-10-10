package com.substichoose.pipeline

import android.content.Context
import android.net.Uri
import android.util.Log
import com.substichoose.ai.GemmaService
import com.substichoose.data.IngredientLineParser
import com.substichoose.data.RecipeRepository
import com.substichoose.model.ExtractedRecipe
import com.substichoose.ocr.OcrService
import com.substichoose.safety.AllergenChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Where a scan is, for the progress UI. */
enum class ScanStage { COPYING, READING_TEXT, UNDERSTANDING, SAVING }

/** Photo -> structured, saved recipe. Never throws (except coroutine cancellation). */
class ScanPipeline(
    context: Context,
    private val ocr: OcrService,
    private val gemma: GemmaService,
    private val recipes: RecipeRepository,
    private val checker: AllergenChecker,
) {
    private val appContext = context.applicationContext

    /** Photo -> saved recipe id.
     *  1. copy image into filesDir/images
     *  2. read the text: Gemma vision (handles handwriting), else ML Kit OCR
     *  3. if the offline parser finds the ingredient list: gemma.extractIngredients(lines) (fast path);
     *     otherwise gemma.extractRecipe(ocrText); if null or no ingredients -> gemma.extractRecipeFromImage(file);
     *     if still null -> a simple offline parser of the OCR text (lines between "ingredients" and
     *     "steps/instructions/directions/procedure" headings, or bullet/number-led lines) so the demo never dead-ends
     *  4. recipes.saveNew(...)
     *  Result.failure with a user-readable message when nothing usable was found. */
    suspend fun process(imageUri: Uri, onStage: (ScanStage) -> Unit = {}): Result<Long> {
        try {
            // 1. Copy the photo somewhere permanent (gallery Uris can lose permission; camera files live in cache).
            onStage(ScanStage.COPYING)
            val imageFile = withContext(Dispatchers.IO) { copyImage(imageUri) }
                ?: return Result.failure(Exception("Couldn't open that photo. Please try another one."))

            // 2. Read the text. Gemma vision first: it reads handwriting that ML Kit misses.
            //    ML Kit is the fallback when Gemma is missing or sees no text.
            onStage(ScanStage.READING_TEXT)
            val ocrText = safeAi("readImageText") { gemma.readImageText(imageFile) }
                ?: try {
                    ocr.readText(Uri.fromFile(imageFile)).trim()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Log.w(TAG, "OCR failed", e)
                    ""
                }
            Log.i(TAG, "Read ${ocrText.length} chars")

            // 3. Understand: Gemma on text -> Gemma on the image -> offline rules.
            onStage(ScanStage.UNDERSTANDING)
            val recipe = understand(ocrText, imageFile)
            if (recipe == null) {
                runCatching { imageFile.delete() }
                val message = if (ocrText.isBlank()) {
                    "Couldn't read any text. Try better light and hold the phone steady."
                } else {
                    "Couldn't find a recipe in that photo. Make sure the ingredient list is in the frame."
                }
                return Result.failure(Exception(message))
            }

            // 4. Save.
            onStage(ScanStage.SAVING)
            val id = recipes.saveNew(recipe, imageFile.absolutePath, ocrText)
            cleanUpCameraCache(imageUri)
            return Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Scan failed", e)
            return Result.failure(Exception("Something went wrong while reading the recipe. Please try again."))
        }
    }

    /** The best recipe we can get, or null when nothing usable was found. */
    private suspend fun understand(ocrText: String, imageFile: File): ExtractedRecipe? {
        // Fast path: when the rules can already find the ingredient list, Gemma only names each line
        // (a short reply), leaves out lines that aren't food, and picks the title from the top lines.
        // Steps come straight from the OCR text. A line Gemma leaves out is dropped, unless it starts
        // with an amount ("2 eggs") or names an allergen: those keep the offline parse, so no allergen
        // word is ever lost to a wrong Gemma answer.
        val offline = if (ocrText.isBlank()) null else withContext(Dispatchers.Default) { OfflineRecipeParser.parse(ocrText) }
        if (offline != null && offline.ingredients.size >= MIN_FAST_PATH_LINES) {
            val lines = offline.ingredients.map { it.raw }
            val top = OfflineRecipeParser.topLines(ocrText)
            val reply = safeAi("extractIngredients") { gemma.extractIngredients(lines, top) }
            val merged = offline.ingredients.mapIndexedNotNull { i, ing ->
                val notFood = reply != null && i !in reply.named &&
                    !IngredientLineParser.startsWithQty(ing.raw) && checker.allergensIn(ing.raw).isEmpty()
                if (notFood) {
                    Log.i(TAG, "Gemma: not an ingredient: ${ing.raw}")
                    null
                } else {
                    reply?.named?.get(i) ?: ing
                }
            }
            // Gemma's title only counts if it is written on the page; "" means it found none.
            val title = if (reply != null) reply.title.takeIf { isWrittenIn(it, top.joinToString(" ")) }.orEmpty() else offline.title
            if (merged.isNotEmpty()) {
                return tidy(offline.copy(title = title, ingredients = merged), ocrText) ?: offline
            }
            // Gemma says none of these lines is food: the rules picked the wrong lines. Read everything.
        }

        if (ocrText.isNotBlank()) {
            safeAi("extractRecipe") { gemma.extractRecipe(ocrText) }
                ?.let { tidy(it, ocrText) }
                ?.let { return it }
        }

        safeAi("extractRecipeFromImage") { gemma.extractRecipeFromImage(imageFile) }
            ?.let { tidy(it, ocrText) }
            ?.let { return it }

        if (offline == null) return null
        // Steps alone are still worth saving: the user can add ingredients on the Review screen.
        return offline.takeIf { it.ingredients.isNotEmpty() || it.steps.isNotEmpty() }
    }

    /** Runs a Gemma call, turning any failure into null. */
    private suspend fun <T> safeAi(label: String, block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.w(TAG, "$label failed", e)
        null
    }

    /**
     * Drops empty rows and text that is clearly not an ingredient, fills raw gaps. The title stays
     * blank unless it is written in the OCR text (Gemma must not invent one); with no OCR text
     * (vision only) Gemma's title is trusted. Returns null when no ingredient is left.
     */
    private fun tidy(recipe: ExtractedRecipe, ocrText: String): ExtractedRecipe? {
        val ingredients = recipe.ingredients
            .filter { it.name.isNotBlank() }
            .map { ing ->
                if (ing.raw.isNotBlank()) ing
                else ing.copy(raw = listOf(ing.qty, ing.unit, ing.name).filter { it.isNotBlank() }.joinToString(" "))
            }
            .filterNot { IngredientFilter.isNotIngredient(it.raw) }
        if (ingredients.isEmpty()) return null
        val title = recipe.title.trim().takeIf { ocrText.isBlank() || isWrittenIn(it, ocrText) }.orEmpty().take(80)
        return recipe.copy(
            title = title,
            ingredients = ingredients,
            steps = recipe.steps.map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    /** Copies any readable Uri (content:// or file://) to filesDir/images/<timestamp>.jpg. */
    private fun copyImage(uri: Uri): File? = try {
        val dir = File(appContext.filesDir, "images").apply { mkdirs() }
        val target = File(dir, "${System.currentTimeMillis()}.jpg")
        val input = appContext.contentResolver.openInputStream(uri)
        if (input == null) {
            null
        } else {
            input.use { src -> target.outputStream().use { dst -> src.copyTo(dst) } }
            if (target.length() > 0L) target else { target.delete(); null }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Copying $uri failed", e)
        null
    }

    /** Deletes the temporary camera JPEG once its copy is saved. Gallery photos are never touched. */
    private suspend fun cleanUpCameraCache(uri: Uri) {
        if (uri.scheme != "file") return
        withContext(Dispatchers.IO) {
            runCatching {
                val path = uri.path ?: return@runCatching
                val file = File(path)
                if (file.canonicalPath.startsWith(appContext.cacheDir.canonicalPath)) file.delete()
            }
        }
    }

    internal companion object {
        private const val TAG = "ScanPipeline"

        /** True when [title] appears in [text], ignoring case, punctuation and line breaks. */
        fun isWrittenIn(title: String, text: String): Boolean {
            fun norm(s: String) = s.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
            val t = norm(title)
            return t.isNotEmpty() && " ${norm(text)} ".contains(" $t ")
        }

        /** Fewer lines than this usually means the rules missed the list; let Gemma read everything. */
        private const val MIN_FAST_PATH_LINES = 2
    }
}
