package com.braiso22.mylauncher.domain

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppRepository private constructor(context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: AppRepository? = null

        fun getInstance(context: Context): AppRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        private val KEY_FAVORITES = stringSetPreferencesKey("favorites")
        private val KEY_BLOCKED = stringSetPreferencesKey("blocked")
        private val KEY_LAST_OPENED = stringPreferencesKey("last_opened")
        private val KEY_BLOCK_TIMES = stringPreferencesKey("block_times")
        private val KEY_UNLOCK_EXPIRIES = stringPreferencesKey("unlock_expiries")
        private val KEY_TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")
    }

    private val dataStore = context.applicationContext.dataStore
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val favorites: StateFlow<Set<String>> = dataStore.data
        .map { prefs -> prefs[KEY_FAVORITES] ?: emptySet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val blocked: StateFlow<Set<String>> = dataStore.data
        .map { prefs -> prefs[KEY_BLOCKED] ?: emptySet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val lastOpenedPackage: StateFlow<String?> = dataStore.data
        .map { prefs -> prefs[KEY_LAST_OPENED] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val tutorialCompleted: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[KEY_TUTORIAL_COMPLETED] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** Map of packageName -> allowed minutes before overlay */
    val blockTimes: StateFlow<Map<String, Int>> = dataStore.data
        .map { prefs ->
            val raw = prefs[KEY_BLOCK_TIMES] ?: ""
            if (raw.isBlank()) emptyMap()
            else raw.split(",").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 5)
                else null
            }.toMap()
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Map of packageName -> expiry timestamp (millis) for temporarily unlocked apps */
    val unlockExpiries: StateFlow<Map<String, Long>> = dataStore.data
        .map { prefs ->
            val raw = prefs[KEY_UNLOCK_EXPIRIES] ?: ""
            if (raw.isBlank()) emptyMap()
            else raw.split(",").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L)
                else null
            }.toMap()
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun setLastOpened(packageName: String) {
        scope.launch {
            dataStore.edit { prefs -> prefs[KEY_LAST_OPENED] = packageName }
        }
    }

    fun setTutorialCompleted(completed: Boolean) {
        scope.launch {
            dataStore.edit { prefs -> prefs[KEY_TUTORIAL_COMPLETED] = completed }
        }
    }

    fun toggleFavorite(packageName: String) {
        scope.launch {
            dataStore.edit { prefs ->
                val current = prefs[KEY_FAVORITES] ?: emptySet()
                prefs[KEY_FAVORITES] =
                    if (packageName in current) current - packageName else current + packageName
            }
        }
    }

    /** Block an app with a specific allowed time in minutes */
    fun blockApp(packageName: String, allowedMinutes: Int) {
        scope.launch {
            dataStore.edit { prefs ->
                val currentBlocked = prefs[KEY_BLOCKED] ?: emptySet()
                prefs[KEY_BLOCKED] = currentBlocked + packageName

                val raw = prefs[KEY_BLOCK_TIMES] ?: ""
                val map = parseBlockTimes(raw).toMutableMap()
                map[packageName] = allowedMinutes
                prefs[KEY_BLOCK_TIMES] = serializeBlockTimes(map)
            }
        }
    }

    /** Unblock an app */
    fun unblockApp(packageName: String) {
        scope.launch {
            dataStore.edit { prefs ->
                val currentBlocked = prefs[KEY_BLOCKED] ?: emptySet()
                prefs[KEY_BLOCKED] = currentBlocked - packageName

                val raw = prefs[KEY_BLOCK_TIMES] ?: ""
                val map = parseBlockTimes(raw).toMutableMap()
                map.remove(packageName)
                prefs[KEY_BLOCK_TIMES] = serializeBlockTimes(map)

                // Also remove any active unlock for this app
                val unlockRaw = prefs[KEY_UNLOCK_EXPIRIES] ?: ""
                val unlockMap = parseUnlockExpiries(unlockRaw).toMutableMap()
                unlockMap.remove(packageName)
                prefs[KEY_UNLOCK_EXPIRIES] = serializeUnlockExpiries(unlockMap)
            }
        }
    }

    /** Record that the user unlocked a blocked app, setting an expiry timestamp */
    fun markAppUnlocked(packageName: String, allowedMinutes: Int) {
        scope.launch {
            dataStore.edit { prefs ->
                val raw = prefs[KEY_UNLOCK_EXPIRIES] ?: ""
                val map = parseUnlockExpiries(raw).toMutableMap()
                map[packageName] = System.currentTimeMillis() + allowedMinutes * 60_000L
                prefs[KEY_UNLOCK_EXPIRIES] = serializeUnlockExpiries(map)
            }
        }
    }

    /** Check if an app is currently unlocked (has a non-expired unlock) */
    fun isAppUnlocked(packageName: String): Boolean {
        val expiry = unlockExpiries.value[packageName] ?: return false
        return expiry > System.currentTimeMillis()
    }

    /** Clear the unlock for a specific app */
    fun clearUnlock(packageName: String) {
        scope.launch {
            dataStore.edit { prefs ->
                val raw = prefs[KEY_UNLOCK_EXPIRIES] ?: ""
                val map = parseUnlockExpiries(raw).toMutableMap()
                map.remove(packageName)
                prefs[KEY_UNLOCK_EXPIRIES] = serializeUnlockExpiries(map)
            }
        }
    }

    /** Remove all expired unlocks from the map */
    fun clearExpiredUnlocks() {
        scope.launch {
            dataStore.edit { prefs ->
                val raw = prefs[KEY_UNLOCK_EXPIRIES] ?: ""
                val map = parseUnlockExpiries(raw).toMutableMap()
                val now = System.currentTimeMillis()
                val cleaned = map.filterValues { it > now }
                prefs[KEY_UNLOCK_EXPIRIES] = serializeUnlockExpiries(cleaned)
            }
        }
    }

    private fun parseBlockTimes(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 5)
            else null
        }.toMap()
    }

    private fun serializeBlockTimes(map: Map<String, Int>): String {
        return map.entries.joinToString(",") { "${it.key}:${it.value}" }
    }

    private fun parseUnlockExpiries(raw: String): Map<String, Long> {
        if (raw.isBlank()) return emptyMap()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L)
            else null
        }.toMap()
    }

    private fun serializeUnlockExpiries(map: Map<String, Long>): String {
        return map.entries.joinToString(",") { "${it.key}:${it.value}" }
    }
}
