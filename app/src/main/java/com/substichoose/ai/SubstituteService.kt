package com.substichoose.ai

import com.substichoose.model.SubstituteOption
import com.substichoose.safety.AllergenChecker
import com.substichoose.safety.AllergyProfile
import com.substichoose.safety.CuratedSwaps

/**
 * Substitutes for one ingredient: curated (instant) and AI (Gemma). Everything returned has
 * already been through [AllergenChecker], so the UI never shows an unsafe option.
 */
class SubstituteService(
    private val gemma: GemmaService,
    private val checker: AllergenChecker,
    private val curated: CuratedSwaps,
) {
    /** Instant, already safety-filtered. May be empty. */
    fun curated(ingredientName: String, profile: AllergyProfile): List<SubstituteOption> =
        try {
            curated.safeOptionsFor(ingredientName, checker, profile)
        } catch (t: Throwable) {
            emptyList()
        }

    /** Gemma's options, ALWAYS passed through checker.safeOptions. null if Gemma unavailable or failed. */
    suspend fun fromAi(
        ingredientName: String,
        recipeTitle: String,
        reason: SwapReason,
        profile: AllergyProfile,
        alreadyShown: List<String> = emptyList(),
    ): List<SubstituteOption>? {
        val response = gemma.suggestSubstitutes(ingredientName, recipeTitle, reason, avoidLabels(profile), alreadyShown)
            ?: return null
        return checker.safeOptions(response.options, profile).map { it.copy(source = "ai") }
    }

    /** Human labels for a profile: checker.labels for ids + custom terms as typed. */
    fun avoidLabels(profile: AllergyProfile): List<String> =
        (profile.allergenIds.map { id -> checker.labels[id] ?: id } +
            profile.customTerms.map { it.trim() }.filter { it.isNotEmpty() })
            .distinct()
}
