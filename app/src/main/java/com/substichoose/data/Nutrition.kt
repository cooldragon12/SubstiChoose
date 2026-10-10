package com.substichoose.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Nutrition per 100 g for one ingredient name, cached from USDA FoodData Central so each name is
 * looked up once and still shows offline. found = false records "USDA had no match" (not retried).
 */
@Entity(tableName = "nutrition")
data class NutritionEntity(
    /** Lower-case ingredient name, e.g. "butter". */
    @PrimaryKey val key: String,
    val found: Boolean,
    /** The USDA food it matched, e.g. "Butter, salted". */
    val description: String = "",
    val kcal: Double? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val fetchedAt: Long = System.currentTimeMillis(),
)

@Dao
interface NutritionDao {
    @Query("SELECT * FROM nutrition")
    fun observeAll(): Flow<List<NutritionEntity>>

    /** Names that need no lookup. A "found" row without calories is treated as missing and re-fetched. */
    @Query("SELECT `key` FROM nutrition WHERE NOT (found = 1 AND kcal IS NULL)")
    suspend fun keys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NutritionEntity)

    @Query("DELETE FROM nutrition")
    suspend fun clear()

    @Query("SELECT name FROM ingredients")
    suspend fun allIngredientNames(): List<String>

    @Query("SELECT chosenSubstituteJson FROM ingredients WHERE chosenSubstituteJson IS NOT NULL")
    suspend fun allChosenSubstitutes(): List<String>
}
