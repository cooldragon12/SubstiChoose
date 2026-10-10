package com.substichoose.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Room access for recipes and their ingredient rows. Use through [RecipeRepository]. */
@Dao
interface RecipeDao {

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<RecipeWithIngredients>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observe(id: Long): Flow<RecipeWithIngredients?>

    @Insert
    suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Insert
    suspend fun insertIngredients(ingredients: List<IngredientEntity>): List<Long>

    @Insert
    suspend fun insertIngredient(ingredient: IngredientEntity): Long

    @Update
    suspend fun updateIngredient(ingredient: IngredientEntity)

    @Query("UPDATE recipes SET title = :title WHERE id = :recipeId")
    suspend fun updateTitle(recipeId: Long, title: String)

    @Query("UPDATE recipes SET adaptedStepsJson = :json WHERE id = :recipeId")
    suspend fun updateAdaptedSteps(recipeId: Long, json: String?)

    @Query("UPDATE ingredients SET chosenSubstituteJson = :json WHERE id = :ingredientId")
    suspend fun updateChosenSubstitute(ingredientId: Long, json: String?)

    @Query("SELECT MAX(position) FROM ingredients WHERE recipeId = :recipeId")
    suspend fun maxPosition(recipeId: Long): Int?

    @Query("SELECT imagePath FROM recipes WHERE id = :recipeId")
    suspend fun imagePath(recipeId: Long): String?

    @Query("DELETE FROM ingredients WHERE id = :ingredientId")
    suspend fun deleteIngredient(ingredientId: Long)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsOf(recipeId: Long)

    @Query("DELETE FROM recipes WHERE id = :recipeId")
    suspend fun deleteRecipe(recipeId: Long)
}
