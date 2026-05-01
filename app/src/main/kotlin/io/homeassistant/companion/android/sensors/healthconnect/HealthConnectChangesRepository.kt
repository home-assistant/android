package io.homeassistant.companion.android.sensors.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.request.ChangesTokenRequest
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Polls the Health Connect Changes API for one or more data types, returning the set of
 * data types that observed at least one upsertion or deletion since the last poll.
 *
 * The returned set is the trigger for the worker to call [io.homeassistant.companion.android.sensors.SensorReceiver.updateAllSensors]
 * — re-reading and re-publishing the affected sensors. We deliberately don't try to push
 * individual record diffs into HA: the existing sensor pipeline already reads the latest
 * value per data type, so a single update broadcast is sufficient and avoids duplicating
 * the sensor → HA mapping logic that already lives in [io.homeassistant.companion.android.sensors.HealthConnectSensorManager].
 *
 * Token lifecycle:
 *  - First poll for a data type: mint a fresh token via [HealthConnectClient.getChangesToken]
 *    and persist it. No changes are emitted from this call — we have no baseline yet.
 *  - Steady state: call [HealthConnectClient.getChanges] with the saved token, follow
 *    the `hasMore` pagination, persist the final `nextChangesToken`.
 *  - `changesTokenExpired = true`: the device has been offline / silent long enough that
 *    HC dropped our cursor. Clear the stored token, mint a new one, and tell the caller
 *    that this data type "changed" so the next sensor poll catches up.
 */
@Singleton
class HealthConnectChangesRepository @Inject constructor(
    private val clientProvider: Provider<HealthConnectClient?>,
    private val tokenStore: HealthConnectChangesTokenStore,
) {

    /**
     * Returns the subset of [dataTypes] that observed at least one change since the last
     * poll, or `null` when Health Connect is unavailable on this device. An empty set is
     * a valid, common result — it means "nothing changed", not "failed".
     */
    suspend fun pollChanges(dataTypes: Collection<HealthConnectDataType>): Set<HealthConnectDataType>? {
        val client = clientProvider.get() ?: return null
        return withContext(Dispatchers.IO) {
            val granted = runCatching { client.permissionController.getGrantedPermissions() }
                .getOrElse { error ->
                    Timber.w(error, "Failed to read Health Connect permissions during changes poll")
                    return@withContext emptySet()
                }

            val changed = mutableSetOf<HealthConnectDataType>()
            for (dataType in dataTypes) {
                val readPermission = HealthPermission.getReadPermission(dataType.recordClass)
                if (readPermission !in granted) continue
                if (pollOne(client, dataType)) {
                    changed += dataType
                }
            }
            changed
        }
    }

    private suspend fun pollOne(client: HealthConnectClient, dataType: HealthConnectDataType): Boolean {
        val existing = tokenStore.get(dataType)
        if (existing == null) {
            // Mint a baseline token. No changes are reported for the very first poll —
            // the existing 15-min SensorWorker already keeps initial values up to date.
            val fresh = runCatching {
                client.getChangesToken(ChangesTokenRequest(setOf(dataType.recordClass)))
            }.onFailure { Timber.w(it, "getChangesToken failed for ${dataType.key}") }
                .getOrNull()
                ?: return false
            tokenStore.put(dataType, fresh)
            return false
        }

        var token: String = existing
        var observed = false
        // hasMore can return multiple pages; drain them so we don't leave events behind.
        while (true) {
            val response = runCatching { client.getChanges(token) }
                .onFailure { Timber.w(it, "getChanges failed for ${dataType.key}") }
                .getOrNull()
                ?: return observed

            if (response.changesTokenExpired) {
                Timber.i("Changes token expired for ${dataType.key} — minting a fresh token")
                tokenStore.clear(dataType)
                val fresh = runCatching {
                    client.getChangesToken(ChangesTokenRequest(setOf(dataType.recordClass)))
                }.getOrNull()
                if (fresh != null) tokenStore.put(dataType, fresh)
                // Treat token expiry as "something changed" so the next sensor poll
                // re-publishes the latest value, even though we don't have the deltas.
                return true
            }

            for (change in response.changes) {
                when (change) {
                    is UpsertionChange, is DeletionChange -> observed = true
                }
            }

            token = response.nextChangesToken
            if (!response.hasMore) break
        }
        tokenStore.put(dataType, token)
        return observed
    }
}
