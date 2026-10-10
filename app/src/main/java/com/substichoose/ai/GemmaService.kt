package com.substichoose.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.substichoose.model.AdaptedStep
import com.substichoose.model.AdaptedSteps
import com.substichoose.model.ExtractedIngredient
import com.substichoose.model.ExtractedRecipe
import com.substichoose.model.IngredientRows
import com.substichoose.model.ModelJson
import com.substichoose.model.SubstituteOption
import com.substichoose.model.SubstitutesResponse
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device Gemma (LiteRT-LM). Loads the first `*.litertlm` file in the app's external files
 * folder, then runs the three jobs. Every request uses a fresh conversation, and only one
 * generation runs at a time. Never throws: failures return null or set [ModelState.Failed].
 */
class GemmaService(context: Context) {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    /** Guards loading. */
    private val loadMutex = Mutex()
    /** One generation at a time. */
    private val genMutex = Mutex()

    @Volatile private var engine: Engine? = null
    @Volatile private var visionAvailable: Boolean = false
    /** Engine released by [close] while a generation was running; closed when it finishes. */
    @Volatile private var pendingClose: Engine? = null

    /** Loads the model once (thread-safe, idempotent). Returns true when Ready. */
    suspend fun ensureLoaded(): Boolean {
        if (engine != null && _state.value == ModelState.Ready) return true
        return loadMutex.withLock {
            if (engine != null && _state.value == ModelState.Ready) return@withLock true
            // A failed load is not retried on every call (each attempt can take tens of seconds).
            if (_state.value is ModelState.Failed) return@withLock false

            val dir = appContext.getExternalFilesDir(null)
            val folderPath = dir?.absolutePath
                ?: "/sdcard/Android/data/${appContext.packageName}/files"
            val modelFile = findModelFile(dir)
            if (modelFile == null) {
                _state.value = ModelState.Missing(folderPath)
                return@withLock false
            }

            _state.value = ModelState.Loading
            val loaded = withContext(Dispatchers.IO) { loadEngine(modelFile.absolutePath) }
            if (loaded != null) {
                engine = loaded
                _state.value = ModelState.Ready
                true
            } else {
                _state.value = ModelState.Failed(
                    "Could not load ${modelFile.name}: ${lastLoadError ?: "unknown error"}"
                )
                false
            }
        }
    }

    /** Job 1 from OCR text. null on failure (after one JSON retry). */
    suspend fun extractRecipe(ocrText: String): ExtractedRecipe? {
        if (ocrText.isBlank()) return null
        val userText = Prompts.extractUser(ocrText)
        return runJob(
            system = Prompts.EXTRACT_SYSTEM,
            temperature = TEMP_EXTRACT,
            maxTokens = MAX_TOKENS_EXTRACT,
            firstMessage = { it.sendMessage(userText) },
            decode = { ModelJson.decodeOrNull<ExtractedRecipe>(it) },
        )?.let(::cleanRecipe)
    }

    /** Fast Job 1 result. [named] is keyed by 0-based line index; lines Gemma judged not food are missing. */
    data class IngredientNames(
        val title: String,
        val named: Map<Int, ExtractedIngredient>,
    )

    /**
     * Fast Job 1: names already-split ingredient lines (raw = the original line), leaving out lines
     * that aren't food, and picks the title from [topLines] ("" if none). null on failure.
     */
    suspend fun extractIngredients(lines: List<String>, topLines: List<String>): IngredientNames? {
        if (lines.isEmpty()) return null
        val userText = Prompts.ingredientsUser(lines, topLines)
        val rows = runJob(
            system = Prompts.INGREDIENTS_SYSTEM,
            temperature = TEMP_EXTRACT,
            maxTokens = MAX_TOKENS_INGREDIENTS,
            firstMessage = { it.sendMessage(userText) },
            decode = { ModelJson.decodeOrNull<IngredientRows>(it) },
        ) ?: return null
        val out = mutableMapOf<Int, ExtractedIngredient>()
        for (item in rows.items) {
            if (item.size < 4) continue
            val index = (item[0].trim().trimEnd('.').toIntOrNull() ?: continue) - 1
            val name = item[3].trim()
            if (index !in lines.indices || name.isEmpty() || index in out) continue
            out[index] = ExtractedIngredient(qty = item[1].trim(), unit = item[2].trim(), name = name, raw = lines[index].trim())
        }
        if (out.isEmpty()) return null // an unusable reply, not "nothing is food"
        return IngredientNames(title = rows.title.trim(), named = out)
    }

