package com.braiso22.mylauncher.domain

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

class AppRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private val KEY_FAVORITES = stringSetPreferencesKey("favorites")
        private val KEY_BLOCKED = stringSetPreferencesKey("blocked")
        private val KEY_LAST_OPENED = stringPreferencesKey("last_opened")
    }

    val favorites: StateFlow<Set<String>> = dataStore.data
        .map { prefs -> prefs[KEY_FAVORITES] ?: emptySet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val blocked: StateFlow<Set<String>> = dataStore.data
        .map { prefs -> prefs[KEY_BLOCKED] ?: emptySet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val lastOpenedPackage: StateFlow<String?> = dataStore.data
        .map { prefs -> prefs[KEY_LAST_OPENED] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun setLastOpened(packageName: String) {
        scope.launch {
            dataStore.edit { prefs -> prefs[KEY_LAST_OPENED] = packageName }
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

    fun toggleBlocked(packageName: String) {
        scope.launch {
            dataStore.edit { prefs ->
                val current = prefs[KEY_BLOCKED] ?: emptySet()
                prefs[KEY_BLOCKED] =
                    if (packageName in current) current - packageName else current + packageName
            }
        }
    }

    fun isFavorite(packageName: String): Boolean = packageName in favorites.value
    fun isBlocked(packageName: String): Boolean = packageName in blocked.value
}
