package com.braiso22.mylauncher.domain

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
        private val KEY_BLOCKED_APP_OPENED_AT = longPreferencesKey("blocked_app_opened_at")
        private val KEY_BLOCKED_APP_OPENED_PKG = stringPreferencesKey("blocked_app_opened_pkg")
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

    /** Package name of the blocked app currently being used (opened via "Entrar") */
    val blockedAppOpenedPkg: StateFlow<String?> = dataStore.data
        .map { prefs -> prefs[KEY_BLOCKED_APP_OPENED_PKG] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Timestamp when the blocked app was opened */
    val blockedAppOpenedAt: StateFlow<Long> = dataStore.data
        .map { prefs -> prefs[KEY_BLOCKED_APP_OPENED_AT] ?: 0L }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

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

                if (prefs[KEY_BLOCKED_APP_OPENED_PKG] == packageName) {
                    prefs.remove(KEY_BLOCKED_APP_OPENED_PKG)
                    prefs.remove(KEY_BLOCKED_APP_OPENED_AT)
                }
            }
        }
    }

    /** Record that the user opened a blocked app (so the service starts its timer) */
    fun markBlockedAppOpened(packageName: String) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_BLOCKED_APP_OPENED_PKG] = packageName
                prefs[KEY_BLOCKED_APP_OPENED_AT] = System.currentTimeMillis()
            }
        }
    }

    /** Clear the opened blocked app tracking */
    fun clearBlockedAppOpened() {
        scope.launch {
            dataStore.edit { prefs ->
                prefs.remove(KEY_BLOCKED_APP_OPENED_PKG)
                prefs.remove(KEY_BLOCKED_APP_OPENED_AT)
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
}
