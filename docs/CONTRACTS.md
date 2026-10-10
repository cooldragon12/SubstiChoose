# SubstiChoose — code contracts between the three parts

Project root: `/mnt/user-data/outputs/SubstiChoose` (Gradle, Kotlin 2.1.21, Compose, minSdk 26, package `com.substichoose`).
Source root: `app/src/main/java/com/substichoose/`.

Already written — do NOT change their public API:
- `model/Models.kt` — `ExtractedRecipe`, `ExtractedIngredient`, `SubstitutesResponse`, `SubstituteOption`, `AdaptedSteps`, `AdaptedStep`, `ModelJson.decodeOrNull<T>()`, `ModelJson.json`.
- `safety/AllergenChecker.kt` — `AllergenChecker` (`allergensIn`, `conflicts`, `isSafe`, `safeOptions`, `labels`), `AllergyProfile(allergenIds: Set<String>, customTerms: Set<String>)`.
- `safety/CuratedSwaps.kt` — `CuratedSwaps.find(name)`, `safeOptionsFor(name, checker, profile)`.
- `AppContainer.kt`, `SubstiChooseApp.kt`, `MainActivity.kt` — wiring. Read AppContainer to see the constructors you must provide.
- `docs/SCHEMAS.md` — JSON shapes, allergen ids, prompt text.

Allergen ids (fixed): `peanut, treenuts, dairy, egg, gluten, soy, fish, shellfish, sesame`. Labels come from `checker.labels`.

Every type and signature below must exist EXACTLY as written (names, parameter order, types). You may add private helpers and extra files inside your own package.

---

## Part A — AI (package `com.substichoose.ai`) — owner: AI agent

```kotlin
package com.substichoose.ai

sealed interface ModelState {
    data object NotLoaded : ModelState
    data object Loading : ModelState
    data object Ready : ModelState
    /** No .litertlm file found. folderPath = where the user must adb push it. */
    data class Missing(val folderPath: String) : ModelState
    data class Failed(val message: String) : ModelState
}

enum class SwapReason { ALLERGY, MISSING }

class GemmaService(context: android.content.Context) {
    val state: kotlinx.coroutines.flow.StateFlow<ModelState>
    /** Loads the model once (thread-safe, idempotent). Returns true when Ready. */
    suspend fun ensureLoaded(): Boolean
    /** Job 1 from OCR text. null on failure (after one JSON retry). */
    suspend fun extractRecipe(ocrText: String): com.substichoose.model.ExtractedRecipe?
    /** Job 1 from the photo itself (Gemma vision). null if vision unsupported or fails. */
    suspend fun extractRecipeFromImage(imageFile: java.io.File): com.substichoose.model.ExtractedRecipe?
    /** Job 2. avoidLabels = human labels of everything the user avoids (e.g. "Egg", "Coconut"). */
    suspend fun suggestSubstitutes(
        ingredient: String,
        recipeTitle: String,
        reason: SwapReason,
        avoidLabels: List<String>,
    ): com.substichoose.model.SubstitutesResponse?
    /** Job 3 (stretch). swaps = original ingredient -> chosen substitute name. */
    suspend fun adaptSteps(steps: List<String>, swaps: Map<String, String>): com.substichoose.model.AdaptedSteps?
    fun close()
}

class SubstituteService(
    gemma: GemmaService,
    checker: com.substichoose.safety.AllergenChecker,
    curated: com.substichoose.safety.CuratedSwaps,
) {
    /** Instant, already safety-filtered. May be empty. */
    fun curated(ingredientName: String, profile: com.substichoose.safety.AllergyProfile): List<com.substichoose.model.SubstituteOption>
    /** Gemma's options, ALWAYS passed through checker.safeOptions. null if Gemma unavailable or failed. */
    suspend fun fromAi(
        ingredientName: String,
        recipeTitle: String,
        reason: SwapReason,
        profile: com.substichoose.safety.AllergyProfile,
    ): List<com.substichoose.model.SubstituteOption>?
    /** Human labels for a profile: checker.labels for ids + custom terms as typed. */
    fun avoidLabels(profile: com.substichoose.safety.AllergyProfile): List<String>
}
```

Model location: any `*.litertlm` file in `context.getExternalFilesDir(null)`
(on the phone: `/sdcard/Android/data/com.substichoose/files/`). Pick the first one found.

LiteRT-LM API (artifact `com.google.ai.edge.litertlm:litertlm-android`, package `com.google.ai.edge.litertlm`), from Google's docs:

```kotlin
val engineConfig = EngineConfig(
    modelPath = "/path/to/model.litertlm",
    backend = Backend.GPU(),          // or Backend.CPU()
    visionBackend = Backend.GPU(),    // needed for image input
    cacheDir = context.cacheDir.path,
)
val engine = Engine(engineConfig)
engine.initialize()                   // slow (~10 s): call on Dispatchers.IO

val conversation = engine.createConversation(
    ConversationConfig(
        systemInstruction = Contents.of("You are ..."),
        samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.2),
    )
)
val reply = conversation.sendMessage("text")                    // returns a Message; reply.toString() gives text
conversation.sendMessage(Contents.of(Content.ImageFile("/path.jpg"), Content.Text("...")))
conversation.sendMessageAsync("text").collect { chunk -> }      // Flow
conversation.close(); engine.close()
```

