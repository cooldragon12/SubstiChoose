package com.substichoose.pipeline

import com.substichoose.model.IngredientRows
import com.substichoose.model.ModelJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRecipeParserTest {

    private val recipes = listOf(
        "banana-bread.txt", "chicken-adobo.txt", "ginataang-kalabasa.txt",
        "leche-flan.txt", "pancit-canton.txt", "peanut-butter-cookies.txt",
    )

    private fun resource(name: String): String =
        requireNotNull(javaClass.getResource("/recipes/$name")) { "missing test recipe $name" }.readText()

    @Test
    fun testRecipesKeepTitleAndEveryIngredient() {
        for (file in recipes) {
            val text = resource(file)
            val written = text.substringAfter("Ingredients").substringBefore("Steps")
                .lines().map { it.trim() }.filter { it.isNotEmpty() }
            val r = OfflineRecipeParser.parse(text)
            assertEquals(file, text.lines().first().trim(), r.title)
            assertEquals(file, written, r.ingredients.map { it.raw })
        }
    }

    @Test
    fun noTitleStaysBlank() {
        val r = OfflineRecipeParser.parse("Ingredients\n2 cups flour\n1 egg\nSteps\n1. Mix everything together well.")
        assertEquals("", r.title)
        assertEquals(listOf("2 cups flour", "1 egg"), r.ingredients.map { it.raw })
    }

    @Test
    fun metadataIsNotATitle() {
        val r = OfflineRecipeParser.parse("Serves 4\nwww.mykitchen.com\nIngredients\n2 cups flour\n1 egg")
        assertEquals("", r.title)
    }

    @Test
    fun extraTextIsNotAnIngredient() {
        val text = """
            Pancakes
            Ingredients
            Serves 4
            Prep time: 10 minutes
            For the batter:
            1 cup flour
            2 eggs
            1 cup milk
            30 minutes
            www.example.com/pancakes
            Steps
            1. Whisk everything together and cook on a hot pan.
        """.trimIndent()
        val r = OfflineRecipeParser.parse(text)
        assertEquals("Pancakes", r.title)
        assertEquals(listOf("1 cup flour", "2 eggs", "1 cup milk"), r.ingredients.map { it.raw })
    }

    @Test
    fun realIngredientsPassTheFilter() {
        for (line in listOf("pinch of salt", "Salt and pepper to taste", "3 bay leaves", "2 heads garlic",
                "Oil for frying", "1 can (390 g) condensed milk", "Makapuno strings", "Note-free butter")) {
            assertFalse(line, IngredientFilter.isNotIngredient(line))
        }
    }

    @Test
    fun titleMustBeWrittenOnThePage() {
        assertTrue(ScanPipeline.isWrittenIn("Chicken Adobo", "CHICKEN\nADOBO\nIngredients"))
        assertFalse(ScanPipeline.isWrittenIn("Classic Banana Bread", "Banana Bread\nIngredients"))
        assertFalse(ScanPipeline.isWrittenIn("", "Banana Bread"))
    }

    @Test
    fun gemmaReplyWithTitle() {
        // Seen on the phone: numbers unquoted in places, fenced.
        val reply = "```json\n" + """{"title": "", "items": [[1, "1", "cup", "flour"], ["3", "", "", "eggs"]]}""" + "\n```"
        val rows = ModelJson.decodeOrNull<IngredientRows>(reply)!!
        assertEquals("", rows.title)
        assertEquals(listOf("1", "3"), rows.items.map { it[0] })
    }
}
