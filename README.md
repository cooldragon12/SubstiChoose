# SubstiChoose

Snap a recipe, see what you're allergic to, and get safe substitutes, fully offline. Gemma 4 E2B runs on the phone through Google's LiteRT-LM.

## Install the app on a phone

You don't need a computer for this. The latest APK is `dist/SubstiChoose-0.3.apk` (48 MB).

**Needs:** an Android phone with Android 8.0 or newer and a 64-bit processor (almost every phone from the last several years), about 3 GB of free space for the AI model, and Wi-Fi for the first launch.

1. **Get the APK onto the phone.** Send it any way you like (Google Drive, Messenger, email, USB cable) and download it on the phone.
2. **Open the APK.** Tap it in your notifications, or find it in the **Files** app under **Downloads**.
3. **Allow the install.** Android asks for permission to install apps from that source (for example "Allow from this source" for Files or Chrome). Turn it on and go back. Android may also warn that the app is unknown to Google Play Protect; tap **More details → Install anyway**. The app isn't on the Play Store, so this is expected.
4. **Tap Install**, then **Open**.
5. **First launch:**
   - Pick your allergies and tap **Save**.
   - The Gemma AI model (2.6 GB) downloads by itself on Wi-Fi. The Home screen shows its progress; on a fast connection it takes a couple of minutes. To download on mobile data instead, tap **Use mobile data** on the banner.
   - You can scan recipes right away. Until Gemma is ready, the app uses a simpler text reader and its built-in swaps.
   - Allow camera access when you first tap **Scan recipe**.

### Updating to a new version

Open the new APK and tap **Update**. Your recipes, allergies and the downloaded model are kept.

### If the install fails

- **"App not installed as package conflicts with an existing package":** the phone has a copy built on a developer's computer (installed with `./gradlew installDebug`). It's signed with a different key, so Android won't replace it. Uninstall SubstiChoose first, then install the APK. Uninstalling deletes that copy's recipes and the downloaded model; the model downloads again on first launch.
  - On Xiaomi phones, also remove it from **Second space** / **security space** if it shows up there.
- **"App not installed" with no reason:** the phone may be 32-bit only, older than Android 8.0, or the download was incomplete. Download the APK again.
- **The Gemma download doesn't start:** connect to Wi-Fi or tap **Use mobile data**. If the banner says there isn't enough space, free up the amount it shows and tap **Try again**.

To check the APK isn't corrupted, compare its SHA-256 with the one given by whoever built it (`shasum -a 256 SubstiChoose-0.3.apk` on a Mac).

## What's inside

| Folder (`app/src/main/java/com/substichoose/`) | What it does |
| --- | --- |
| `ui/` | All screens: My Allergies, Home, Scan, Review Ingredients, Substitutes sheet, Recipe |
| `camera/` | CameraX preview and shutter (handles the camera permission) |
| `ocr/` | ML Kit on-device text recognition (fallback; Gemma vision reads the photo first, see `ai/GemmaService.readImageText`) |
| `ai/` | Gemma via LiteRT-LM: extract ingredients, suggest substitutes, adapt steps |
| `pipeline/` | Photo → text → Gemma → saved recipe, with an offline parser as the last fallback |
| `data/` | Room database (recipes, ingredients) and DataStore (allergies) |
| `safety/` + `assets/` | Allergen dictionary, 24 curated swaps, and the filter that removes unsafe suggestions |
| `model/` | Shared JSON shapes |

More: `docs/CONTRACTS.md` (how the parts connect), `docs/SCHEMAS.md` (JSON + prompts), `docs/P4-SAFETY-KIT.md`.

## 1. Build (VS Code)

You need JDK 21 (17–23 works; 27 does not) and the Android SDK (platform 35, build-tools 35).

1. Create `local.properties` in this folder with your SDK path, e.g.
   `sdk.dir=/Users/johndel/Library/Android/sdk`
2. In the VS Code terminal, from this folder:
   ```
   chmod +x gradlew
   ./gradlew assembleDebug
   ```
   The first build downloads Gradle and the libraries (needs internet, a few minutes).
   Optional: add `USDA_API_KEY=...` to `local.properties` for nutrition values (free key from api.data.gov; without it the shared `DEMO_KEY` runs out quickly).