---

## Part B — Camera, OCR, storage, pipeline — owner: data agent

```kotlin
package com.substichoose.ocr
class OcrService(context: android.content.Context) {
    /** ML Kit Latin text recognition, on-device. Returns "" when no text. Throws on unreadable image. */
    suspend fun readText(imageUri: android.net.Uri): String
}
```

```kotlin
package com.substichoose.camera
/** Full-screen CameraX preview with a shutter button. Handles the CAMERA permission request itself.
 *  Saves a JPEG into context.cacheDir and returns its Uri. */
@androidx.compose.runtime.Composable
fun CameraCapture(
    onCaptured: (android.net.Uri) -> Unit,
    onError: (String) -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
)
```
(The UI agent places this inside its Scan screen and adds its own gallery button and top bar on top.)

```kotlin
package com.substichoose.data

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

@Entity(tableName = "ingredients", foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE)], indices = [Index("recipeId")])
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

data class RecipeWithIngredients(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId") val ingredients: List<IngredientEntity>,
) {
    val steps: List<String>                       // decoded from recipe.stepsJson
    val sortedIngredients: List<IngredientEntity> // by position
    fun chosenSubstitute(ingredient: IngredientEntity): com.substichoose.model.SubstituteOption?
}

abstract class AppDatabase : RoomDatabase() {
    companion object { fun build(context: android.content.Context): AppDatabase }
}

class RecipeRepository(db: AppDatabase) {
    fun observeRecipes(): kotlinx.coroutines.flow.Flow<List<RecipeWithIngredients>>   // newest first
    fun observeRecipe(id: Long): kotlinx.coroutines.flow.Flow<RecipeWithIngredients?>
    suspend fun saveNew(recipe: com.substichoose.model.ExtractedRecipe, imagePath: String?, ocrText: String): Long
    suspend fun updateTitle(recipeId: Long, title: String)
    suspend fun updateIngredient(ingredient: IngredientEntity)
    suspend fun addIngredient(recipeId: Long, raw: String): Long   // parses qty/unit/name with a simple splitter
    suspend fun deleteIngredient(ingredientId: Long)
    suspend fun chooseSubstitute(ingredientId: Long, option: com.substichoose.model.SubstituteOption?)
    suspend fun saveAdaptedSteps(recipeId: Long, steps: com.substichoose.model.AdaptedSteps)
    suspend fun deleteRecipe(recipeId: Long)
}

class SettingsRepository(context: android.content.Context) {   // DataStore Preferences
    val profile: kotlinx.coroutines.flow.Flow<com.substichoose.safety.AllergyProfile>
    val onboardingDone: kotlinx.coroutines.flow.Flow<Boolean>
    suspend fun saveProfile(profile: com.substichoose.safety.AllergyProfile)  // also sets onboardingDone = true
}
```

```kotlin
package com.substichoose.pipeline

enum class ScanStage { COPYING, READING_TEXT, UNDERSTANDING, SAVING }

class ScanPipeline(
    context: android.content.Context,
    ocr: com.substichoose.ocr.OcrService,
    gemma: com.substichoose.ai.GemmaService,
    recipes: com.substichoose.data.RecipeRepository,
) {
    /** Photo -> saved recipe id.
     *  1. copy image into filesDir/images
     *  2. OCR
     *  3. gemma.extractRecipe(ocrText); if null or no ingredients -> gemma.extractRecipeFromImage(file);
     *     if still null -> a simple offline parser of the OCR text (lines between "ingredients" and
     *     "steps/instructions/directions/procedure" headings, or bullet/number-led lines) so the demo never dead-ends
     *  4. recipes.saveNew(...)
     *  Result.failure with a user-readable message when nothing usable was found. */
    suspend fun process(imageUri: android.net.Uri, onStage: (ScanStage) -> Unit = {}): Result<Long>
}
```

---

## Part C — UI (package `com.substichoose.ui`) — owner: UI agent

Entry point used by MainActivity:
```kotlin
package com.substichoose.ui
@Composable fun SubstiChooseRoot(container: com.substichoose.AppContainer)
```

Screens (Compose Navigation): My Allergies (onboarding on first launch + editable later), Home (recipe list / empty state, centered camera FAB), Scan (CameraCapture + gallery picker + progress stages), Review Ingredients (editable rows, red allergen flags, "Don't have it" toggles), Substitutes (bottom sheet or screen: curated instantly, AI below with a loading state, pick one), Recipe (adapted view: swaps applied, steps, disclaimer), plus a model status banner when `gemma.state` is Missing/Failed with adb instructions.

Visual spec (from the approved mockups): background #F5F6F1, cards #FFFFFF radius 18dp, text #18201B, secondary #55605A, accent green #2F6B3F, borders #E1E4DC. Badges: allergen orange bg #FFE8D9 / text #9A3412; safe green bg #E1F0E3 / text #1F5130; swapped blue bg #E4ECF7 / text #1E4A7A. Titles 30sp ExtraBold. Camera button: 72dp green circle with a camera icon, bottom center, label "Scan recipe". Allergy chip top-right on Home showing the user's allergies.
