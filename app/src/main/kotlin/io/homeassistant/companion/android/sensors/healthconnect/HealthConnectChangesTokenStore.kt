package io.homeassistant.companion.android.sensors.healthconnect

import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.di.qualifiers.NamedHealthConnectStorage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists the Health Connect Changes API token for each [HealthConnectDataType].
 *
 * Tokens are opaque cursor strings minted by Health Connect; the changes worker uses
 * them to fetch only what changed since the last poll (rather than re-reading the full
 * sensor history every cycle). Persisting per-type means the worker can recover after
 * a process death without losing or duplicating events.
 *
 * Implementation notes:
 *  - Backed by [LocalStorage] (a `SharedPreferences` wrapper). A token map is small
 *    enough that DataStore migration is unjustified.
 *  - All access is mutex-guarded. CLAUDE.md forbids `synchronized` blocks; the mutex
 *    also lets us await the suspending [LocalStorage] reads safely.
 *  - The store treats a missing or blank token identically: callers should mint a fresh
 *    `getChangesToken` call when [get] returns `null`.
 */
@Singleton
class HealthConnectChangesTokenStore @Inject constructor(
    @NamedHealthConnectStorage private val storage: LocalStorage,
) {
    private val mutex = Mutex()

    suspend fun get(dataType: HealthConnectDataType): String? = mutex.withLock {
        storage.getString(key(dataType))?.takeIf { it.isNotBlank() }
    }

    suspend fun put(dataType: HealthConnectDataType, token: String) = mutex.withLock {
        storage.putString(key(dataType), token)
    }

    /**
     * Drop the persisted token for [dataType]. Used after the Health Connect server
     * reports `changesTokenExpired` — the next poll then mints a fresh token instead
     * of re-sending the expired one in a tight loop.
     */
    suspend fun clear(dataType: HealthConnectDataType) = mutex.withLock {
        storage.remove(key(dataType))
    }

    suspend fun clearAll() = mutex.withLock {
        HealthConnectDataType.all.forEach { storage.remove(key(it)) }
    }

    private fun key(dataType: HealthConnectDataType): String = "$KEY_PREFIX${dataType.key}"

    companion object {
        const val KEY_PREFIX = "changes_token::"
    }
}
