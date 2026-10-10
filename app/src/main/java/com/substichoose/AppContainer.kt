package com.substichoose

import android.content.Context
import com.substichoose.ai.GemmaService
import com.substichoose.ai.ModelDownloader
import com.substichoose.ai.SubstituteService
import com.substichoose.data.AppDatabase
import com.substichoose.data.RecipeRepository
import com.substichoose.data.SettingsRepository
import com.substichoose.nutrition.NutritionRepository
import com.substichoose.nutrition.UsdaClient
import com.substichoose.ocr.OcrService
import com.substichoose.pipeline.ScanPipeline
import com.substichoose.pipeline.ScanQueue
import com.substichoose.safety.AllergenChecker
import com.substichoose.safety.CuratedSwaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection: one instance of each service for the whole app. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Lives as long as the app; used to warm up Gemma at start and to run background scans. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val checker: AllergenChecker by lazy { AllergenChecker.fromAssets(appContext) }
    val curatedSwaps: CuratedSwaps by lazy { CuratedSwaps.fromAssets(appContext) }

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val recipes: RecipeRepository by lazy { RecipeRepository(database) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val gemma: GemmaService by lazy { GemmaService(appContext) }
    /** Downloads Gemma when it isn't on the phone yet. */
    val modelDownloader: ModelDownloader by lazy { ModelDownloader(appContext, gemma, appScope) }
    val ocr: OcrService by lazy { OcrService(appContext) }

    val substitutes: SubstituteService by lazy { SubstituteService(gemma, checker, curatedSwaps) }
    val scanPipeline: ScanPipeline by lazy { ScanPipeline(appContext, ocr, gemma, recipes, checker) }

    /** Nutrition per 100 g from USDA, fetched in the background when online and cached. */
    val nutrition: NutritionRepository by lazy {
        NutritionRepository(appContext, database.nutritionDao(), UsdaClient(BuildConfig.USDA_API_KEY), appScope)
    }

    /** Background scans: keep running while the user moves around the app. */
    val scanQueue: ScanQueue by lazy { ScanQueue(appScope, scanPipeline, recipes) }
}
