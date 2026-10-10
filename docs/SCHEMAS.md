# SubstiChoose — shared JSON shapes

Everyone codes to these shapes. The Kotlin versions live in `app/src/main/java/com/substichoose/model/Models.kt`.

## Allergen ids

Use these exact ids everywhere: the My Allergies screen, DataStore, `contains` fields and prompts.

| id | Label on screen |
| --- | --- |
| `peanut` | Peanut |
| `treenuts` | Tree nuts |
| `dairy` | Milk / dairy |
| `egg` | Egg |
| `gluten` | Wheat / gluten |
| `soy` | Soy |
| `fish` | Fish |
| `shellfish` | Shellfish |
| `sesame` | Sesame |

Items the user types under "Your own" (e.g. "Coconut") are stored as plain text in `AllergyProfile.customTerms`.

## Job 1 — Extract ingredients (Gemma reply → `ExtractedRecipe`)

```json
{
  "title": "Banana Bread",
  "ingredients": [
    {"qty": "1", "unit": "pcs", "name": "egg", "raw": "1 large egg, beaten"},
    {"qty": "1 1/2", "unit": "cup", "name": "all-purpose flour", "raw": "1 1/2 cups all-purpose flour"}
  ],
  "steps": ["Preheat the oven to 175°C.", "Mix the bananas and butter."]
}
```

- `qty` is a string so "1 1/2" and "a pinch" survive. Empty string when missing.
- `name` is the plain ingredient (no amount, no prep words). `raw` is the line as it was written.

## Job 2 — Substitutes (Gemma reply → `SubstitutesResponse`)

```json
{
  "ingredient": "butter",
  "options": [
    {
      "name": "Vegetable oil",
      "ratio": "1 cup butter = 3/4 cup vegetable oil",
      "effect": "Moister, less buttery flavor",
      "note": "Not for flaky pastry",
      "contains": []
    }
  ]
}
```

- `ratio`: the original ingredient goes on the LEFT of `=`. The safety filter checks only the right side.
- `contains`: allergen ids the substitute itself contains. Gemma fills it, and the Kotlin filter double-checks the words anyway.
- `source` is set by the app (`"ai"` or `"curated"`), not by Gemma.

## Job 3 — Adapt steps, stretch goal (Gemma reply → `AdaptedSteps`)

```json
{
  "steps": [
    {"text": "Mix 1 tbsp ground flaxseed with 3 tbsp water and rest 5 minutes.", "changed": true},
    {"text": "Preheat the oven to 175°C.", "changed": false}
  ]
}
```

## Prompt snippets for P1

Extraction system prompt:

```text
You are a cooking assistant. Read the recipe text and reply with JSON only, no other words.
Use exactly this shape:
{"title": "...", "ingredients": [{"qty": "...", "unit": "...", "name": "...", "raw": "..."}], "steps": ["..."]}
Use "" when a qty or unit is missing. "name" is the ingredient alone, without amounts or preparation.
```

Substitutes system prompt (fill in the {placeholders}):

```text
You are a cooking assistant. Suggest 2 or 3 substitutes for "{ingredient}" in "{recipe title}".
Reason: {"the user is allergic to it" | "the user doesn't have it"}.
The user must avoid: {allergy labels, comma-separated}. Never suggest anything containing them.
Reply with JSON only, in exactly this shape:
{"ingredient": "...", "options": [{"name": "...", "ratio": "<original amount> = <substitute amount>", "effect": "...", "note": "...", "contains": []}]}
"contains" lists any of these ids the substitute contains: peanut, treenuts, dairy, egg, gluten, soy, fish, shellfish, sesame.
```

## Parsing rules

1. Parse with `ModelJson.decodeOrNull<T>(reply)`. It strips ```json fences and chatter.
2. On `null`, retry once with: "Your last reply was not valid JSON. Reply with the JSON only."
3. Still `null`: show the curated swaps (`CuratedSwaps.safeOptionsFor`) or the raw text.
4. ALWAYS pass AI options through `AllergenChecker.safeOptions(options, profile)` before showing them.
