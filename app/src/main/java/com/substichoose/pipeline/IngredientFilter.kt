package com.substichoose.pipeline

import com.substichoose.data.IngredientLineParser

/**
 * Rules for text that OCR picks up near an ingredient list but that is not an ingredient:
 * servings, times, nutrition, sub-headings, page furniture. Deliberately narrow: a real
 * ingredient must never be dropped, so anything unclear is kept (Gemma gets the final say
 * in the scan pipeline, and the user can delete rows on the Review screen).
 */
internal object IngredientFilter {

    /** Line openings that only ever start recipe metadata. Matched as whole words ("note-free" is not "note"). */
    private val metaStarts = listOf(
        "serves", "serving size", "servings", "yield", "yields", "makes", "portions",
        "prep time", "preparation time", "cook time", "cooking time", "total time", "bake time", "ready in",
        "active time", "calories", "kcal", "nutrition", "per serving", "difficulty", "level", "rating",
        "reviews", "page", "copyright", "recipe by", "recipe from", "adapted from", "source", "photo",
        "print", "save recipe", "jump to", "share", "notes", "note", "tip", "tips", "course", "cuisine",
        "keyword", "author", "posted", "updated",
    ).map { Regex("""^${Regex.escape(it)}(?=[\s:.,]|$)""") }

    /** "4 servings", "30 minutes", "1 hr 15 mins", "350°F" — a number with only time/serving words. */
    private val metaOnly = Regex(
        """^[\d\s./\-–]+(?:servings?|portions?|people|persons?|pax|minutes?|mins?|hours?|hrs?|h|m|°\s*[cf]|degrees?)\b[\s\d.\-–a-z°]*$"""
    )

    private val web = Regex("""https?://|www\.|\.com\b|\.ph\b|\.net\b|\.org\b|@\w""")

    /** True when [line] is clearly not an ingredient. */
    fun isNotIngredient(line: String): Boolean {
        val text = IngredientLineParser.stripMarker(line).trim()
        val lower = text.lowercase()
        if (lower.count { it.isLetter() } < 2) return true // "12", "---", "©"
        if (web.containsMatchIn(lower)) return true
        if (lower.endsWith(":")) return true // "For the sauce:" sub-heading
        if (metaStarts.any { it.containsMatchIn(lower) }) return true
        if (metaOnly.matches(lower)) return true
        // A long sentence without an amount is a step or intro text, not an ingredient line.
        val words = text.split(Regex("""\s+""")).size
        if (words > 14 && !IngredientLineParser.startsWithQty(text)) return true
        return false
    }
}
