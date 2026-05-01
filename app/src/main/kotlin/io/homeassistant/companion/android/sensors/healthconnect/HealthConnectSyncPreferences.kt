package io.homeassistant.companion.android.sensors.healthconnect

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.di.qualifiers.NamedHealthConnectStorage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight read/write facade over the Health Connect [LocalStorage] for cross-cutting
 * preferences that don't fit anywhere else (currently: the real-time-sync opt-in flag).
 *
 * Token persistence lives in [HealthConnectChangesTokenStore] — this class is intentionally
 * separate so the storage interleaving stays readable when more knobs land (e.g. a per-data-type
 * "allow writes from HA" flag in Commit D).
 */
@Singleton
class HealthConnectSyncPreferences @Inject constructor(@NamedHealthConnectStorage private val storage: LocalStorage) {

    /** Whether the user opted into the [HealthConnectChangesWorker] cadence. Default: false. */
    suspend fun isRealtimeSyncEnabled(): Boolean = storage.getBoolean(KEY_REALTIME_SYNC)

    suspend fun setRealtimeSyncEnabled(enabled: Boolean) {
        storage.putBoolean(KEY_REALTIME_SYNC, enabled)
    }

    companion object {
        const val KEY_REALTIME_SYNC = "realtime_sync_enabled"
    }
}
