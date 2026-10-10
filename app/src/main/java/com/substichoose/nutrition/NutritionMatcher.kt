package com.substichoose.nutrition

/**
 * Picks the USDA food that best matches a plain recipe ingredient, or none. USDA's own ranking often
 * puts a processed form first ("egg" -> egg white, "bananas" -> banana powder), and a loose match is
 * worse than no match ("whole peppercorns" -> a whole egg). So:
 *  - every key word of the ingredient must appear in the USDA name ("baking soda" needs both words);
 *    preparation/descriptor words ("diced", "white", "unsalted") and notes in brackets are optional;
 *  - among those, prefer the ingredient as the leading word, raw/whole forms and short names, and
 *    avoid dried, powdered, prepared or partial forms unless the ingredient itself says so.
 */
internal object NutritionMatcher {

    private val preferred = mapOf("raw" to 8, "whole" to 10)

    private val avoided = setOf(
        "dried", "dehydrated", "powder", "babyfood", "chip", "snack", "substitute", "cooked", "fried",
        "frozen", "mix", "dessert", "pudding", "juice", "skin", "white", "yolk", "clarified", "ghee",
        "toasted", "bread", "bagel", "cracker", "roll", "salad", "canned", "mcdonald", "restaurant", "sauce", "cube", "dry",
    )

    /** Words that describe or prepare an ingredient but don't identify it. Scored, never required. */
    private val optional = setOf(
        "diced", "chopped", "melted", "minced", "sliced", "crushed", "beaten", "mashed", "fresh", "large",
        "small", "medium", "ripe", "whole", "ground", "white", "cooking", "other", "or", "the", "for", "and",
        "of", "with", "unsalted", "salted", "plain", "to", "taste", "finely", "thinly", "peeled", "cut",
        "into", "piece", "optional", "packed", "light", "dark", "raw", "a", "smoked",
    )

    fun words(text: String): List<String> =
        text.lowercase().split(Regex("""[^\p{L}]+""")).filter { it.isNotEmpty() }.map(::singular)

    /** Crude singular so "bananas"/"banana", "eggs"/"egg" and "leaves"/"leaf" match. */
    private fun singular(w: String): String = when {
        w.length > 5 && (w.endsWith("eaves") || w.endsWith("lves")) -> w.dropLast(3) + "f"
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    /** "flax" matches "flaxseed"; "pepper" does not match "peppercorn". */
    private fun matches(queryWord: String, descWord: String): Boolean =
        descWord == queryWord || (queryWord.length >= 4 && descWord.startsWith(queryWord))

    /**
     * The ingredient's words, without notes in brackets ("white sugar (for the caramel)") and
     * without alternatives ("spaghetti or other pasta" -> "spaghetti").
     */
    private fun queryWords(query: String): List<String> =
        words(query.replace(Regex("""\([^)]*\)"""), " ").substringBefore(" or "))

    /** Branded/restaurant items ("HORMEL ALWAYS TENDER, ...", "TACO BELL, Nachos") aren't plain ingredients. */
    private fun isBranded(description: String): Boolean =
        description.trim().split(' ', ',').firstOrNull()?.let { w -> w.length >= 3 && w.all { it.isUpperCase() || it == '\'' } } == true

    /** null when the candidate is missing a key word of the ingredient. */
    fun score(query: String, description: String): Int? {
        val q = queryWords(query).toSet()
        val d = words(description)
        if (q.isEmpty() || d.isEmpty() || isBranded(description)) return null
        val required = (q - optional).ifEmpty { q }
        if (!required.all { r -> d.any { matches(r, it) } }) return null
        // "Chicken, canned, no broth" does not contain broth.
        val negated = d.zipWithNext().filter { (a, _) -> a == "no" || a == "without" }.map { it.second }
        if (required.any { r -> negated.any { matches(r, it) } }) return null

        var score = 0
        val head = words(description.substringBefore(','))
        if (head.firstOrNull()?.let { h -> q.any { matches(it, h) } } == true) score += 30
        // Extra nouns at the very start mean a different food ("Sugar-apples" for "sugar").
        score -= 15 * head.take(2).count { h -> q.none { matches(it, h) } && h !in optional }
        // The ingredient as a phrase ("Soup, chicken broth, ..."), not just its words scattered about.
        val phrase = queryWords(query).filter { it in required }
        if (phrase.size >= 2 && d.windowed(phrase.size).any { win -> win.indices.all { matches(phrase[it], win[it]) } }) score += 25
        score += 5 * q.count { w -> d.any { matches(w, it) } }
        score -= 15 * q.count { w -> d.none { matches(w, it) } } // "egg white" must not settle for a whole egg
        preferred.forEach { (w, points) -> if (w in d) score += points }
        score -= 25 * d.toSet().count { it in avoided && it !in q }
        score -= d.size
        return score
    }

    /** Below this a candidate only shares words with the ingredient ("peppercorn dressing" for peppercorns). */
    private const val MIN_SCORE = -40

    /** Indices of acceptable candidates, best first. Ties keep USDA's order. */
    fun ranked(query: String, descriptions: List<String>): List<Int> =
        descriptions.indices
            .mapNotNull { i -> score(query, descriptions[i])?.takeIf { it >= MIN_SCORE }?.let { i to it } }
            .sortedByDescending { it.second }
            .map { it.first }

    /** Index of the best acceptable candidate, or -1 if none qualifies. */
    fun best(query: String, descriptions: List<String>): Int = ranked(query, descriptions).firstOrNull() ?: -1
}
