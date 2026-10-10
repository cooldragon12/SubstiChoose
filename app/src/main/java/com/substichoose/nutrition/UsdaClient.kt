package com.substichoose.nutrition

import com.substichoose.data.NutritionEntity
import com.substichoose.model.ModelJson
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** USDA FoodData Central search. Only the ingredient name is sent. Blocking: call on Dispatchers.IO. */
class UsdaClient(private val apiKey: String) {

    sealed interface Result {
        data class Found(val entity: NutritionEntity) : Result
        data object NotFound : Result
        /** 429/403: the key's hourly limit is used up. */
        data object RateLimited : Result
        data class Failed(val reason: String) : Result
    }

    @Serializable
    private data class SearchResponse(val foods: List<Food> = emptyList())

    @Serializable
    private data class Food(val description: String = "", val foodNutrients: List<FoodNutrient> = emptyList())

    @Serializable
    private data class FoodNutrient(val nutrientNumber: String? = null, val unitName: String? = null, val value: Double? = null)

    fun lookup(key: String): Result {
        // USDA has no plain "water" entry that search finds reliably; it's zero anyway.
        if (key in ZERO_NUTRITION) {
            return Result.Found(NutritionEntity(key = key, found = true, description = "Water", kcal = 0.0, proteinG = 0.0, carbsG = 0.0, fatG = 0.0))
        }
        val url = URL(
            "https://api.nal.usda.gov/fdc/v1/foods/search" +
                "?query=" + enc(key) +
                "&dataType=" + enc("Foundation,SR Legacy") +
                "&pageSize=25" +
                "&api_key=" + enc(apiKey)
        )
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            when (val code = conn.responseCode) {
                200 -> parse(key, conn.inputStream.bufferedReader().use { it.readText() })
                429, 403 -> Result.RateLimited
                else -> Result.Failed("HTTP $code")
            }
        } catch (e: Exception) {
            Result.Failed(e.javaClass.simpleName)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(key: String, body: String): Result {
        val foods = runCatching { ModelJson.json.decodeFromString(SearchResponse.serializer(), body).foods }
            .getOrElse { return Result.Failed("bad JSON") }
        fun Food.nutrient(vararg numbers: String, unit: String): Double? = numbers.firstNotNullOfOrNull { n ->
            foodNutrients.firstOrNull { it.nutrientNumber == n && it.unitName.equals(unit, ignoreCase = true) }?.value
        }
        // 208 = Energy; Foundation foods sometimes only carry the Atwater energies (957/958).
        fun Food.kcal() = nutrient("208", "957", "958", unit = "KCAL")
        // Best match that actually has values (some USDA entries come back without nutrients).
        val food = NutritionMatcher.ranked(key, foods.map { it.description })
            .map { foods[it] }
            .firstOrNull { it.kcal() != null }
            ?: return Result.NotFound
        return Result.Found(
            NutritionEntity(
                key = key,
                found = true,
                description = food.description,
                kcal = food.kcal(),
                proteinG = food.nutrient("203", unit = "G"),
                carbsG = food.nutrient("205", unit = "G"),
                fatG = food.nutrient("204", unit = "G"),
            )
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        val ZERO_NUTRITION = setOf("water", "cold water", "warm water", "hot water", "boiling water", "ice", "ice water")
    }
}
