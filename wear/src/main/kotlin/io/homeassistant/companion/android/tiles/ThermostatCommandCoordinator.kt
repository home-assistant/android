package io.homeassistant.companion.android.tiles

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import androidx.wear.tiles.TileService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.integration.EntityChange
import io.homeassistant.companion.android.common.data.integration.EntityFilter
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val VISIBILITY_MAX_AGE_MS = 10_000L
private const val LIVE_INITIAL_CACHE_AGE_MS = 1_000L
private const val LIVE_OBSERVATION_TIMEOUT_MS = 90_000L
private const val SCREEN_CHECK_INTERVAL_MS = 1_000L
private const val ENTITY_CACHE_TTL_MS = 120_000L
private const val TEMPERATURE_CONFIRMATION_TOLERANCE = 0.051f
private const val ACK_POLL_ATTEMPTS = 12
private const val ACK_POLL_INTERVAL_MS = 1_500L
private const val FILTERED_ENTITY_SUBSCRIPTION_YEAR = 2022
private const val FILTERED_ENTITY_SUBSCRIPTION_MONTH = 4
private const val LIVE_RETRY_INTERVAL_MS = 3_000L
private const val INITIAL_CONFIRMATION_TIMEOUT_MS = 3_000L
private const val SUBSCRIPTION_CONFIRMATION_TIMEOUT_MS = 4_000L
private const val COMMAND_CONFIRMATION_TIMEOUT_MS = 18_000L

/**
 * Keeps slow thermostat acknowledgments out of the gesture path. A pending request is stored
 * independently of Room tile configuration so a view doesn't accidentally present a proposed
 * temperature as the confirmed one. No network work runs on the UI thread.
 *
 * Confirmation means that Home Assistant's entity state reflects the request, not proof that
 * the thermostat's physical relay has already changed state.
 */
