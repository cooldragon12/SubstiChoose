package com.substichoose.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionMatcherTest {

    private fun pick(query: String, candidates: List<String>) = candidates[NutritionMatcher.best(query, candidates)]

    @Test
    fun eggPicksWholeRawEgg() {
        // Real USDA search results for "egg" (top of the list).
        val candidates = listOf(
            "Eggs, Grade A, Large, egg white", "Eggs, Grade A, Large, egg whole", "Eggs, Grade A, Large, egg yolk",
            "Bagels, egg", "Egg substitute, powder", "Egg, white, dried", "Egg, whole, dried",
            "Egg, white, raw, fresh", "Egg, whole, cooked, fried", "Egg, whole, raw, fresh", "Egg, yolk, raw, fresh",
        )
        assertEquals("Egg, whole, raw, fresh", pick("egg", candidates))
    }

    @Test
    fun bananasPicksRawNotPowder() {
        val candidates = listOf(
            "Bananas, dehydrated, or banana powder", "Bananas, raw", "Bananas, overripe, raw",
            "Snacks, banana chips", "Babyfood, banana no tapioca, strained", "Bananas, ripe and slightly ripe, raw",
        )
        assertEquals("Bananas, raw", pick("bananas", candidates))
    }

    @Test
    fun butterAvoidsGhee() {
        val candidates = listOf("Butter, Clarified butter (ghee)", "Butter, salted", "Butter, without salt")
        assertEquals("Butter, salted", pick("butter", candidates))
    }

    @Test
    fun askedForFormIsNotPenalised() {
        val candidates = listOf("Egg, whole, raw, fresh", "Egg, white, raw, fresh")
        assertEquals("Egg, white, raw, fresh", pick("egg white", candidates))
    }

    private fun pickOrNull(query: String, candidates: List<String>) =
        NutritionMatcher.best(query, candidates).let { if (it < 0) null else candidates[it] }

    // The cases below are real wrong matches seen on the phone with the first version.

    @Test
    fun everyKeyWordMustMatch() {
        assertEquals(null, pickOrNull("whole peppercorns", listOf("Egg, whole, raw, fresh", "Pepper, banana, raw")))
        assertEquals(null, pickOrNull("flax egg", listOf("Egg, whole, raw, fresh", "Egg, white, raw, fresh")))
        assertEquals(
            "Leavening agents, baking soda",
            pickOrNull("baking soda", listOf("Baking chocolate, mexican, squares", "Leavening agents, baking soda")),
        )
        assertEquals(null, pickOrNull("taco seasoning", listOf("TACO BELL, Nachos", "Taco shells, baked")))
        assertEquals(
            "Soup, chicken broth, ready-to-serve",
            pickOrNull("chicken broth", listOf("Chicken spread", "Soup, chicken broth, ready-to-serve")),
        )
    }

    @Test
    fun descriptorsAndNotesAreOptional() {
        assertEquals(
            "Sugars, granulated",
            pickOrNull("white sugar (for the caramel)", listOf("Sugar-apples, (sweetsop), raw", "Sugars, granulated")),
        )
        assertEquals("Onions, raw", pickOrNull("diced onion", listOf("Onions, raw", "Onions, dehydrated flakes")))
        assertEquals("Butter, without salt", pickOrNull("unsalted butter", listOf("Butter, Clarified butter (ghee)", "Butter, without salt")))
    }

    @Test
    fun leavesMatchLeaf() {
        assertEquals("Spices, bay leaf", pickOrNull("bay leaves", listOf("Drumstick leaves, raw", "Spices, bay leaf")))
    }

    @Test
    fun secondRoundOfRealMistakes() {
        assertEquals(
            "Soup, chicken broth, ready-to-serve",
            pickOrNull("chicken broth", listOf("Chicken, canned, no broth", "Soup, chicken broth, ready-to-serve")),
        )
        assertEquals(null, pickOrNull("chicken broth", listOf("Chicken, canned, no broth")))
        assertEquals(
            "Soy sauce made from soy (tamari)",
            pickOrNull(
                "soy sauce",
                listOf("Sauce, peanut, made from peanut butter, water, soy sauce", "Soy sauce made from soy (tamari)"),
            ),
        )
        assertEquals(
            null,
            pickOrNull("whole peppercorns", listOf("HORMEL ALWAYS TENDER, Pork Tenderloin, Peppercorn-Flavored")),
        )
        assertEquals(
            "Spaghetti, spinach, dry",
            pickOrNull("spaghetti or other pasta", listOf("Sauce, pasta, spaghetti/marinara, ready-to-serve", "Spaghetti, spinach, dry")),
        )
        assertEquals("Spices, paprika", pickOrNull("smoked paprika", listOf("Spices, paprika")))
    }

    @Test
    fun phraseBeatsScatteredWordsAndWeakMatchesAreDropped() {
        // Real USDA candidates for "chicken broth".
        val broth = listOf(
            "Chicken, canned, no broth", "Chicken, canned, meat only, with broth", "Soup, chicken broth cubes, dry",
            "Soup, chicken broth or bouillon, dry",
            "Soup, chicken broth, canned, condensed", "Soup, chicken broth, ready-to-serve",
        )
        assertEquals("Soup, chicken broth, ready-to-serve", pickOrNull("chicken broth", broth))
        assertEquals(
            null,
            pickOrNull("whole peppercorns", listOf("Salad dressing, peppercorn dressing, commercial, regular")),
        )
    }
}
