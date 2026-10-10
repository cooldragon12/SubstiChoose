package com.substichoose.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.substichoose.model.ModelJson
import com.substichoose.model.SubstituteOption
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** One saved recipe. Ingredients live in [IngredientEntity] rows. */
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val imagePath: String?,       // copied photo in filesDir/images, or null
    val ocrText: String,
    val stepsJson: String,        // JSON array of strings
    val adaptedStepsJson: String?,// JSON of AdaptedSteps, or null
    val createdAt: Long,
)

/** One ingredient row of a recipe, ordered by [position]. */
@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val position: Int,
    val qty: String,
    val unit: String,
    val name: String,
    val raw: String,
    val isMissing: Boolean = false,          // "Don't have it" toggle
    val chosenSubstituteJson: String? = null // JSON of SubstituteOption, or null
)

/** A recipe with all its ingredient rows, loaded in one @Transaction query. */
data class RecipeWithIngredients(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId") val ingredients: List<IngredientEntity>,
) {
    /** Decoded from recipe.stepsJson; empty if the JSON is broken. */
    @get:Ignore
    val steps: List<String>
        get() = RecipeJson.decodeSteps(recipe.stepsJson)

    /** Ingredients in the order they were written (by position, then id). */
    @get:Ignore
    val sortedIngredients: List<IngredientEntity>
        get() = ingredients.sortedWith(compareBy<IngredientEntity>({ it.position }, { it.id }))

    /** The substitute the user picked for [ingredient], or null. */
    fun chosenSubstitute(ingredient: IngredientEntity): SubstituteOption? =
        RecipeJson.decodeSubstitute(ingredient.chosenSubstituteJson)
}

/** JSON helpers for the text columns, all built on [ModelJson.json] and never throwing on decode. */
internal object RecipeJson {
    private val stringList = ListSerializer(String.serializer())

    fun encodeSteps(steps: List<String>): String = ModelJson.json.encodeToString(stringList, steps)

    fun decodeSteps(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { ModelJson.json.decodeFromString(stringList, json) }.getOrDefault(emptyList())
    }

    fun encodeSubstitute(option: SubstituteOption?): String? =
        option?.let { ModelJson.json.encodeToString(SubstituteOption.serializer(), it) }

    fun decodeSubstitute(json: String?): SubstituteOption? {
        if (json.isNullOrBlank()) return null
        return runCatching { ModelJson.json.decodeFromString(SubstituteOption.serializer(), json) }.getOrNull()
    }
}
