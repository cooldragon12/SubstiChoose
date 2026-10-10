package com.substichoose.pipeline

import com.substichoose.data.IngredientLineParser
import com.substichoose.model.ExtractedIngredient
import com.substichoose.model.ExtractedRecipe

/**
 * Last-resort recipe parser for raw OCR text, used when Gemma is missing or fails, so the demo
 * never dead-ends. Plain rules, no model:
 *  - title = a short name-like line above the ingredient list, or "" when there is none
 *  - lines that are clearly not ingredients (servings, times, links ...) are left out
 *  - lines under an "Ingredients" / "Mga sangkap" heading are ingredients (until a steps heading)
 *  - lines under "Steps" / "Instructions" / "Directions" / "Method" / "Procedure" / "Paraan" are steps
 *  - without headings: bullet- or quantity-led short lines are ingredients, numbered or long lines are steps
 */
internal object OfflineRecipeParser {

    private const val MAX_TOP_LINES = 5

    private enum class Section { NONE, INGREDIENTS, STEPS, OTHER }

    private val ingredientHeadings = setOf(
        "ingredients", "ingredient", "ingredient list", "you will need", "you ll need",
        "mga sangkap", "sangkap", "mga kailangan",
    )

    private val stepHeadings = setOf(
        "steps", "step", "instructions", "instruction", "directions", "direction",
        "method", "procedure", "procedures", "preparation", "how to cook", "how to make",
        "paraan", "paraan ng pagluluto", "mga hakbang", "hakbang", "pamamaraan", "paano lutuin",
    )

    /** Other headings that end a section ("Notes", "Nutrition" ...). */
    private val otherHeadings = setOf(
        "notes", "note", "tips", "nutrition", "nutrition facts", "serves", "servings", "yield",
    )

    /** "1." / "2)" / "Step 3:" at the start of a step line. */
    private val stepNumber = Regex("""^\s*(?:step\s*)?\d{1,2}\s*[.):-](?!\d)\s*""", RegexOption.IGNORE_CASE)

    private val sentenceEnd = Regex("""[.!?)]\s*$""")

    fun parse(ocrText: String): ExtractedRecipe {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return ExtractedRecipe()

        val titleIndex = titleIndex(lines)
        val title = if (titleIndex >= 0) lines[titleIndex] else ""
        val hasIngredientHeading = lines.any { headingOf(it) == Section.INGREDIENTS }

        val ingredients = mutableListOf<ExtractedIngredient>()
        val steps = mutableListOf<String>()
        var section = Section.NONE

        lines.forEachIndexed { index, line ->
            if (index == titleIndex) return@forEachIndexed
            val heading = headingOf(line)
            if (heading != null) {
                section = heading
                // "Ingredients: 2 eggs" — keep anything written after the colon.
                val inline = line.substringAfter(':', "").trim()
                if (inline.isEmpty()) return@forEachIndexed
                handleLine(inline, section, hasIngredientHeading, ingredients, steps)
                return@forEachIndexed
            }
            handleLine(line, section, hasIngredientHeading, ingredients, steps)
        }

        return ExtractedRecipe(
            title = title,
            ingredients = ingredients,
            steps = steps,
        )
    }

    private fun handleLine(
        line: String,
        section: Section,
        hasIngredientHeading: Boolean,
        ingredients: MutableList<ExtractedIngredient>,
        steps: MutableList<String>,
    ) {
        when (section) {
            Section.INGREDIENTS -> {
                // A numbered sentence right under the ingredient list is usually step 1 without a heading.
                if (looksLikeStep(line)) addStep(line, steps)
                else addIngredient(line, ingredients)
            }
            Section.STEPS -> addStep(line, steps)
            Section.OTHER -> Unit
            Section.NONE -> {
                if (hasIngredientHeading) return // intro text before the list
                when {
                    looksLikeIngredient(line) -> addIngredient(line, ingredients)
                    looksLikeStep(line) || steps.isNotEmpty() -> addStep(line, steps)
                }
            }
        }
    }