// One lock owns the cache, visibility generation, and ordered command lifecycle.
@Suppress("TooManyFunctions")
@Singleton
class ThermostatCommandCoordinator internal constructor(
    context: Context,
    private val serverManager: ServerManager,
    private val scope: CoroutineScope,
    private val requestTileUpdate: () -> Unit,
) {
    @Inject
    constructor(@ApplicationContext context: Context, serverManager: ServerManager) : this(
        context,
        serverManager,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        { TileService.getUpdater(context).requestUpdate(ThermostatTile::class.java) },
    )

    data class Pending(
        val value: String,
        val status: Status,
        val single: Float? = null,
        val low: Float? = null,
        val high: Float? = null,
    ) {
        enum class Status { WAITING, FAILED }
    }

    data class Target(val serverId: Int, val entityId: String) {
        init {
            require(serverId != ServerManager.SERVER_ID_ACTIVE)
        }

        // Bind clickable IDs to the rendered target, including across server switches.
        val actionPrefix: String get() = "$serverId|$entityId|"
    }

    private data class PendingKey(val tileId: Int, val target: Target) {
        val prefix: String get() = "v2.${target.serverId}.${target.entityId}.$tileId"
    }

    private val prefs by lazy {
        context.getSharedPreferences("thermostat_tile_commands", Context.MODE_PRIVATE)
    }
    private val latest = mutableMapOf<PendingKey, Long>()
    private val lock = ReentrantLock()
    private var nextToken = 0L

    // Queue service dispatches in submission order for the same entity. The next
    // service call need not wait for the previous entity-state acknowledgment.
    private val dispatchTails = mutableMapOf<Target, CompletableDeferred<Unit>>()
    private val acknowledgementJobs = mutableMapOf<PendingKey, Job>()

    private data class CachedEntity(val item: EntityDisplayWithoutContext, val receivedAt: Long)
    private val entityCache = ConcurrentHashMap<Target, CachedEntity>()
    private val refreshInFlight = ConcurrentHashMap<Target, AtomicBoolean>()
    private val refreshAttemptAt = ConcurrentHashMap<Target, Long>()

    // A tile request alone does not prove visibility. Only recent interaction evidence may
    // start a bounded observer. Redraws never extend an existing session.
    private val powerManager by lazy { context.getSystemService(PowerManager::class.java) }
    private var liveJob: Job? = null
    private var liveTileId: Int? = null
    private var liveTarget: Target? = null
    private var liveGeneration = 0L
    private var lastVisibilityAt = 0L

    // Early exits keep visibility checks and lock ownership explicit.
    @Suppress("ReturnCount")
    fun observeVisibleTile(tileId: Int, target: Target, visibleAt: Long) {
        val age = System.currentTimeMillis() - visibleAt
        if (age !in 0L..VISIBILITY_MAX_AGE_MS || powerManager?.isInteractive != true) return
        lock.withLock {
            if (visibleAt <= lastVisibilityAt) return
            lastVisibilityAt = visibleAt
            if (liveJob?.isActive == true && liveTileId == tileId && liveTarget == target) return
            liveJob?.cancel()
            val generation = ++liveGeneration
            liveTileId = tileId
            liveTarget = target
            liveJob = scope.launch { runLiveObservation(target, generation) }
        }
    }

    private suspend fun supportsFilteredSubscription(target: Target): Boolean =
        serverManager.getServer(target.serverId)?.version?.isAtLeast(
            FILTERED_ENTITY_SUBSCRIPTION_YEAR,
            FILTERED_ENTITY_SUBSCRIPTION_MONTH,
            0,
        ) == true

    private suspend fun runLiveObservation(target: Target, generation: Long) {
        try {
            if (!supportsFilteredSubscription(target)) return
            Timber.i("Thermostat live observation started: %s", target.entityId)
            refreshEntityIfStale(target, LIVE_INITIAL_CACHE_AGE_MS)
            // Leave events can be delayed on Wear OS 6+. Never keep a socket collector
            // alive indefinitely based on uncertain visibility.
            withTimeoutOrNull(LIVE_OBSERVATION_TIMEOUT_MS) {
                coroutineScope {
                    val collector = launch {
                        collectLiveChanges(target, generation)
                    }
                    try {
                        // Local screen-state check only; this is not network polling.
                        while (collector.isActive && powerManager?.isInteractive == true) {
                            delay(SCREEN_CHECK_INTERVAL_MS)
                        }
                    } finally {
                        collector.cancel()
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Thermostat live observation failed: %s", target.entityId)
        } finally {
            lock.withLock {
                if (liveGeneration == generation) {
                    liveJob = null
                    liveTileId = null
                    liveTarget = null
                }
            }
            Timber.i("Thermostat live observation stopped: %s", target.entityId)
        }
    }

    private suspend fun CoroutineScope.collectLiveChanges(target: Target, generation: Long) {
        while (isActive) {
            try {
                serverManager.integrationRepository(target.serverId)
                    .getEntityChanges(EntityFilter(entityIds = setOf(target.entityId)))
                    ?.collect { change -> applyLiveChange(target, generation, change) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Thermostat live subscription failed: %s", target.entityId)
            }
            // Retry a disconnected or unavailable subscription only within
            // the same short observation window.
            delay(LIVE_RETRY_INTERVAL_MS)
        }
    }

    fun stopObservingTile(tileId: Int, leftAt: Long = System.currentTimeMillis()) {
        lock.withLock {
            if (liveTileId != tileId || leftAt < lastVisibilityAt) return
            lastVisibilityAt = leftAt
            ++liveGeneration
            liveJob?.cancel()
            liveJob = null
            liveTileId = null
            liveTarget = null
        }
    }

    private fun applyLiveChange(target: Target, generation: Long, change: EntityChange) {
        lock.withLock {
            // The command acknowledgement path owns state during an in-flight command.
            if (liveGeneration != generation || latest.keys.any { it.target == target }) return
            when (change) {
                is EntityChange.StateChanged -> applyObservedState(target, EntityDisplayWithoutContext(change.entity))
                is EntityChange.Removed -> if (change.entityId == target.entityId) {
                    if (entityCache.remove(target) != null) refresh()
                }
            }
        }
    }

    // Caller holds lock; reject events older than the current cached state.
    private fun applyObservedState(target: Target, entity: EntityDisplayWithoutContext) {
        if (entity.entityId != target.entityId) return
        val previous = entityCache[target]?.item
        val previousUpdated = previous?.lastUpdated
        val updated = entity.lastUpdated
        if (previousUpdated != null && updated != null && updated < previousUpdated) return
        entityCache[target] = CachedEntity(entity, System.currentTimeMillis())
        if (previous == null || displayChanged(previous, entity)) {
            Timber.i("Thermostat live state changed: %s", target.entityId)
            refresh()
        }
    }

    private fun displayChanged(previous: EntityDisplayWithoutContext, entity: EntityDisplayWithoutContext): Boolean =
        previous.rawState != entity.rawState ||
            previous.name != entity.name ||
            previous.climateControls != entity.climateControls

    fun cachedEntity(target: Target): EntityDisplayWithoutContext? =
        entityCache[target]?.takeIf { System.currentTimeMillis() - it.receivedAt < ENTITY_CACHE_TTL_MS }?.item

    fun rememberEntity(target: Target, entity: EntityDisplayWithoutContext) {
        if (entity.entityId != target.entityId) return
        lock.withLock {
            val previous = entityCache[target]
            val previousUpdated = previous?.item?.lastUpdated
            val updated = entity.lastUpdated
            if (previousUpdated != null && updated != null && updated < previousUpdated) return
            entityCache[target] = CachedEntity(entity, System.currentTimeMillis())
            // A state change should appear without waiting for the tile's long periodic interval.
            if (previous != null && previous.item != entity) refresh()
        }
    }

    // Independent early exits avoid unnecessary reads and preserve the in-flight gate.
    @Suppress("ReturnCount")
    fun refreshEntityIfStale(target: Target, maxAgeMillis: Long) {
        // A command already provides state updates. Avoid a second request from tile rendering.
        val refreshToken = lock.withLock {
            if (latest.keys.any { it.target == target }) return
            nextToken
        }
        val now = System.currentTimeMillis()
        val entry = entityCache[target]
        if (entry != null && now - entry.receivedAt < maxAgeMillis) return
        // Limit retries when offline and coalesce simultaneous tile requests.
        val previousAttempt = refreshAttemptAt[target] ?: 0L
        if (now - previousAttempt < maxAgeMillis) return
        val gate = refreshInFlight.getOrPut(target) { AtomicBoolean(false) }
        if (!gate.compareAndSet(false, true)) return
        refreshAttemptAt[target] = now
        scope.launch {
            try {
                serverManager.integrationRepository(target.serverId).getEntity(target.entityId)?.let {
                    lock.withLock {
                        // Discard a read started before a newer command was submitted.
                        if (nextToken == refreshToken &&
                            entityCache[target] === entry &&
                            latest.keys.none { key -> key.target == target }
                        ) {
                            rememberEntity(target, EntityDisplayWithoutContext(it))
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Thermostat background refresh failed: %s", target.entityId)
            } finally {
                gate.set(false)
            }
        }
    }

    @WorkerThread
    fun get(tileId: Int, target: Target): Pending? = lock.withLock {
        val key = PendingKey(tileId, target)
        val prefix = key.prefix
        val time = prefs.getLong("$prefix.time", 0L)
        if (time == 0L) return null
        val failed = prefs.getBoolean("$prefix.failed", false)
        val maxAge = if (failed) 15_000 else 45_000
        if (System.currentTimeMillis() - time > maxAge) {
            clear(key)
            return null
        }
        fun floatOrNull(key: String) = if (prefs.contains("$prefix.$key")) prefs.getFloat("$prefix.$key", 0f) else null
        return Pending(
            value = prefs.getString("$prefix.value", "").orEmpty(),
            status = if (failed) Pending.Status.FAILED else Pending.Status.WAITING,
            single = floatOrNull("single"),
            low = floatOrNull("low"),
            high = floatOrNull("high"),
        )
    }

    // Called under lock, so a newer request cannot be overwritten by an older reply.
    private fun store(key: PendingKey, pending: Pending, updateTile: Boolean = true) {
        val failed = pending.status == Pending.Status.FAILED
        val prefix = key.prefix
        prefs.edit {
            putLong("$prefix.time", System.currentTimeMillis())
            putString("$prefix.value", pending.value)
            putBoolean("$prefix.failed", failed)
            if (!failed) {
                remove("$prefix.single")
                remove("$prefix.low")
                remove("$prefix.high")
                pending.single?.let { putFloat("$prefix.single", it) }
                pending.low?.let { putFloat("$prefix.low", it) }
                pending.high?.let { putFloat("$prefix.high", it) }
            }
        }
        if (updateTile) refresh()
    }

    private fun clear(key: PendingKey) {
        latest.remove(key)
        val prefix = key.prefix
        prefs.edit {
            remove("$prefix.time")
            remove("$prefix.value")
            remove("$prefix.failed")
            remove("$prefix.single")
            remove("$prefix.low")
            remove("$prefix.high")
        }
        refresh()
    }

    // Nullable single/range values match the existing tile and test call sites.
    @Suppress("LongParameterList")
    @WorkerThread
    fun submitTemperature(tileId: Int, target: Target, isRange: Boolean, low: Float?, high: Float?, single: Float?) {
        val data: Map<String, Any?> = when {
            isRange && low != null && high != null -> mapOf(
                "entity_id" to target.entityId,
                "target_temp_low" to low,
                "target_temp_high" to high,
            )
            !isRange && single != null -> mapOf("entity_id" to target.entityId, "temperature" to single)
            else -> return
        }
        val displayValue = if (isRange) "$low–$high°" else "$single°"
        submit(tileId, target, "set_temperature", data, displayValue, single, low, high) { entity ->
            val state = entity.climateControls ?: return@submit false
            if (isRange) {
                approximately(state.targetTemperatureLow, low) && approximately(state.targetTemperatureHigh, high)
            } else {
                approximately(state.targetTemperature, single)
            }
        }
    }

    @WorkerThread
    fun submitHvac(tileId: Int, target: Target, mode: String) = submit(
        tileId,
        target,
        "set_hvac_mode",
        mapOf("entity_id" to target.entityId, "hvac_mode" to mode),
        mode,
    ) { entity -> entity.rawState == mode }

    @WorkerThread
    fun submitFan(tileId: Int, target: Target, mode: String) = submit(
        tileId,
        target,
        "set_fan_mode",
        mapOf("entity_id" to target.entityId, "fan_mode" to mode),
        mode,
    ) { entity -> entity.climateControls?.fanMode == mode }

    private fun approximately(a: Float?, b: Float?): Boolean =
        a != null && b != null && kotlin.math.abs(a - b) < TEMPERATURE_CONFIRMATION_TOLERANCE

    // Keep dispatch, acknowledgement ownership and failure cleanup together in one coroutine.
    @Suppress("LongParameterList", "LongMethod")
    private fun submit(
        tileId: Int,
        target: Target,
        action: String,
        data: Map<String, Any?>,
        value: String,
        single: Float? = null,
        low: Float? = null,
        high: Float? = null,
        confirmed: (EntityDisplay) -> Boolean,
    ) {
        val key = PendingKey(tileId, target)
        val submittedAt = SystemClock.elapsedRealtime()
        val dispatched = CompletableDeferred<Unit>()
        val (token, predecessor) = lock.withLock {
            val token = ++nextToken
            latest[key] = token
            // Only registered after dispatch; never cancel a queued or in-flight service call.
            acknowledgementJobs.remove(key)?.cancel()
            store(key, Pending(value, Pending.Status.WAITING, single, low, high), updateTile = false)
            token to dispatchTails.put(target, dispatched)
        }
        scope.launch {
            val started = submittedAt
            try {
                try {
                    predecessor?.await()
                    Timber.i(
                        "Thermostat %s dispatch starting after %d ms",
                        action,
                        SystemClock.elapsedRealtime() - started,
                    )
                    serverManager.integrationRepository(target.serverId).callAction("climate", action, data)
                } finally {
                    // Always release the following command, including after a failed service call.
                    lock.withLock {
                        if (dispatchTails[target] === dispatched) dispatchTails.remove(target)
                        dispatched.complete(Unit)
                    }
                }
                Timber.i("Thermostat %s service accepted after %d ms", action, SystemClock.elapsedRealtime() - started)
                val monitor = coroutineContext.job
                lock.withLock {
                    if (latest[key] != token) return@launch
                    acknowledgementJobs[key] = monitor
                }
                try {
                    val acknowledged = withTimeoutOrNull(COMMAND_CONFIRMATION_TIMEOUT_MS) {
                        awaitAcknowledgement(key, token, confirmed)
                    } == true
                    lock.withLock {
                        if (latest[key] != token) return@launch
                        if (acknowledged) {
                            Timber.i(
                                "Thermostat %s state confirmed after %d ms",
                                action,
                                SystemClock.elapsedRealtime() - started,
                            )
                            clear(key)
                        } else {
                            Timber.w(
                                "Thermostat %s unconfirmed after %d ms",
                                action,
                                SystemClock.elapsedRealtime() - started,
                            )
                            store(key, Pending(value, Pending.Status.FAILED))
                            latest.remove(key)
                        }
                    }
                } finally {
                    lock.withLock {
                        if (acknowledgementJobs[key] === monitor) acknowledgementJobs.remove(key)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Thermostat tile command failed: %s", action)
                lock.withLock {
                    if (latest[key] == token) {
                        store(key, Pending(value, Pending.Status.FAILED))
                        latest.remove(key)
                    }
                }
            }
        }
    }

    /** Prefer a short-lived, single-entity WebSocket subscription. Poll only as a fallback. */
    private suspend fun awaitAcknowledgement(
        key: PendingKey,
        token: Long,
        confirmed: (EntityDisplay) -> Boolean,
    ): Boolean {
        val target = key.target
        val repository = serverManager.integrationRepository(target.serverId)
        fun checkState(entity: EntityDisplayWithoutContext): Boolean = lock.withLock {
            if (latest[key] != token || entity.entityId != target.entityId) return false
            rememberEntity(target, entity)
            confirmed(entity)
        }
        return readConfirmation(target, repository, ::checkState) ||
            subscribeForConfirmation(key, token, repository, ::checkState) ||
            pollForConfirmation(key, token, repository, ::checkState)
    }

    private suspend fun readConfirmation(
        target: Target,
        repository: IntegrationRepository,
        checkState: (EntityDisplayWithoutContext) -> Boolean,
    ): Boolean {
        // Check the requested entity immediately before waiting for subscription events.
        try {
            val current = withTimeoutOrNull(INITIAL_CONFIRMATION_TIMEOUT_MS) {
                repository.getEntity(target.entityId)?.let { EntityDisplayWithoutContext(it) }
            }
            return current != null && checkState(current)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Thermostat initial confirmation read failed: %s", target.entityId)
            return false
        }
    }

    private suspend fun subscribeForConfirmation(
        key: PendingKey,
        token: Long,
        repository: IntegrationRepository,
        checkState: (EntityDisplayWithoutContext) -> Boolean,
    ): Boolean {
        val target = key.target
        // Unknown and pre-2022.4 servers may send all entity states on subscription.
        // Prefer a targeted read instead on those servers.
        if (!supportsFilteredSubscription(target)) return false
        return try {
            val change = withTimeoutOrNull(SUBSCRIPTION_CONFIRMATION_TIMEOUT_MS) {
                repository.getEntityChanges(EntityFilter(entityIds = setOf(target.entityId)))
                    ?.firstOrNull { change ->
                        when (change) {
                            is EntityChange.StateChanged -> checkState(EntityDisplayWithoutContext(change.entity))
                            is EntityChange.Removed -> {
                                lock.withLock {
                                    if (latest[key] == token && change.entityId == target.entityId) {
                                        entityCache.remove(target)
                                        refresh()
                                    }
                                }
                                false
                            }
                        }
                    }
            }
            change != null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "Thermostat state subscription failed: %s", target.entityId)
            false
        }
    }

    // Stop polling immediately when superseded, including after each delay.
    @Suppress("ReturnCount")
    private suspend fun pollForConfirmation(
        key: PendingKey,
        token: Long,
        repository: IntegrationRepository,
        checkState: (EntityDisplayWithoutContext) -> Boolean,
    ): Boolean {
        val target = key.target
        // The outer timeout bounds both subscription and fallback polling, including slow reads.
        repeat(ACK_POLL_ATTEMPTS) { attempt ->
            if (!isLatest(key, token)) return false
            if (attempt > 0) delay(ACK_POLL_INTERVAL_MS)
            if (!isLatest(key, token)) return false
            val current = repository.getEntity(target.entityId)?.let { EntityDisplayWithoutContext(it) }
            if (current != null && checkState(current)) return true
        }
        return false
    }

    private fun isLatest(key: PendingKey, token: Long): Boolean = lock.withLock { latest[key] == token }

    private fun refresh() {
        requestTileUpdate()
    }
}
