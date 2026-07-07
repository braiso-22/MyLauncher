package com.braiso22.mylauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.braiso22.mylauncher.domain.timebank.TimeBankAnchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant

private val Context.timeBankDataStore: DataStore<Preferences> by preferencesDataStore(name = "time_bank_prefs")

/**
 * Persists the time-bank [TimeBankAnchor] checkpoint. The anchor is `null` until the bank
 * is first initialized (see [TimeBankManager]); this repository only stores and exposes it.
 */
class TimeBankRepository private constructor(context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: TimeBankRepository? = null

        fun getInstance(context: Context): TimeBankRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: TimeBankRepository(context.applicationContext).also { INSTANCE = it }
            }

        private val KEY_ANCHOR = stringPreferencesKey("timebank_anchor")
    }

    private val dataStore = context.applicationContext.timeBankDataStore
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val anchor: StateFlow<TimeBankAnchor?> = dataStore.data
        .map { prefs -> prefs[KEY_ANCHOR]?.let(::deserialize) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun save(anchor: TimeBankAnchor) {
        dataStore.edit { prefs -> prefs[KEY_ANCHOR] = serialize(anchor) }
    }

    /** `"epochMillis:balanceMillis"`. Tolerant of corrupt values (returns null). */
    private fun serialize(anchor: TimeBankAnchor): String =
        "${anchor.lastActivityEnd.toEpochMilli()}:${anchor.balance.toMillis()}"

    private fun deserialize(raw: String): TimeBankAnchor? {
        val parts = raw.split(":")
        if (parts.size != 2) return null
        val at = parts[0].toLongOrNull() ?: return null
        val balance = parts[1].toLongOrNull() ?: return null
        return TimeBankAnchor(Instant.ofEpochMilli(at), Duration.ofMillis(balance))
    }
}
