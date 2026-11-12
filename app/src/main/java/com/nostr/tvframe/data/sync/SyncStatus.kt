package com.nostr.tvframe.data.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/** What started a sync. Shown next to the "last sync" time so it is clear where photos came from. */
enum class SyncTrigger(val label: String) {
    MANUAL("manual"),
    APP_START("app opened"),
    FILTER_CHANGE("settings changed"),
    /** No longer used since 1.3.1; kept so sync records saved by 1.3.0 still read back. */
    AFTER_CLEAR("after clearing"),
    SCREENSAVER("screensaver"),
    BACKGROUND("automatic")
}

enum class SyncPhase {
    IDLE,
    SEARCHING,
    DOWNLOADING,
    SCANNING_LOCAL,
    DONE,
    ERROR
}

/**
 * Live state of the current (or most recent) sync. [progressDone]/[progressTotal] are only
 * meaningful while [phase] is [SyncPhase.DOWNLOADING].
 */
data class SyncStatus(
    val phase: SyncPhase = SyncPhase.IDLE,
    val trigger: SyncTrigger = SyncTrigger.MANUAL,
    val queryLabel: String = "",
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val newPhotos: Int = 0,
    val message: String = ""
) {
    val isRunning: Boolean
        get() = phase == SyncPhase.SEARCHING || phase == SyncPhase.DOWNLOADING || phase == SyncPhase.SCANNING_LOCAL
}

/** Result of the last finished sync, persisted so it survives app restarts and background runs. */
data class LastSyncRecord(
    val finishedAtEpochMs: Long,
    val trigger: SyncTrigger,
    val newPhotos: Int,
    val message: String,
    val isError: Boolean
)

/** Summary returned by a sync run. */
data class SyncReport(
    val newPhotos: Int,
    val queryLabel: String,
    val message: String
)

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "tvframe_sync_state")

/**
 * Process-wide holder for sync progress. Both the UI ("Sync New") and the background
 * PrefetchWorker go through the same repository, so the Gallery shows background runs too.
 */
class SyncStatusTracker(private val context: Context) {

    private object Keys {
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
        val LAST_SYNC_TRIGGER = stringPreferencesKey("last_sync_trigger")
        val LAST_SYNC_NEW = intPreferencesKey("last_sync_new")
        val LAST_SYNC_MESSAGE = stringPreferencesKey("last_sync_message")
        val LAST_SYNC_ERROR = booleanPreferencesKey("last_sync_error")
        val LAST_CLEAR_AT = longPreferencesKey("last_clear_at")
    }

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    val lastSync: Flow<LastSyncRecord?> = context.syncDataStore.data.map { prefs ->
        val at = prefs[Keys.LAST_SYNC_AT] ?: return@map null
        LastSyncRecord(
            finishedAtEpochMs = at,
            trigger = runCatching { SyncTrigger.valueOf(prefs[Keys.LAST_SYNC_TRIGGER] ?: "") }
                .getOrDefault(SyncTrigger.MANUAL),
            newPhotos = prefs[Keys.LAST_SYNC_NEW] ?: 0,
            message = prefs[Keys.LAST_SYNC_MESSAGE] ?: "",
            isError = prefs[Keys.LAST_SYNC_ERROR] ?: false
        )
    }

    /** When "Clear Non-Favorites" last ran, or null if never. */
    val lastClearEpochMs: Flow<Long?> = context.syncDataStore.data.map { it[Keys.LAST_CLEAR_AT] }

    suspend fun markCleared() {
        context.syncDataStore.edit { it[Keys.LAST_CLEAR_AT] = System.currentTimeMillis() }
    }

    fun update(transform: (SyncStatus) -> SyncStatus) {
        _status.value = transform(_status.value)
    }

    fun start(trigger: SyncTrigger, queryLabel: String) {
        _status.value = SyncStatus(
            phase = SyncPhase.SEARCHING,
            trigger = trigger,
            queryLabel = queryLabel,
            message = "Searching for new photos…"
        )
    }

    /** Shows a note in the status card (e.g. "press Sync to apply") unless a sync is running. */
    fun hint(message: String) {
        if (_status.value.isRunning) return
        _status.value = SyncStatus(phase = SyncPhase.IDLE, message = message)
    }

    suspend fun finish(trigger: SyncTrigger, newPhotos: Int, message: String, isError: Boolean) {
        _status.value = _status.value.copy(
            phase = if (isError) SyncPhase.ERROR else SyncPhase.DONE,
            trigger = trigger,
            newPhotos = newPhotos,
            message = message
        )
        context.syncDataStore.edit { prefs ->
            prefs[Keys.LAST_SYNC_AT] = System.currentTimeMillis()
            prefs[Keys.LAST_SYNC_TRIGGER] = trigger.name
            prefs[Keys.LAST_SYNC_NEW] = newPhotos
            prefs[Keys.LAST_SYNC_MESSAGE] = message
            prefs[Keys.LAST_SYNC_ERROR] = isError
        }
    }
}
