package com.substichoose.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.substichoose.safety.AllergyProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

/** One DataStore file ("settings") for the whole app. Must stay top-level so it is a singleton. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** The user's allergy profile and onboarding flag, stored with DataStore Preferences. */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val ALLERGEN_IDS = stringSetPreferencesKey("allergen_ids")
        val CUSTOM_TERMS = stringSetPreferencesKey("custom_terms")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    /** Preferences, or empty ones if the file can't be read (never crashes the UI). */
    private val prefs: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    /** What the user avoids. Empty profile until they finish onboarding. */
    val profile: Flow<AllergyProfile> = prefs
        .map { p ->
            AllergyProfile(
                allergenIds = p[Keys.ALLERGEN_IDS].orEmpty(),
                customTerms = p[Keys.CUSTOM_TERMS].orEmpty(),
            )
        }
        .distinctUntilChanged()

    /** True once the user has saved their allergies at least once. */
    val onboardingDone: Flow<Boolean> = prefs
        .map { p -> p[Keys.ONBOARDING_DONE] ?: false }
        .distinctUntilChanged()

    /** Saves the profile (custom terms trimmed, blanks dropped) and marks onboarding as done. */
    suspend fun saveProfile(profile: AllergyProfile) {
        withContext(Dispatchers.IO) {
            dataStore.edit { p ->
                p[Keys.ALLERGEN_IDS] = profile.allergenIds
                p[Keys.CUSTOM_TERMS] = profile.customTerms
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
                p[Keys.ONBOARDING_DONE] = true
            }
        }
    }
}
