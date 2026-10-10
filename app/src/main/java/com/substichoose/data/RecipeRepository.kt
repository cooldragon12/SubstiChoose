package com.substichoose.data

import androidx.room.withTransaction
import com.substichoose.model.AdaptedSteps
import com.substichoose.model.ExtractedRecipe
import com.substichoose.model.ModelJson
import com.substichoose.model.SubstituteOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/** Saved recipes. Every write runs on [Dispatchers.IO]; the Flows re-emit after each change. */
class RecipeRepository(private val db: AppDatabase) {

    private val dao = db.recipeDao()

    /** All recipes, newest first. */
    fun observeRecipes(): Flow<List<RecipeWithIngredients>> =
        dao.observeAll().flowOn(Dispatchers.IO)

    /** One recipe, or null once it is deleted. */
    fun observeRecipe(id: Long): Flow<RecipeWithIngredients?> =
        dao.observe(id).flowOn(Dispatchers.IO)

    /** Inserts the recipe and its ingredients (position = list index) in one transaction. Returns the new id. */
    suspend fun saveNew(recipe: ExtractedRecipe, imagePath: String?, ocrText: String): Long =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val recipeId = dao.insertRecipe(
                    RecipeEntity(
                        title = recipe.title.trim(), // blank when the page has no title; screens show "Untitled recipe"
                        imagePath = imagePath,
                        ocrText = ocrText,
                        stepsJson = RecipeJson.encodeSteps(recipe.steps.map { it.trim() }.filter { it.isNotEmpty() }),
                        adaptedStepsJson = null,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                val rows = recipe.ingredients
                    .filter { it.name.isNotBlank() || it.raw.isNotBlank() }
                    .mapIndexed { index, ing ->
                        IngredientEntity(
                            recipeId = recipeId,
                            position = index,
                            qty = ing.qty.trim(),
                            unit = ing.unit.trim(),
                            name = ing.name.trim().ifEmpty { ing.raw.trim() },
                            raw = ing.raw.trim().ifEmpty { listOf(ing.qty, ing.unit, ing.name).filter { it.isNotBlank() }.joinToString(" ") },
                        )
                    }
                if (rows.isNotEmpty()) dao.insertIngredients(rows)
                recipeId
            }
        }

    suspend fun updateTitle(recipeId: Long, title: String) = withContext(Dispatchers.IO) {
        dao.updateTitle(recipeId, title.trim())
    }

    /** Saves an edited row as-is (qty, unit, name, isMissing, chosen substitute ...). */
    suspend fun updateIngredient(ingredient: IngredientEntity) = withContext(Dispatchers.IO) {
        dao.updateIngredient(ingredient)
    }

    /** Adds a row typed by the user, e.g. "1 1/2 cups flour", at the end of the list. Returns its id. */
    /**
     * Replaces a row's text (e.g. fixing a bad scan) and re-splits it like [addIngredient].
     * A different ingredient name clears the chosen swap, which was picked for the old one.
     */
    suspend fun editIngredientText(ingredient: IngredientEntity, raw: String) = withContext(Dispatchers.IO) {
        val clean = raw.trim()
        if (clean.isEmpty()) return@withContext
        val parsed = IngredientLineParser.parse(clean)
        val name = parsed?.name ?: clean
        dao.updateIngredient(
            ingredient.copy(
                qty = parsed?.qty.orEmpty(),
                unit = parsed?.unit.orEmpty(),
                name = name,
                raw = clean,
                chosenSubstituteJson = ingredient.chosenSubstituteJson.takeIf { name.equals(ingredient.name, ignoreCase = true) },
            )
        )
    }

    suspend fun addIngredient(recipeId: Long, raw: String): Long = withContext(Dispatchers.IO) {
        val parsed = IngredientLineParser.parse(raw)
        db.withTransaction {
            val position = (dao.maxPosition(recipeId) ?: -1) + 1
            dao.insertIngredient(
                IngredientEntity(
                    recipeId = recipeId,
                    position = position,
                    qty = parsed?.qty.orEmpty(),
                    unit = parsed?.unit.orEmpty(),
                    name = parsed?.name ?: raw.trim(),
                    raw = raw.trim(),
                )
            )
        }
    }

    /** Puts a deleted row back exactly as it was (same id and position), for Undo. */
    suspend fun restoreIngredient(ingredient: IngredientEntity) = withContext(Dispatchers.IO) {
        dao.insertIngredient(ingredient)
    }

    suspend fun deleteIngredient(ingredientId: Long) = withContext(Dispatchers.IO) {
        dao.deleteIngredient(ingredientId)
    }

    /** Stores the picked substitute for a row; null clears it. */
    suspend fun chooseSubstitute(ingredientId: Long, option: SubstituteOption?) = withContext(Dispatchers.IO) {
        dao.updateChosenSubstitute(ingredientId, RecipeJson.encodeSubstitute(option))
    }

    suspend fun saveAdaptedSteps(recipeId: Long, steps: AdaptedSteps) = withContext(Dispatchers.IO) {
        dao.updateAdaptedSteps(recipeId, ModelJson.json.encodeToString(AdaptedSteps.serializer(), steps))
    }

    /** Deletes the recipe, its rows and its copied photo. */
    suspend fun deleteRecipe(recipeId: Long) = withContext(Dispatchers.IO) {
        val imagePath = dao.imagePath(recipeId)
        db.withTransaction {
            dao.deleteIngredientsOf(recipeId)
            dao.deleteRecipe(recipeId)
        }
        imagePath?.let { runCatching { File(it).delete() } }
        Unit
    }
}
