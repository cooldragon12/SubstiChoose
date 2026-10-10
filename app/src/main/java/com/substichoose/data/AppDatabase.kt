package com.substichoose.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app's local database. Version 2 added the nutrition cache with a real migration so saved
 * recipes survive; any other schema change without a migration still wipes the data.
 */
@Database(
    entities = [RecipeEntity::class, IngredientEntity::class, NutritionEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun nutritionDao(): NutritionDao

    companion object {
        private const val NAME = "substichoose.db"

        /** 1 -> 2: adds the nutrition cache. Must match NutritionEntity exactly (Room checks it). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `nutrition` (`key` TEXT NOT NULL, `found` INTEGER NOT NULL, " +
                        "`description` TEXT NOT NULL, `kcal` REAL, `proteinG` REAL, `carbsG` REAL, `fatG` REAL, " +
                        "`fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        /** Returns the single app-wide database (built once). */
        fun build(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
