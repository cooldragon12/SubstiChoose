package com.substichoose.ai

/** All prompt text sent to Gemma. JSON shapes match docs/SCHEMAS.md and model/Models.kt. */
internal object Prompts {

    /** Max OCR characters sent to the model, so long pages don't overflow the context window. */
    const val MAX_OCR_CHARS = 6000

    const val RETRY_JSON = "Your last reply was not valid JSON. Reply with the JSON only."

    private const val ALLERGEN_IDS = "peanut, treenuts, dairy, egg, gluten, soy, fish, shellfish, sesame"

    /** Job 1 system prompt (text and image input). */
    val EXTRACT_SYSTEM = """
        You are a cooking assistant. Read the recipe and reply with JSON only, no other words.
        Use exactly this shape:
        {"title": "...", "ingredients": [{"qty": "...", "unit": "...", "name": "...", "raw": "..."}], "steps": ["..."]}
        Use "" when a qty or unit is missing. "name" is the ingredient alone, without amounts or preparation.
        "raw" is the ingredient line as it was written. "steps" are the instructions in order, one per item.
        Only list real food ingredients. Leave out servings, cooking times, notes, nutrition facts,
        headings and any other text.
        "title" is the dish name exactly as written. If no dish name is written, use "". Never make one up.
    """.trimIndent()

    /** Job 1 user message for OCR text. */
    fun extractUser(ocrText: String): String {
        val text = ocrText.trim().take(MAX_OCR_CHARS)
        return "Recipe text (from OCR, may contain errors):\n\"\"\"\n$text\n\"\"\"\nReply with the JSON now."
    }

    /**
     * Fast Job 1: the ingredient lines are already split out; Gemma names them, flags lines that
     * aren't food (OCR picks up servings, notes and page text) and picks the title from the top lines.
     */
    val INGREDIENTS_SYSTEM = """
        You are a cooking assistant. You get the top lines of a recipe page and numbered lines that
        may be ingredients. The text comes from a camera and may include other text.
        For each line that is a real food ingredient give [number, qty, unit, name]. "name" is the
        ingredient alone, without amounts or preparation. Use "" when a qty or unit is missing.
        Leave out every line that is not a food ingredient (servings, times, notes, greetings,
        headings, brand, page or website text).
        "title" is the dish name copied from the top lines. If none of them is a dish name, use "".
        Never make up a title. Reply with JSON only, in exactly this shape:
        {"title": "...", "items": [["1", "2", "cup", "flour"]]}
    """.trimIndent()

    /** Fast Job 1 user message: the page's top lines, then the candidate lines numbered from 1. */
    fun ingredientsUser(lines: List<String>, topLines: List<String>): String {
        val top = topLines.joinToString("\n") { it.trim() }.ifEmpty { "(none)" }
        val numbered = lines.mapIndexed { i, line -> "${i + 1}. ${line.trim()}" }.joinToString("\n")
        return "Top lines:\n$top\n\nLines:\n$numbered\nReply with the JSON now.".take(MAX_OCR_CHARS)
    }

    /** Reading a photo (printed or handwritten) into plain text, in place of ML Kit OCR. */
    val READ_IMAGE_SYSTEM = """
        You read photos of recipes, including handwritten ones. Write out the text in the photo
        line by line, top to bottom, exactly as written. Keep every amount, fraction and unit.
        Put each ingredient on its own line. Don't add, explain or fix anything. If a word is hard
        to read, write your best guess. Reply with the text only.
    """.trimIndent()

    const val READ_IMAGE_USER = "Write out the text in this photo."

    /** Job 1 user text that accompanies the photo. */
    const val EXTRACT_IMAGE_USER =
        "This photo shows a recipe. Read it and reply with the JSON only, in the shape described."

    /** Job 2 system prompt. */
    fun substitutesSystem(
        ingredient: String,
        recipeTitle: String,
        reason: SwapReason,
        avoidLabels: List<String>,
        alreadyShown: List<String> = emptyList(),
    ): String {
        val reasonText = when (reason) {
            SwapReason.ALLERGY -> "the user is allergic to it"
            SwapReason.MISSING -> "the user doesn't have it"
        }
        val avoid = avoidLabels.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val avoidLine = if (avoid.isEmpty()) {
            "The user has no listed allergies."
        } else {
            "The user must avoid: ${avoid.joinToString(", ")}. Never suggest anything containing them."
        }
        val title = recipeTitle.trim().ifEmpty { "this recipe" }
        val shown = alreadyShown.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val shownLine = if (shown.isEmpty()) "" else "The user already sees these swaps, so suggest different ones: ${shown.joinToString(", ")}."
        return """
            You are a cooking assistant. Suggest 2 or 3 substitutes for "${ingredient.trim()}" in "$title".
            Reason: $reasonText.
            $avoidLine
            $shownLine
            Only suggest common ingredients sold in ordinary grocery stores. Never invent product names.
            Reply with JSON only, in exactly this shape:
            {"ingredient": "...", "options": [{"name": "...", "ratio": "<original amount> = <substitute amount>", "effect": "...", "note": "...", "contains": []}]}
            "contains" lists any of these ids the substitute contains: $ALLERGEN_IDS.
            Keep "effect" and "note" under 12 words each.
        """.trimIndent()
    }

    /** Job 2 user message. */
    fun substitutesUser(ingredient: String): String =
        "Ingredient: ${ingredient.trim()}. Reply with the JSON now."

    /** Job 3 system prompt (stretch). */
    val ADAPT_SYSTEM = """
        You are a cooking assistant. Rewrite recipe steps so they use the substitutes given.
        Keep every step, in the same order. Change only what the substitutes require
        (for example, how to prepare a flax egg). Reply with JSON only, in exactly this shape:
        {"steps": [{"text": "...", "changed": true}]}
        "changed" is true only for steps you rewrote.
    """.trimIndent()

    /** Job 3 user message. */
    fun adaptUser(steps: List<String>, swaps: Map<String, String>): String {
        val swapLines = swaps.entries.joinToString("\n") { (orig, sub) -> "- ${orig.trim()} -> ${sub.trim()}" }
        val stepLines = steps.mapIndexed { i, s -> "${i + 1}. ${s.trim()}" }.joinToString("\n")
        return "Substitutes (original -> replacement):\n$swapLines\n\nSteps:\n$stepLines\n\nReply with the JSON now."
            .take(MAX_OCR_CHARS * 2)
    }
}