    /**
     * Reads the photo's text with Gemma vision (much better than ML Kit on handwriting).
     * null when vision is unavailable, fails, or finds almost no text.
     */
    suspend fun readImageText(imageFile: File): String? {
        if (!imageFile.isFile) return null
        if (!ensureLoaded() || !visionAvailable) return null
        val upright = withContext(Dispatchers.IO) { ImagePrep.uprightCopy(imageFile, appContext.cacheDir) } ?: return null
        try {
            val path = upright.absolutePath
            return runJob(
                system = Prompts.READ_IMAGE_SYSTEM,
                temperature = TEMP_EXTRACT,
                maxTokens = MAX_TOKENS_READ,
                firstMessage = {
                    it.sendMessage(Contents.of(Content.ImageFile(path), Content.Text(Prompts.READ_IMAGE_USER)))
                },
                retry = null,
                decode = { reply ->
                    reply.replace(Regex("""^\s*```[a-z]*\s*|\s*```\s*$"""), "").trim()
                        .takeIf { text -> text.count { c -> c.isLetter() } >= MIN_READ_LETTERS }
                },
            )
        } finally {
            upright.delete()
        }
    }

    /** Job 1 from the photo itself (Gemma vision). null if vision unsupported or fails. */
    suspend fun extractRecipeFromImage(imageFile: File): ExtractedRecipe? {
        if (!imageFile.isFile) return null
        if (!ensureLoaded() || !visionAvailable) return null
        val path = imageFile.absolutePath
        return runJob(
            system = Prompts.EXTRACT_SYSTEM,
            temperature = TEMP_EXTRACT,
            maxTokens = MAX_TOKENS_EXTRACT,
            firstMessage = {
                it.sendMessage(Contents.of(Content.ImageFile(path), Content.Text(Prompts.EXTRACT_IMAGE_USER)))
            },
            decode = { ModelJson.decodeOrNull<ExtractedRecipe>(it) },
        )?.let(::cleanRecipe)
    }

    /** Job 2. avoidLabels = human labels of everything the user avoids (e.g. "Egg", "Coconut"). */
    suspend fun suggestSubstitutes(
        ingredient: String,
        recipeTitle: String,
        reason: SwapReason,
        avoidLabels: List<String>,
        alreadyShown: List<String> = emptyList(),
    ): SubstitutesResponse? {
        if (ingredient.isBlank()) return null
        val system = Prompts.substitutesSystem(ingredient, recipeTitle, reason, avoidLabels, alreadyShown)
        val userText = Prompts.substitutesUser(ingredient)
        return runJob(
            system = system,
            temperature = TEMP_SUBSTITUTES,
            maxTokens = MAX_TOKENS_SUBSTITUTES,
            firstMessage = { it.sendMessage(userText) },
            decode = { ModelJson.decodeSubstitutesOrNull(it, ingredient) },
        )?.let { cleanSubstitutes(it, ingredient) }
    }

