package io.homeassistant.companion.android.tiles

import android.content.Context
import android.os.SystemClock
import androidx.wear.tiles.TileService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import timber.log.Timber

/**
 * Keeps slow thermostat acknowledgments out of the gesture path. A pending request is stored
 * independently of Room tile configuration so a view doesn't accidentally present a proposed
 * temperature as the confirmed one. No network work runs on the UI thread.
 *
 * Confirmation means that Home Assistant's entity state reflects the request, not proof that
 * the thermostat's physical relay has already changed state.
 */
@Singleton
class ThermostatCommandCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverManager: ServerManager,
) {
    data class Pending(
        val value: String,
        val status: Status,
        val single: Float? = null,
        val low: Float? = null,
        val high: Float? = null,
    ) {
        enum class Status { WAITING, FAILED }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("thermostat_tile_commands", Context.MODE_PRIVATE)
    private val latest = mutableMapOf<Int, Long>()
    private val lock = Any()
    private var nextToken = 0L
    // Queue service dispatches in submission order for the same entity. The next
    // service call need not wait for the previous entity-state acknowledgment.
    private val dispatchTails = mutableMapOf<String, CompletableDeferred<Unit>>()

    private data class CachedEntity(val item: EntityDisplayWithoutContext, val receivedAt: Long)
    private val entityCache = ConcurrentHashMap<String, CachedEntity>()
    private val refreshInFlight = ConcurrentHashMap<String, AtomicBoolean>()
    private val refreshAttemptAt = ConcurrentHashMap<String, Long>()

    fun cachedEntity(entityId: String): EntityDisplayWithoutContext? =
        entityCache[entityId]?.takeIf { System.currentTimeMillis() - it.receivedAt < 120_000L }?.item

    fun rememberEntity(entity: EntityDisplayWithoutContext) {
        val previous = entityCache.put(entity.entityId, CachedEntity(entity, System.currentTimeMillis()))
        // A state change should appear without waiting for the tile's long periodic interval.
        if (previous != null && previous.item != entity) refresh()
    }

    fun refreshEntityIfStale(entityId: String, maxAgeMillis: Long) {
        val now = System.currentTimeMillis()
        val entry = entityCache[entityId]
        if (entry != null && now - entry.receivedAt < maxAgeMillis) return
        // Limit retries when offline and coalesce simultaneous tile requests.
        val previousAttempt = refreshAttemptAt[entityId] ?: 0L
        if (now - previousAttempt < maxAgeMillis) return
        val gate = refreshInFlight.getOrPut(entityId) { AtomicBoolean(false) }
        if (!gate.compareAndSet(false, true)) return
        refreshAttemptAt[entityId] = now
        scope.launch {
            try {
                serverManager.integrationRepository().getEntity(entityId)?.let {
                    rememberEntity(EntityDisplayWithoutContext(it))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Thermostat background refresh failed: %s", entityId)
            } finally {
                gate.set(false)
            }
        }
    }

    fun get(tileId: Int): Pending? = synchronized(lock) {
        val time = prefs.getLong("$tileId.time", 0L)
        if (time == 0L) return null
        val failed = prefs.getBoolean("$tileId.failed", false)
        val maxAge = if (failed) 15_000 else 45_000
        if (System.currentTimeMillis() - time > maxAge) {
            clear(tileId)
            return null
        }
        fun floatOrNull(key: String) =
            if (prefs.contains("$tileId.$key")) prefs.getFloat("$tileId.$key", 0f) else null
        return Pending(
            value = prefs.getString("$tileId.value", "") ?: "",
            status = if (failed) Pending.Status.FAILED else Pending.Status.WAITING,
            single = floatOrNull("single"),
            low = floatOrNull("low"),
            high = floatOrNull("high"),
        )
    }

    // Called under lock, so a newer request cannot be overwritten by an older reply.
    private fun store(
        tileId: Int,
        value: String,
        failed: Boolean = false,
        single: Float? = null,
        low: Float? = null,
        high: Float? = null,
    ) {
        val edit = prefs.edit().putLong("$tileId.time", System.currentTimeMillis())
            .putString("$tileId.value", value).putBoolean("$tileId.failed", failed)
        if (!failed) {
            edit.remove("$tileId.single").remove("$tileId.low").remove("$tileId.high")
            if (single != null) edit.putFloat("$tileId.single", single)
            if (low != null) edit.putFloat("$tileId.low", low)
            if (high != null) edit.putFloat("$tileId.high", high)
        }
        edit.apply()
        refresh()
    }

    private fun clear(tileId: Int) {
        prefs.edit().remove("$tileId.time").remove("$tileId.value")
            .remove("$tileId.failed").remove("$tileId.single")
            .remove("$tileId.low").remove("$tileId.high").apply()
        refresh()
    }

    fun submitTemperature(
        tileId: Int,
        entityId: String,
        isRange: Boolean,
        low: Float?,
        high: Float?,
        single: Float?,
    ) {
        val data: Map<String, Any?> = when {
            isRange && low != null && high != null -> mapOf(
                "entity_id" to entityId,
                "target_temp_low" to low,
                "target_temp_high" to high,
            )
            !isRange && single != null -> mapOf("entity_id" to entityId, "temperature" to single)
            else -> return
        }
        val displayValue = if (isRange) "$low–$high°" else "$single°"
        submit(tileId, entityId, "set_temperature", data, displayValue, single, low, high) { entity ->
            val state = entity.climateControls ?: return@submit false
            if (isRange) {
                approximately(state.targetTemperatureLow, low) && approximately(state.targetTemperatureHigh, high)
            } else {
                approximately(state.targetTemperature, single)
            }
        }
    }

    fun submitHvac(tileId: Int, entityId: String, mode: String) = submit(
        tileId, entityId, "set_hvac_mode", mapOf("entity_id" to entityId, "hvac_mode" to mode), mode,
    ) { entity -> entity.rawState == mode }

    fun submitFan(tileId: Int, entityId: String, mode: String) = submit(
        tileId, entityId, "set_fan_mode", mapOf("entity_id" to entityId, "fan_mode" to mode), mode,
    ) { entity -> entity.climateControls?.fanMode == mode }

    private fun approximately(a: Float?, b: Float?): Boolean = a != null && b != null && kotlin.math.abs(a - b) < 0.051f

    private fun submit(
        tileId: Int,
        entityId: String,
        action: String,
        data: Map<String, Any?>,
        value: String,
        single: Float? = null,
        low: Float? = null,
        high: Float? = null,
        confirmed: (EntityDisplay) -> Boolean,
    ) {
        val dispatched = CompletableDeferred<Unit>()
        val (token, predecessor) = synchronized(lock) {
            val token = ++nextToken
            latest[tileId] = token
            store(tileId, value, single = single, low = low, high = high)
            token to dispatchTails.put(entityId, dispatched)
        }
        scope.launch {
            val started = SystemClock.elapsedRealtime()
            try {
                try {
                    predecessor?.await()
                    serverManager.integrationRepository().callAction("climate", action, data)
                } finally {
                    // Always release the following command, including after a failed service call.
                    synchronized(lock) {
                        if (dispatchTails[entityId] === dispatched) dispatchTails.remove(entityId)
                        dispatched.complete(Unit)
                    }
                }
                Timber.i("Thermostat %s service accepted after %d ms", action, SystemClock.elapsedRealtime() - started)
                // Request only the climate entity, not ALL HA states and the entire
                // entity registry. Acknowledgment polling must be cheap on a watch.
                repeat(12) {
                    if (!isLatest(tileId, token)) return@launch
                    val current = serverManager.integrationRepository().getEntity(entityId)
                        ?.let { EntityDisplayWithoutContext(it) }
                    synchronized(lock) {
                        if (latest[tileId] != token) return@launch
                        if (current != null) {
                            rememberEntity(current)
                            if (confirmed(current)) {
                                Timber.i("Thermostat %s state confirmed after %d ms", action, SystemClock.elapsedRealtime() - started)
                                clear(tileId)
                                return@launch
                            }
                        }
                    }
                    delay(1_500)
                }
                Timber.w("Thermostat %s unconfirmed after %d ms", action, SystemClock.elapsedRealtime() - started)
                synchronized(lock) {
                    if (latest[tileId] == token) store(tileId, value, failed = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Thermostat tile command failed: %s", action)
                synchronized(lock) {
                    if (latest[tileId] == token) store(tileId, value, failed = true)
                }
            }
        }
    }

    private fun isLatest(tileId: Int, token: Long): Boolean = synchronized(lock) { latest[tileId] == token }

    private fun refresh() {
        TileService.getUpdater(context).requestUpdate(ThermostatTile::class.java)
    }
}
