package com.substichoose.safety

import com.substichoose.model.ModelJson
import com.substichoose.model.SubstitutesResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Run with: ./gradlew :app:testDebugUnitTest (working directory = the app module). */
class AllergenCheckerTest {

    private val checker = AllergenChecker.fromJson(File("src/main/assets/allergens.json").readText())
    private val swaps = CuratedSwaps.fromJson(File("src/main/assets/substitutes.json").readText())

    private fun resource(name: String): String =
        requireNotNull(javaClass.getResource("/recipes/$name")) { "missing test recipe $name" }.readText()

    @Test
    fun testRecipesFlagTheExpectedAllergens() {
        val expected = Json.parseToJsonElement(resource("expected.json")).jsonObject
        for ((file, ids) in expected) {
            val ingredients = resource(file).substringAfter("Ingredients").substringBefore("Steps")
            val found = ingredients.lines().filter { it.isNotBlank() }
                .flatMap { checker.allergensIn(it) }.toSet()
            assertEquals(file, ids.jsonArray.map { it.jsonPrimitive.content }.toSet(), found)
        }
    }

    @Test
    fun trickyPhrasesDoNotFoolIt() {
        val cases = mapOf(
            "eggplant" to setOf(),
            "almond milk" to setOf("treenuts"),
            "peanut butter" to setOf("peanut"),
            "coconut milk" to setOf(),
            "cream of tartar" to setOf(),
            "rice flour" to setOf(),
            "flax egg" to setOf(),
            "buttermilk" to setOf("dairy"),
            "gluten-free soy sauce" to setOf("soy"),
            "bagoong alamang" to setOf("fish", "shellfish"),
            "nutmeg" to setOf(),
            "butternut squash" to setOf(),
            "2 Eggs" to setOf("egg"),
            "All-Purpose Flour" to setOf("gluten"),
            "patis" to setOf("fish"),
            "toyo" to setOf("soy", "gluten"),
            "manok" to setOf(),
            "cocoa butter" to setOf(),
            "egg noodles" to setOf("egg", "gluten"),
            "glass noodles" to setOf(),
            "oyster mushrooms" to setOf(),
        )
        for ((text, ids) in cases) assertEquals(text, ids, checker.allergensIn(text))
    }

    @Test
    fun customTermsAreMatchedToo() {
        val profile = AllergyProfile(setOf("dairy"), setOf("Coconut"))
        assertEquals(setOf("Coconut"), checker.conflicts("2 cups coconut milk", profile))
        assertEquals(setOf("dairy"), checker.conflicts("1/2 cup butter", profile))
    }

    @Test
    fun unsafeSubstitutesAreDropped() {
        val milk = swaps.safeOptionsFor("1 cup milk", checker, AllergyProfile(setOf("soy", "treenuts")))
        assertEquals(listOf("Rice milk", "Oat milk"), milk.map { it.name })

        val egg = swaps.safeOptionsFor("1 large egg", checker, AllergyProfile(setOf("egg")))
        assertEquals(3, egg.size) // "1 egg = ..." on the left of the ratio must not count

        val butter = swaps.safeOptionsFor("1/2 cup butter", checker, AllergyProfile(setOf("dairy"), setOf("Coconut")))
        assertEquals(listOf("Vegetable oil", "Plant-based butter"), butter.map { it.name })
        assertTrue(butter.all { it.source == "curated" })
    }

    @Test
    fun longestMatchWins() {
        assertEquals("peanut butter", swaps.find("1 cup peanut butter")?.ingredient)
        assertEquals("fish sauce", swaps.find("1 tbsp fish sauce (patis)")?.ingredient)
        assertEquals("buttermilk", swaps.find("1 cup buttermilk")?.ingredient)
    }

    @Test
    fun aiReplyWithFencesStillParses() {
        val reply = """
            Sure! Here you go:
            ```json
            {"ingredient":"butter","options":[{"name":"Almond butter","ratio":"1 cup butter = 1 cup almond butter","contains":["treenuts"]},{"name":"Vegetable oil","ratio":"1 cup butter = 3/4 cup vegetable oil"}]}
            ```
        """.trimIndent()
        val parsed = ModelJson.decodeOrNull<SubstitutesResponse>(reply)
        assertNotNull(parsed)
        val safe = checker.safeOptions(parsed!!.options, AllergyProfile(setOf("treenuts")))
        assertEquals(listOf("Vegetable oil"), safe.map { it.name })
    }
}