    /** Job 3 (stretch). swaps = original ingredient -> chosen substitute name. */
    suspend fun adaptSteps(steps: List<String>, swaps: Map<String, String>): AdaptedSteps? {
        val cleanSteps = steps.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanSteps.isEmpty()) return null
        if (swaps.isEmpty()) return AdaptedSteps(cleanSteps.map { AdaptedStep(it, changed = false) })
        val userText = Prompts.adaptUser(cleanSteps, swaps)
        return runJob(
            system = Prompts.ADAPT_SYSTEM,
            temperature = TEMP_ADAPT,
            maxTokens = MAX_TOKENS_ADAPT,
            firstMessage = { it.sendMessage(userText) },
            decode = { ModelJson.decodeOrNull<AdaptedSteps>(it) },
        )?.let { reply ->
            val out = reply.steps.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
            if (out.isEmpty()) null else AdaptedSteps(out)
        }
    }

    /** Releases the native engine. Safe to call more than once. */
    fun close() {
        val e = synchronized(this) { engine.also { engine = null } } ?: return
        visionAvailable = false
        _state.value = ModelState.NotLoaded
        if (genMutex.tryLock()) {
            try {
                safeClose(e)
            } finally {
                genMutex.unlock()
            }
        } else {
            pendingClose = e
        }
    }

    // ---------------------------------------------------------------- internals

    @Volatile private var lastLoadError: String? = null

    private fun findModelFile(dir: File?): File? = try {
        dir?.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".litertlm", ignoreCase = true) }
            ?.minByOrNull { it.name }
    } catch (t: Throwable) {
        Log.w(TAG, "Could not list $dir", t)
        null
    }

    /**
     * Tries GPU then CPU, first with a GPU vision backend, then without vision.
     * Blocking; call on Dispatchers.IO. Returns null when every attempt fails.
     */
    private fun loadEngine(modelPath: String): Engine? {
        // (main backend on GPU?, vision backend enabled?) in order of preference.
        val attempts = listOf(true to true, false to true, true to false, false to false)
        for ((gpu, vision) in attempts) {
            val main: Backend = if (gpu) Backend.GPU() else Backend.CPU()
            val visionBackend: Backend? = if (vision) Backend.GPU() else null
            val e = tryInit(modelPath, main, visionBackend)
            if (e != null) {
                visionAvailable = vision
                Log.i(TAG, "Gemma loaded (backend=${main.name}, vision=$vision)")
                return e
            }
        }
        visionAvailable = false
        return null
    }

    private fun tryInit(modelPath: String, backend: Backend, visionBackend: Backend?): Engine? {
        var e: Engine? = null
        return try {
            val config = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                visionBackend = visionBackend,
                cacheDir = appContext.cacheDir.path,
            )
            val created = Engine(config)
            e = created
            created.initialize()
            created
        } catch (t: Throwable) {
            lastLoadError = t.message ?: t.javaClass.simpleName
            Log.w(TAG, "Engine init failed (backend=${backend.name}, vision=${visionBackend?.name})", t)
            e?.let(::safeClose)
            null
        }
    }

    /**
     * Runs one request in a fresh conversation: send, parse, retry once on bad JSON, close.
     * Returns null when the model is unavailable or anything fails.
     */
    private suspend fun <T> runJob(
        system: String,
        temperature: Double,
        maxTokens: Int,
        firstMessage: (Conversation) -> Message,
        decode: (String) -> T?,
        /** Sent once when [decode] fails; null = no retry. */
        retry: String? = Prompts.RETRY_JSON,
    ): T? {
        if (!ensureLoaded()) return null
        return genMutex.withLock {
            val eng = engine ?: return@withLock null
            try {
                withContext(Dispatchers.IO) {
                    var conversation: Conversation? = null
                    try {
                        val conv = eng.createConversation(
                            ConversationConfig(
                                systemInstruction = Contents.of(system),
                                samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = temperature),
                                // Thinking makes Gemma 4 write long hidden reasoning first; these jobs don't need it.
                                maxOutputToken = maxTokens,
                                thinkingConfig = ThinkingConfig(false),
                            )
                        )
                        conversation = conv
                        val started = System.currentTimeMillis()
                        val first = firstMessage(conv).toString()
                        Log.i(TAG, "Reply in ${System.currentTimeMillis() - started} ms, ${first.length} chars")
                        val parsed = decode(first)
                        if (parsed != null || retry == null) {
                            parsed
                        } else {
                            Log.w(TAG, "Reply was not valid JSON, retrying once: ${first.take(300)}")
                            decode(conv.sendMessage(retry).toString())
                        }
                    } finally {
                        conversation?.let(::safeClose)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Gemma request failed", t)
                null
            } finally {
                pendingClose?.let {
                    pendingClose = null
                    safeClose(it)
                }
            }
        }
    }

    private fun safeClose(c: AutoCloseable) {
        try {
            c.close()
        } catch (t: Throwable) {
            Log.w(TAG, "close() failed", t)
        }
    }

    /** Trim fields, drop rows with blank names, rebuild [ExtractedIngredient.raw] when missing. */
    private fun cleanRecipe(r: ExtractedRecipe): ExtractedRecipe {
        val ingredients = r.ingredients.mapNotNull { ing ->
            val name = ing.name.trim()
            if (name.isEmpty()) return@mapNotNull null
            val qty = ing.qty.trim()
            val unit = ing.unit.trim()
            val raw = ing.raw.trim().ifEmpty {
                listOf(qty, unit, name).filter { it.isNotEmpty() }.joinToString(" ")
            }
            ExtractedIngredient(qty = qty, unit = unit, name = name, raw = raw)
        }
        return ExtractedRecipe(
            title = r.title.trim(),
            ingredients = ingredients,
            steps = r.steps.map { it.trim() }.filter { it.isNotEmpty() },
        )
    }

    /** Trim fields, drop blank options, normalise allergen ids, force source = "ai". */
    private fun cleanSubstitutes(r: SubstitutesResponse, ingredient: String): SubstitutesResponse =
        SubstitutesResponse(
            ingredient = r.ingredient.trim().ifEmpty { ingredient.trim() },
            options = r.options.mapNotNull { o ->
                val name = o.name.trim()
                if (name.isEmpty()) return@mapNotNull null
                SubstituteOption(
                    name = name,
                    ratio = o.ratio.trim(),
                    effect = o.effect.trim(),
                    note = o.note.trim(),
                    contains = o.contains.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
                    source = "ai",
                )
            },
        )

    private companion object {
        const val TAG = "GemmaService"
        const val TEMP_EXTRACT = 0.2
        const val TEMP_SUBSTITUTES = 0.5
        const val TEMP_ADAPT = 0.3
        const val MAX_TOKENS_EXTRACT = 1024
        /** A full recipe page written out as text. */
        const val MAX_TOKENS_READ = 1024
        /** Fewer letters than this means Gemma saw no real text. */
        const val MIN_READ_LETTERS = 15
        const val MAX_TOKENS_INGREDIENTS = 700
        const val MAX_TOKENS_SUBSTITUTES = 400
        const val MAX_TOKENS_ADAPT = 768
    }
}
