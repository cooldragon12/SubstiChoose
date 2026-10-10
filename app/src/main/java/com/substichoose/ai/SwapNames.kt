package com.substichoose.ai

/**
 * Loose "is this the same swap?" check, so Gemma doesn't repeat a built-in swap under another name
 * ("Aquafaba" vs "Aquafaba (chickpea liquid)", "Ground flaxseed mixed with water" vs "Flax egg").
 */
internal object SwapNames {

    /** Words too common to identify a swap on their own. */
    private val generic = setOf(
        "with", "mixed", "ground", "plain", "fresh", "water", "milk", "juice", "powder", "sauce", "liquid",
        "oil", "cup", "cups", "tbsp", "tsp", "and", "the", "for", "unsweetened", "mashed", "ripe", "store", "bought",
    )

    fun keyWords(name: String): Set<String> =
        name.lowercase()
            .split(Regex("""[^\p{L}]+"""))
            .filter { it.length >= 4 && it !in generic }
            .toSet()

    /** True when the two names share a key word, or one key word starts with the other ("flaxseed" / "flax"). */
    fun isSameSwap(a: String, b: String): Boolean {
        val ka = keyWords(a)
        val kb = keyWords(b).ifEmpty { return a.trim().equals(b.trim(), ignoreCase = true) }
        if (ka.isEmpty()) return a.trim().equals(b.trim(), ignoreCase = true)
        return ka.any { x -> kb.any { y -> x == y || x.startsWith(y) || y.startsWith(x) } }
    }
}
