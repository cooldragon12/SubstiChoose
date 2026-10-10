package com.substichoose.data

import com.substichoose.model.ExtractedIngredient

/**
 * Splits one written ingredient line into qty / unit / name, e.g.
 * "1 1/2 cups all-purpose flour" -> ("1 1/2", "cup", "all-purpose flour").
 *
 * Shared by [RecipeRepository.addIngredient] and the offline fallback parser in the scan pipeline.
 * Deliberately conservative: parentheses stay in the name ("bagoong alamang (shrimp paste)") so the
 * allergen checker never loses a word; only a trailing ", chopped"-style clause is dropped.
 */
internal object IngredientLineParser {

    /** Written unit (lower-case, no trailing dot) -> canonical unit stored in the app. */
    private val units: Map<String, String> = mapOf(
        "cup" to "cup", "cups" to "cup", "c" to "cup",
        "tbsp" to "tbsp", "tbsps" to "tbsp", "tbs" to "tbsp", "tablespoon" to "tbsp", "tablespoons" to "tbsp",
        "tsp" to "tsp", "tsps" to "tsp", "teaspoon" to "tsp", "teaspoons" to "tsp",
        "g" to "g", "gram" to "g", "grams" to "g", "gr" to "g",
        "kg" to "kg", "kilo" to "kg", "kilos" to "kg", "kilogram" to "kg", "kilograms" to "kg",
        "ml" to "ml", "milliliter" to "ml", "milliliters" to "ml", "millilitre" to "ml", "millilitres" to "ml",
        "l" to "l", "liter" to "l", "liters" to "l", "litre" to "l", "litres" to "l",
        "oz" to "oz", "ounce" to "oz", "ounces" to "oz",
        "lb" to "lb", "lbs" to "lb", "pound" to "lb", "pounds" to "lb",
        "pc" to "pcs", "pcs" to "pcs", "piece" to "pcs", "pieces" to "pcs",
        "clove" to "clove", "cloves" to "clove",
        "can" to "can", "cans" to "can",
        "pinch" to "pinch", "pinches" to "pinch",
    )

    private const val UNICODE_FRACTIONS = "½¼¾⅓⅔⅛⅜⅝⅞"

    /** Bullets ("-", "•", "*") or list numbering ("1." / "2)") at the start of a line. */
    private val leadingMarker = Regex("""^\s*(?:[-–—•*·▪●◦○>]+|\d{1,2}[.)])\s+""")

    /**
     * Quantity at the start: "1 1/2", "1½", "1/2", "1.5", "2-3", "2 to 3", "½".
     * Must be followed by whitespace, a letter or "(" so "1/2cup" and "500g" also split.
     */
    private val qtyRegex = Regex(
        """^(\d+\s+\d+/\d+|\d+\s*[$UNICODE_FRACTIONS]|\d+/\d+|\d+(?:[.,]\d+)?(?:\s*(?:-|–|to)\s*\d+(?:[.,]\d+)?)?|[$UNICODE_FRACTIONS])(?=\s|$|[A-Za-z(])"""
    )

    /** First word of what follows the qty, optionally ending in "." ("tbsp."). */
    private val unitRegex = Regex("""^([A-Za-z]+)\.?(?=\s|$|\()""")

    /** A size note right after the unit, e.g. the "(390 g)" in "1 can (390 g) condensed milk". */
    private val sizeNote = Regex("""^\([^)]*\d[^)]*\)\s*""")

    private val optionalNote = Regex("""\(\s*optional\s*\)""", RegexOption.IGNORE_CASE)

    /** Removes a bullet or "1." numbering prefix. */
    fun stripMarker(line: String): String = line.replaceFirst(leadingMarker, "").trim()

    /** True when the line starts with a bullet or list number. */
    fun hasMarker(line: String): Boolean = leadingMarker.containsMatchIn(line)

    /** True when the line starts with a quantity ("2 cups ...", "½ tsp ..."). */
    fun startsWithQty(line: String): Boolean = qtyRegex.containsMatchIn(stripMarker(line))

    /** True when the line starts with a known unit word after its qty, or with a bare unit ("pinch of salt"). */
    fun hasUnit(line: String): Boolean = parse(line)?.unit?.isNotEmpty() == true

    /** Parses one line, or returns null for a blank line. [ExtractedIngredient.raw] keeps the line as written. */
    fun parse(line: String): ExtractedIngredient? {
        val raw = line.trim()
        if (raw.isEmpty()) return null
        var rest = stripMarker(raw)

        var qty = ""
        qtyRegex.find(rest)?.let { m ->
            qty = m.groupValues[1].replace(Regex("""\s+"""), " ").trim()
            rest = rest.substring(m.range.last + 1).trim()
        }

        var unit = ""
        unitRegex.find(rest)?.let { m ->
            val canonical = units[m.groupValues[1].lowercase()]
            // "c" and "l" are only units when a qty came first ("1 c sugar"), never as the first word of a name.
            val ambiguous = m.groupValues[1].length == 1 && qty.isEmpty()
            if (canonical != null && !ambiguous) {
                unit = canonical
                rest = rest.substring(m.range.last + 1).trim()
            }
        }

        if (unit.isNotEmpty() || qty.isNotEmpty()) {
            rest = rest.replaceFirst(sizeNote, "")
            if (rest.startsWith("of ", ignoreCase = true)) rest = rest.substring(3)
        }

        val name = cleanName(rest).ifEmpty { cleanName(stripMarker(raw)) }.ifEmpty { raw }
        return ExtractedIngredient(qty = qty, unit = unit, name = name, raw = raw)
    }

    /** Drops "(optional)", a trailing top-level ", chopped" clause and stray punctuation. */
    private fun cleanName(text: String): String {
        var s = text.replace(optionalNote, " ")
        var depth = 0
        for (i in s.indices) {
            when (s[i]) {
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                ',', ';' -> if (depth == 0) {
                    val head = s.substring(0, i)
                    if (head.isNotBlank()) s = head
                    break
                }
            }
        }
        return s.replace(Regex("""\s+"""), " ").trim().trim(',', ';', ':', '.', '-', ' ')
    }
}