    private fun addIngredient(line: String, ingredients: MutableList<ExtractedIngredient>) {
        if (IngredientFilter.isNotIngredient(line)) return
        IngredientLineParser.parse(line)?.let { ingredients += it }
    }

    /**
     * The lines above the first heading or ingredient-like line (at most [MAX_TOP_LINES]): where a
     * recipe's name is written. Gemma picks the title from these.
     */
    fun topLines(ocrText: String): List<String> =
        topOf(ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() })

    private fun topOf(lines: List<String>): List<String> =
        lines.takeWhile { headingOf(it) == null && !looksLikeIngredient(it) }.take(MAX_TOP_LINES)

    /** Index of the first top line that reads like a dish name, or -1 (the title stays blank). */
    private fun titleIndex(lines: List<String>): Int = topOf(lines).indexOfFirst(::looksLikeTitle)

    /** "Banana Bread", "Lola's Chicken Adobo": short, mostly letters, not a sentence or metadata. */
    private fun looksLikeTitle(line: String): Boolean {
        if (line.length !in 3..60) return false
        if (line.count { it.isLetter() } < line.length / 2) return false
        if (line.split(' ').count { it.isNotBlank() } > 8) return false
        if (line.endsWith('.') || line.endsWith(':')) return false
        return !stepNumber.containsMatchIn(line) && !IngredientFilter.isNotIngredient(line)
    }

    /** Short line led by a bullet, a quantity or a unit word ("pinch of salt"). */
    private fun looksLikeIngredient(line: String): Boolean {
        if (line.length > 70 || stepNumber.containsMatchIn(line) && line.length > 40) return false
        return IngredientLineParser.hasMarker(line) && !stepNumber.containsMatchIn(line) ||
            IngredientLineParser.startsWithQty(line) ||
            (line.split(' ').size <= 6 && IngredientLineParser.hasUnit(line))
    }

    /** "1. Preheat the oven ..." or a long sentence. */
    private fun looksLikeStep(line: String): Boolean {
        val numbered = stepNumber.find(line)
        if (numbered != null) {
            val text = line.substring(numbered.range.last + 1)
            if (IngredientLineParser.startsWithQty(text)) return false
            // "1. 2 cups flour" style numbering of ingredients is rare; a verb-led sentence is a step.
            return text.length > 25 || text.split(' ').size >= 5
        }
        return line.length > 70 && line.count { it == ' ' } >= 8
    }

    /** Adds a step, or joins it to the previous one when OCR broke a sentence across lines. */
    private fun addStep(line: String, steps: MutableList<String>) {
        val numbered = stepNumber.containsMatchIn(line)
        val text = line.replaceFirst(stepNumber, "").let { IngredientLineParser.stripMarker(it) }.trim()
        if (text.isEmpty()) return
        val last = steps.lastOrNull()
        if (!numbered && last != null && !sentenceEnd.containsMatchIn(last) && text.first().isLowerCase()) {
            steps[steps.lastIndex] = "$last $text"
        } else {
            steps += text
        }
    }

    /** Which section a heading line opens, or null if the line is not a heading. */
    private fun headingOf(line: String): Section? {
        if (line.length > 40) return null
        val head = line.substringBefore(':')
        val norm = head.lowercase()
            .replace(Regex("""[^\p{L}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (norm.isEmpty()) return null
        return when {
            norm in ingredientHeadings -> Section.INGREDIENTS
            norm in stepHeadings -> Section.STEPS
            // "Serves 4" or "Yield: 12" is metadata on one line, not a heading that starts a section.
            norm in otherHeadings && line.none { it.isDigit() } -> Section.OTHER
            // "Ingredients for the sauce", "Instructions (serves 4)"
            norm.startsWith("ingredients ") && norm.split(' ').size <= 4 -> Section.INGREDIENTS
            norm.startsWith("mga sangkap ") && norm.split(' ').size <= 5 -> Section.INGREDIENTS
            stepHeadings.any { norm.startsWith("$it ") } && norm.split(' ').size <= 4 && !head.any { it.isDigit() } -> Section.STEPS
            else -> null
        }
    }
}
