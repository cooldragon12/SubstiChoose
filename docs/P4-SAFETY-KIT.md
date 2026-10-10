# SubstiChoose — P4 kit (safety, content and test recipes)

Everything P4 owns, ready to drop into the Android project.

| File | What it is |
| --- | --- |
| `docs/SCHEMAS.md` | The shared JSON shapes, allergen ids and prompt text. Share this with the whole team in hour 0. |
| `app/src/main/assets/allergens.json` | Allergen dictionary: 9 allergens, English and Filipino keywords (patis, bagoong, toyo, keso…), plus "exclude" phrases so `peanut butter` isn't dairy and `eggplant` isn't egg. |
| `app/src/main/assets/substitutes.json` | 24 curated swaps (egg, butter, milk, flour, soy sauce, patis, bagoong, oyster sauce…), each with ratio, effect, note and the allergens it contains. |
| `app/src/main/java/com/substichoose/safety/AllergenChecker.kt` | Flags allergens in any text and filters out unsafe substitutes. |
| `app/src/main/java/com/substichoose/safety/CuratedSwaps.kt` | Instant lookup of curated swaps for an ingredient, already filtered for the user. |
| `app/src/main/java/com/substichoose/model/Models.kt` | Kotlin data classes for every JSON shape, plus `ModelJson` to parse Gemma replies. |
| `app/src/test/…/AllergenCheckerTest.kt` | Unit tests: the 6 recipes, tricky phrases, the filter, and a messy AI reply. |
| `app/src/test/resources/recipes/` | 6 test recipes + `expected.json` (the allergens each should flag). Print them or show them on a laptop screen to test the camera. |

## Setup

1. Copy the `app/` folder over your project's `app/` folder (it only adds files).
2. If your package isn't `com.substichoose`, change the `package` line at the top of the 3 Kotlin files (and the test), and move them to match.
3. In `app/build.gradle.kts`, make sure you have the serialization plugin and library:

```kotlin
plugins {
    kotlin("plugin.serialization") version "<your Kotlin version>"
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}
```

4. Run the tests from the project folder: `./gradlew :app:testDebugUnitTest`

## How the app uses it

```kotlin
val checker = AllergenChecker.fromAssets(context)
val swaps = CuratedSwaps.fromAssets(context)

// From DataStore: ticked ids from My Allergies + what the user typed under "Your own"
val profile = AllergyProfile(allergenIds = setOf("egg", "dairy"), customTerms = setOf("Coconut"))

// Review Ingredients screen: red flag per row
val hits = checker.conflicts(ingredient.raw, profile)        // e.g. {"egg"}
val isAllergen = hits.isNotEmpty()

// Substitutes screen: show curated swaps instantly...
val instant = swaps.safeOptionsFor(ingredient.name, checker, profile)

// ...then Gemma's answer, ALWAYS filtered
val ai = ModelJson.decodeOrNull<SubstitutesResponse>(reply)
val shown = ai?.let { checker.safeOptions(it.options, profile) } ?: instant
```

Labels for badges and chips: `checker.labels["dairy"]` → "Milk / dairy".

## Adding a keyword or swap

- New allergen word: add it to that allergen's `keywords` in `allergens.json`. If it causes a false alarm, add the longer harmless phrase to `exclude` (e.g. `coconut milk` under dairy).
- New swap: add an entry to `substitutes.json`. Keep the original ingredient on the left of `=` in `ratio`, and list every allergen the substitute contains in `contains`.
- Re-run the unit tests after any change.

## What was checked

The matching code was compiled with Kotlin 2.0 and run against both JSON files: all 6 recipes flag exactly their expected allergens, all tricky phrases pass, and every curated swap's `contains` covers what its own words say. The Gradle test run itself hasn't been done yet, because the Kotlin serialization library couldn't be downloaded in that environment.

These lists are a safety net for a demo, not medical advice. The app should keep its "Check labels — AI suggestions may be wrong" note.
