package com.substichoose.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Shared JSON shapes. Gemma's replies, the curated swaps table and the UI all use these,
// so every teammate codes to the same fields. See docs/SCHEMAS.md for examples and prompt text.

/** Job 1 reply: the recipe Gemma pulled out of the OCR text. */
@Serializable
data class ExtractedRecipe(
    val title: String = "",
    val ingredients: List<ExtractedIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
)

/** One ingredient row. qty stays a string so "1 1/2" and "a pinch" survive. */
@Serializable
data class ExtractedIngredient(
    val qty: String = "",
    val unit: String = "",
    val name: String,
    val raw: String = "",
)

/**
 * Fast Job 1 reply: one row per numbered ingredient line, as [line number, qty, unit, name].
 * Much shorter than [ExtractedRecipe], so Gemma answers several times faster.
 * [title] is the dish name copied from the page's top lines, or "". Lines Gemma judged not to be
 * food (servings, notes, page text) are left out of [items].
 */
@Serializable
data class IngredientRows(
    val title: String = "",
    val items: List<List<String>> = emptyList(),
)

/** Job 2 reply: substitutes for one ingredient. */
@Serializable
data class SubstitutesResponse(
    val ingredient: String = "",
    val options: List<SubstituteOption> = emptyList(),
)

/**
 * One substitute. In [ratio] the original ingredient goes on the left of "=".
 * [contains] uses allergen ids from allergens.json. [source] is "ai" or "curated".
 */
@Serializable
data class SubstituteOption(
    val name: String,
    val ratio: String = "",
    val effect: String = "",
    val note: String = "",
    val contains: List<String> = emptyList(),
    val source: String = "ai",
)

/** Job 3 reply (stretch goal): rewritten steps, changed lines marked. */
@Serializable
data class AdaptedSteps(
    val steps: List<AdaptedStep> = emptyList(),
)

@Serializable
data class AdaptedStep(
    val text: String,
    val changed: Boolean = false,
)

/** Parsing helpers for model replies, which may wrap JSON in ```json fences or add chatter. */
object ModelJson {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** The first "{" to the last "}" of a reply, or null if there is no JSON object. */
    fun extractObject(reply: String): String? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        return if (start >= 0 && end > start) reply.substring(start, end + 1) else null
    }

    /** The first "[" to the last "]" of a reply, or null if there is no JSON array. */
    fun extractArray(reply: String): String? {
        val start = reply.indexOf('[')
        val end = reply.lastIndexOf(']')
        return if (start >= 0 && end > start) reply.substring(start, end + 1) else null
    }

    /** Decodes a reply that is a JSON array, or returns null. */
    inline fun <reified T> decodeListOrNull(reply: String): List<T>? =
        extractArray(reply)?.let { runCatching { json.decodeFromString<List<T>>(it) }.getOrNull() }

    /**
     * Job 2 replies in any shape Gemma uses: the requested object, a list of such objects
     * (one per suggestion), or a bare list of options. null if none fits.
     */
    fun decodeSubstitutesOrNull(reply: String, ingredient: String): SubstitutesResponse? =
        decodeOrNull<SubstitutesResponse>(reply)
            ?: decodeListOrNull<SubstitutesResponse>(reply)
                ?.flatMap { it.options }
                ?.takeIf { it.isNotEmpty() }
                ?.let { SubstitutesResponse(ingredient, it) }
            ?: decodeListOrNull<SubstituteOption>(reply)
                ?.takeIf { it.isNotEmpty() }
                ?.let { SubstitutesResponse(ingredient, it) }

    /** Decodes a reply, or returns null so the caller can retry once ("Your last reply was not valid JSON"). */
    inline fun <reified T> decodeOrNull(reply: String): T? =
        extractObject(reply)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }
}
