package com.substichoose.safety

import android.content.Context
import com.substichoose.model.SubstituteOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CuratedSwap(
    val ingredient: String,
    val match: List<String>,
    val options: List<SubstituteOption>,
)

@Serializable
data class CuratedSwapBook(
    val version: Int,
    val swaps: List<CuratedSwap>,
)

/**
 * The hand-checked swaps in substitutes.json. Answers instantly, so the UI can show these
 * while Gemma is thinking, and falls back to them if Gemma fails or times out.
 */
class CuratedSwaps(book: CuratedSwapBook) {

    // Longest match term first, so "peanut butter" wins over "butter" and "fish sauce" over "fish".
    private val terms: List<Pair<Regex, CuratedSwap>> = book.swaps
        .flatMap { swap ->
            swap.match.map { term -> term to swap.copy(options = swap.options.map { it.copy(source = "curated") }) }
        }
        .sortedByDescending { AllergenChecker.normalize(it.first).length }
        .mapNotNull { (term, swap) -> AllergenChecker.phraseRegex(listOf(term))?.let { it to swap } }

    /** The swap entry for an ingredient name such as "1/2 cup melted butter", or null. */
    fun find(ingredientName: String): CuratedSwap? {
        val norm = AllergenChecker.normalize(ingredientName)
        return terms.firstOrNull { (regex, _) -> regex.containsMatchIn(norm) }?.second
    }

    /** Curated options for this ingredient with anything unsafe for the user already removed. */
    fun safeOptionsFor(
        ingredientName: String,
        checker: AllergenChecker,
        profile: AllergyProfile,
    ): List<SubstituteOption> =
        find(ingredientName)?.let { checker.safeOptions(it.options, profile) } ?: emptyList()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): CuratedSwaps =
            CuratedSwaps(json.decodeFromString(CuratedSwapBook.serializer(), text))

        fun fromAssets(context: Context, fileName: String = "substitutes.json"): CuratedSwaps =
            fromJson(context.assets.open(fileName).bufferedReader().use { it.readText() })
    }
}