3. Plug in the demo phone (USB debugging on) and install:
   ```
   ./gradlew installDebug
   ```

## 2. Put Gemma on the phone

The model (about 2.6 GB) is not in the APK. **The app downloads it by itself** on first launch: Gemma 4 E2B (`gemma-4-E2B-it.litertlm`, Apache 2.0) from Hugging Face's public `litert-community/gemma-4-E2B-it-litert-lm` repo (`ai/ModelDownloader.kt`).

- It waits for Wi-Fi; the Home banner has a **Use mobile data** button.
- Android's download manager runs it, so it continues outside the app and shows progress in the notification shade.
- The finished file is checked (size + SHA-256) before Gemma loads. A damaged download is deleted and the banner offers **Try again**.
- About 2.9 GB of free space is needed.

To skip the download (e.g. on a test phone), push the file yourself. Open the app once first so Android creates the folder:

```
adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.substichoose/files/
```

Any file ending in `.litertlm` in that folder is picked up; restart the app after pushing.

Without the model the app still works: curated swaps appear instantly and recipes are parsed by the offline parser.

## 3. Test

- Unit tests (allergens, swaps, test recipes): `./gradlew :app:testDebugUnitTest`
- Test recipes to photograph: `app/src/test/resources/recipes/` (print them or show them on a laptop screen).

## 4. Build a release APK to share

Release builds are signed with the team's key in `release.jks`, with the passwords in `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Both are git-ignored. **Back them up:** a phone only accepts an update signed with the same key, and if the key is lost, everyone has to uninstall and lose their data. Without `keystore.properties` the build falls back to the debug key.

1. Raise the version in `app/build.gradle.kts`: `versionCode` by 1 (Android needs a higher number to update) and `versionName` (e.g. `"0.4-hackathon"`).
2. Build:
   ```
   ./gradlew assembleRelease
   ```
3. Copy `app/build/outputs/apk/release/app-release.apk` to `dist/SubstiChoose-<version>.apk` and share that file, with its checksum: `shasum -a 256 dist/SubstiChoose-<version>.apk`.

The release APK is 64-bit only (`arm64-v8a`), which halves its size. The `USDA_API_KEY` from `local.properties` is built into the APK, so anyone with the file can extract it.

To test a release build on a phone that has a debug build, uninstall the debug build first (see "If the install fails").

## Build status (Oct 10)

Builds and runs on the demo phone (Xiaomi 14T, Android 16). All 28 unit tests pass, and all 6 test recipes flag exactly the allergens in `expected.json` when scanned on the phone with Gemma.

- **Use JDK 17–23 (JDK 21 recommended).** Gradle 8.11 fails with just "27" on JDK 27. `build-and-run.command` picks a working JDK automatically.
- **SDK:** the Homebrew SDK at `/opt/homebrew/share/android-commandlinetools` with platform 35 + build-tools 35.
- **LiteRT-LM is pinned to 0.18.0.**
- **Xiaomi phones:** turn on *Install via USB* and *USB debugging (Security settings)* in Developer options.
- **Model:** if AI Edge Gallery already has Gemma, copy it on the phone instead of pushing 2.6 GB:
  `adb shell cp /sdcard/Android/data/com.google.ai.edge.gallery/files/Gemma_4_E2B_it/*/gemma-4-E2B-it.litertlm /sdcard/Android/data/com.substichoose/files/`
- **Reading photos:** Gemma vision reads the photo first (it handles handwriting); ML Kit OCR is the fallback when Gemma isn't ready.
- **Speed on this phone:** a scan takes about 30–60 s. The offline parser splits the ingredient lines and steps instantly; Gemma only names each line (a short reply, about 15–20 s at roughly 6 tokens/s on the GPU). Pages where the parser can't find an ingredient list fall back to full Gemma extraction (about 70–120 s). Gemma's substitutes take about 30 s; curated swaps show instantly.

## Demo checklist

1. Set Egg + Dairy in My Allergies.
2. Airplane mode on.
3. Scan the printed banana bread recipe → eggs and butter flagged.
4. Tap egg → pick Flax egg. Toggle "Don't have it" on baking soda → baking powder.
5. Done → show the adapted recipe → back to My Recipes.

AI suggestions can be wrong; the app always shows "Check labels — AI suggestions may be wrong."
