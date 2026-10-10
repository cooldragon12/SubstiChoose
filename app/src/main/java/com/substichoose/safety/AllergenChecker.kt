package com.substichoose.safety

import android.content.Context
import com.substichoose.model.SubstituteOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AllergenEntry(
    val id: String,
    val label: String,
    val keywords: List<String>,
    val exclude: List<String> = emptyList(),
)

@Serializable
data class AllergenDictionary(
    val version: Int,
    val allergens: List<AllergenEntry>,
)

/** What the user avoids: ids from allergens.json (e.g. "egg") plus items they typed (e.g. "Coconut"). */
data class AllergyProfile(
    val allergenIds: Set<String>,
    val customTerms: Set<String> = emptySet(),
)

/**
 * The app's safety net. Plain Kotlin rules, never the model, decide what gets flagged:
 *  - flag ingredient rows on the Review screen ([conflicts])
 *  - drop any substitute (AI or curated) that hits the user's allergies ([safeOptions])
 *
 * Matching: lower-case, punctuation to spaces, whole words, a trailing "s"/"es" also matches.
 * Each allergen's "exclude" phrases are removed first, so "peanut butter" is not dairy
 * and "eggplant" is not egg.
 */
class AllergenChecker(dictionary: AllergenDictionary) {

    private class Compiled(val id: String, val keywords: Regex?, val exclude: Regex?)

    private val compiled = dictionary.allergens.map {
        Compiled(it.id, phraseRegex(it.keywords), phraseRegex(it.exclude))
    }

    // Synchronized: Home, Review and Recipe view models call conflicts() from Dispatchers.Default concurrently.
    private val customCache: MutableMap<String, Regex?> = java.util.Collections.synchronizedMap(HashMap())

    /** id -> label, e.g. "dairy" -> "Milk / dairy", for chips and badges. */
    val labels: Map<String, String> = dictionary.allergens.associate { it.id to it.label }

    /** Every allergen id found in [text], whatever the user's profile. */
    fun allergensIn(text: String): Set<String> {
        val norm = normalize(text)
        return compiled.filter { c ->
            val keywords = c.keywords ?: return@filter false
            val cleaned = c.exclude?.replace(norm, " ") ?: norm
            keywords.containsMatchIn(cleaned)
        }.mapTo(LinkedHashSet()) { it.id }
    }

    /** The user's allergies that [text] hits: allergen ids, plus custom terms as the user typed them. */
    fun conflicts(text: String, profile: AllergyProfile): Set<String> {
        val hits = allergensIn(text).filterTo(LinkedHashSet()) { it in profile.allergenIds }
        val norm = normalize(text)
        for (term in profile.customTerms) {
            val regex = customCache.getOrPut(term) { phraseRegex(listOf(term)) } ?: continue
            if (regex.containsMatchIn(norm)) hits += term
        }
        return hits
    }

    /**
     * Safe when its declared [SubstituteOption.contains] and its own words both stay clear of the
     * user's allergies. Only the right side of "=" in the ratio is checked, because the left side
     * names the original ingredient ("1 egg = 1 tbsp ground flaxseed + 3 tbsp water").
     */
    fun isSafe(option: SubstituteOption, profile: AllergyProfile): Boolean {
        if (option.contains.any { it in profile.allergenIds }) return false
        val replacementText = option.name + " " + option.ratio.substringAfter('=', option.ratio)
        return conflicts(replacementText, profile).isEmpty()
    }

    fun safeOptions(options: List<SubstituteOption>, profile: AllergyProfile): List<SubstituteOption> =
        options.filter { isSafe(it, profile) }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): AllergenChecker =
            AllergenChecker(json.decodeFromString(AllergenDictionary.serializer(), text))

        fun fromAssets(context: Context, fileName: String = "allergens.json"): AllergenChecker =
            fromJson(context.assets.open(fileName).bufferedReader().use { it.readText() })

        private val nonWord = Regex("[^\\p{L}\\p{N}]+")

        /** " all purpose flour " style: lower case, single spaces, padded. */
        internal fun normalize(text: String): String =
            " " + text.lowercase().replace(nonWord, " ").trim() + " "

        /** One regex for a list of phrases, longest first, whole words, optional plural. */
        internal fun phraseRegex(phrases: List<String>): Regex? {
            val parts = phrases.map { normalize(it).trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sortedByDescending { it.length }
            if (parts.isEmpty()) return null
            val alternatives = parts.joinToString("|") { Regex.escape(it) }
            return Regex("(?<![\\p{L}\\p{N}])(?:$alternatives)(?:s|es)?(?![\\p{L}\\p{N}])")
        }
    }
}
