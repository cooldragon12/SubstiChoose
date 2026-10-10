package com.substichoose.nutrition

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.substichoose.data.NutritionDao
import com.substichoose.data.NutritionEntity
import com.substichoose.model.ModelJson
import com.substichoose.model.SubstituteOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Nutrition per 100 g, fetched quietly in the background whenever the phone is online and cached
 * in Room. Screens just observe [all]; values appear when they arrive. Never throws.
 */
class NutritionRepository(
    context: Context,
    private val dao: NutritionDao,
    private val client: UsdaClient,
    private val scope: CoroutineScope,
) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val prefs = context.applicationContext.getSharedPreferences("nutrition", Context.MODE_PRIVATE)
    private val lock = Mutex()
    @Volatile private var pausedUntil = 0L

    /** Cached nutrition by [key]. */
    val all: Flow<Map<String, NutritionEntity>> = dao.observeAll().map { rows -> rows.associateBy { it.key } }

    /** Starts watching connectivity: every time the phone comes online, fill in anything missing. */
    fun start() {
        runCatching {
            connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) prefetchAll()
                }
            })
        }.onFailure { Log.w(TAG, "Couldn't watch connectivity", it) }
    }

    /** Looks up any of these ingredient names not cached yet (fire and forget). */
    fun prefetch(names: Collection<String>) {
        val keys = names.map(::key).filter { it.isNotEmpty() }.distinct()
        if (keys.isEmpty()) return
        scope.launch(Dispatchers.IO) { fetchMissing(keys) }
    }

    /** Every ingredient and chosen substitute in the database. */
    fun prefetchAll() {
        scope.launch(Dispatchers.IO) {
            val names = runCatching {
                dao.allIngredientNames() + dao.allChosenSubstitutes().mapNotNull { json ->
                    runCatching { ModelJson.json.decodeFromString(SubstituteOption.serializer(), json).name }.getOrNull()
                }
            }.getOrDefault(emptyList())
            fetchMissing(names.map(::key).filter { it.isNotEmpty() }.distinct())
        }
    }

    private suspend fun fetchMissing(keys: List<String>) = lock.withLock {
        // Matching rules changed since these were cached: drop them once so they're looked up again.
        if (prefs.getInt(PREF_MATCHER, 0) < MATCHER_VERSION) {
            runCatching { dao.clear() }.onSuccess { prefs.edit().putInt(PREF_MATCHER, MATCHER_VERSION).apply() }
        }
        if (!isOnline()) return@withLock
        val cached = runCatching { dao.keys().toSet() }.getOrDefault(emptySet())
        for (key in keys) {
            if (key in cached) continue
            if (System.currentTimeMillis() < pausedUntil) return@withLock
            when (val result = client.lookup(key)) {
                is UsdaClient.Result.Found -> dao.upsert(result.entity)
                UsdaClient.Result.NotFound -> dao.upsert(NutritionEntity(key = key, found = false))
                UsdaClient.Result.RateLimited -> {
                    Log.w(TAG, "USDA rate limit reached; pausing nutrition lookups for an hour")
                    pausedUntil = System.currentTimeMillis() + PAUSE_MS
                    return@withLock
                }
                // Network hiccup: leave it uncached so the next chance retries it.
                is UsdaClient.Result.Failed -> Log.w(TAG, "Lookup failed for \"$key\": ${result.reason}")
            }
        }
    }

    private fun isOnline(): Boolean = runCatching {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    companion object {
        private const val TAG = "Nutrition"
        private const val PAUSE_MS = 60 * 60 * 1000L
        private const val PREF_MATCHER = "matcherVersion"

        /** Bump when NutritionMatcher's rules change, so cached matches are redone. */
        private const val MATCHER_VERSION = 5

        fun key(name: String): String = name.trim().lowercase()
    }
}
