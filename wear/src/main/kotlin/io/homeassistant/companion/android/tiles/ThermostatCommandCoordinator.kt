@file:OptIn(ExperimentalTime::class)

package io.homeassistant.companion.android.tiles

import android.content.Context
import android.os.PowerManager
import androidx.wear.tiles.TileService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.integration.ClimateControls
import io.homeassistant.companion.android.common.data.integration.EntityChange
import io.homeassistant.companion.android.common.data.integration.EntityFilter
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val VISIBILITY_MAX_AGE_MS = 10_000L
private const val LIVE_INITIAL_CACHE_AGE_MS = 1_000L
private const val COMMAND_BASELINE_MAX_AGE_MS = 5_000L
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
private const val FAILED_COMMAND_DISPLAY_MS = 15_000L

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
internal class ThermostatCommandCoordinator internal constructor(
    context: Context,
    private val serverManager: ServerManager,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val requestTileUpdate: () -> Unit,
) {
    @Inject
    constructor(@ApplicationContext context: Context, serverManager: ServerManager, clock: Clock) : this(
        context,
        serverManager,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        clock,
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

    private enum class CommandKind { TEMPERATURE, HVAC, FAN }

    private data class PendingKey(val target: Target, val kind: CommandKind)

    // Pending commands belong to the running process, not persistent tile settings.
    private val pendingCommands = mutableMapOf<PendingKey, Pending>()
    private val latest = mutableMapOf<PendingKey, Long>()
    private val lock = Mutex()
    private val relativeCommandLocks = ConcurrentHashMap<Target, Mutex>()
    private var nextToken = 0L

    // Queue service dispatches in submission order for the same entity. The next
    // service call need not wait for the previous entity-state acknowledgment.
    private val dispatchTails = mutableMapOf<Target, CompletableDeferred<Unit>>()
    private val acknowledgementJobs = mutableMapOf<PendingKey, Job>()

    private data class CachedEntity(val item: EntityDisplayWithoutContext, val receivedAt: Long)
    private val entityCache = ConcurrentHashMap<Target, CachedEntity>()
    private val refreshInFlight = mutableMapOf<Target, Any>()
    private val refreshAttemptAt = mutableMapOf<Target, Long>()

    // A tile request alone does not prove visibility. Only recent interaction evidence may
    // start a bounded observer. Redraws never extend an existing session.
    private val powerManager by lazy { context.getSystemService(PowerManager::class.java) }
    private var liveJob: Job? = null
    private var liveTileId: Int? = null
    private var liveTarget: Target? = null
    private var liveGeneration = 0L
    private var lastVisibilityAt = 0L

    internal fun nowMillis(): Long = clock.now().toEpochMilliseconds()

    // Early exits keep visibility checks and lock ownership explicit.
    @Suppress("ReturnCount")
    suspend fun observeVisibleTile(tileId: Int, target: Target, visibleAt: Long) {
        val age = nowMillis() - visibleAt
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
            withContext(NonCancellable) {
                lock.withLock {
                    if (liveGeneration == generation) {
                        liveJob = null
                        liveTileId = null
                        liveTarget = null
                    }
                }
            }
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

    suspend fun stopObservingTile(tileId: Int, leftAt: Long = nowMillis()) {
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

    private suspend fun applyLiveChange(target: Target, generation: Long, change: EntityChange) {
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
        entityCache[target] = CachedEntity(entity, nowMillis())
        if (previous == null || displayChanged(previous, entity)) {
            refresh()
        }
    }

    private fun displayChanged(previous: EntityDisplayWithoutContext, entity: EntityDisplayWithoutContext): Boolean =
        previous.rawState != entity.rawState ||
            previous.name != entity.name ||
            previous.climateControls != entity.climateControls

    fun cachedEntity(target: Target): EntityDisplayWithoutContext? =
        entityCache[target]?.takeIf { nowMillis() - it.receivedAt in 0L until ENTITY_CACHE_TTL_MS }?.item

    suspend fun rememberEntity(target: Target, entity: EntityDisplayWithoutContext): Boolean = lock.withLock {
        rememberEntityLocked(target, entity)
    }

    @Suppress("ReturnCount")
    private fun rememberEntityLocked(target: Target, entity: EntityDisplayWithoutContext): Boolean {
        if (entity.entityId != target.entityId) return false
        val previous = entityCache[target]
        val previousUpdated = previous?.item?.lastUpdated
        val updated = entity.lastUpdated
        if (previousUpdated != null && updated != null && updated < previousUpdated) return false
        entityCache[target] = CachedEntity(entity, nowMillis())
        // A state change should appear without waiting for the tile's long periodic interval.
        if (previous != null && previous.item != entity) refresh()
        return true
    }

    // Independent early exits avoid unnecessary reads and preserve the in-flight gate.
    @Suppress("ReturnCount")
    suspend fun refreshEntityIfStale(target: Target, maxAgeMillis: Long) {
        // A command already provides state updates. Avoid a second request from tile rendering.
        val (entry, refreshToken) = lock.withLock {
            if (latest.keys.any { it.target == target } || target in refreshInFlight) return
            val now = nowMillis()
            val cached = entityCache[target]
            if (cached != null && now - cached.receivedAt in 0L until maxAgeMillis) return
            val previousAttempt = refreshAttemptAt[target]
            if (previousAttempt != null && now - previousAttempt in 0L until maxAgeMillis) return
            val token = Any()
            refreshInFlight[target] = token
            refreshAttemptAt[target] = now
            cached to token
        }
        scope.launch {
            try {
                serverManager.integrationRepository(target.serverId).getEntity(target.entityId)?.let {
                    lock.withLock {
                        // Discard a read started before a newer command was submitted.
                        if (refreshInFlight[target] === refreshToken &&
                            entityCache[target] === entry &&
                            latest.keys.none { key -> key.target == target }
                        ) {
                            rememberEntityLocked(target, EntityDisplayWithoutContext(it))
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Thermostat background refresh failed: %s", target.entityId)
            } finally {
                withContext(NonCancellable) {
                    lock.withLock {
                        if (refreshInFlight[target] === refreshToken) refreshInFlight.remove(target)
                    }
                }
            }
        }
    }

    suspend fun get(target: Target): Pending? = lock.withLock {
        val commands = CommandKind.entries.mapNotNull { pendingCommands[PendingKey(target, it)] }
        commands.firstOrNull { it.status == Pending.Status.WAITING } ?: commands.firstOrNull()
    }

    private fun waiting(target: Target, kind: CommandKind): Pending? =
        pendingCommands[PendingKey(target, kind)]?.takeIf { it.status == Pending.Status.WAITING }

    // Called under lock, so a newer request cannot be overwritten by an older reply.
    private fun store(key: PendingKey, pending: Pending, updateTile: Boolean = true) {
        pendingCommands[key] = pending
        if (pending.status == Pending.Status.FAILED) {
            scope.launch {
                delay(FAILED_COMMAND_DISPLAY_MS)
                lock.withLock {
                    if (pendingCommands[key] === pending) {
                        pendingCommands.remove(key)
                        refresh()
                    }
                }
            }
        }
        if (updateTile) refresh()
    }

    private fun clear(key: PendingKey) {
        latest.remove(key)
        pendingCommands.remove(key)
        refresh()
    }

    internal enum class TemperatureSlot { CLASSIC_SINGLE, CLASSIC_RANGE, SINGLE, HEAT, COOL }

    // Relative commands require a recent server baseline, unless a pending command already
    // provides the value to adjust. Serialize only relative commands for the same target;
    // network reads must never hold the shared state mutex.
    private suspend fun withFreshBaseline(
        target: Target,
        kind: CommandKind,
        action: (EntityDisplayWithoutContext) -> Unit,
    ) {
        relativeCommandLocks.getOrPut(target) { Mutex() }.withLock {
            val handled = lock.withLock {
                val entity = entityCache[target]?.item
                if (entity != null && hasFreshBaseline(target, kind)) {
                    action(entity)
                    true
                } else {
                    false
                }
            }
            if (handled) return@withLock

            val current = try {
                withTimeoutOrNull(INITIAL_CONFIRMATION_TIMEOUT_MS) {
                    serverManager.integrationRepository(target.serverId).getEntity(target.entityId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Unable to refresh thermostat command baseline: %s", target.entityId)
                null
            }
            lock.withLock {
                if (!hasFreshBaseline(target, kind) && current != null) {
                    rememberEntityLocked(target, EntityDisplayWithoutContext(current))
                }
                val entity = entityCache[target]?.item
                if (entity != null && hasFreshBaseline(target, kind)) {
                    action(entity)
                } else if (waiting(target, kind) == null) {
                    // Never send a relative command calculated from an expired snapshot.
                    store(PendingKey(target, kind), Pending("", Pending.Status.FAILED))
                }
            }
        }
    }

    private fun hasFreshBaseline(target: Target, kind: CommandKind): Boolean {
        val cached = entityCache[target] ?: return false
        return waiting(target, kind) != null || nowMillis() - cached.receivedAt in 0L until COMMAND_BASELINE_MAX_AGE_MS
    }

    // Calculate and publish the new absolute target in the same critical section.
    @Suppress("ReturnCount")
    internal suspend fun adjustTemperature(
        target: Target,
        units: ThermostatDisplayUnits,
        slot: TemperatureSlot,
        amount: Float,
    ) {
        withFreshBaseline(target, CommandKind.TEMPERATURE) { entity ->
            val controls = entity.climateControls ?: return@withFreshBaseline
            if (entity.rawState in listOf("off", "unknown", "unavailable")) return@withFreshBaseline
            // A mode change may invalidate the layout from which the tap originated.
            val pendingMode = waiting(target, CommandKind.HVAC)?.value
            if (pendingMode != null && pendingMode != entity.rawState) return@withFreshBaseline
            val bounds = TemperatureBounds(
                step = units.serverStep(controls.targetTemperatureStep),
                minimum = controls.minTemperature ?: units.minimum(),
                maximum = controls.maxTemperature ?: units.maximum(),
            )
            val delta = temperatureDelta(units, slot, amount, bounds.step)
            if (entity.rawState == "heat_cool") {
                adjustRangeLocked(target, slot, controls, delta, bounds)
            } else if (slot == TemperatureSlot.SINGLE || slot == TemperatureSlot.CLASSIC_SINGLE) {
                adjustSingleLocked(target, controls, delta, bounds)
            }
        }
    }

    private data class TemperatureBounds(val step: Float, val minimum: Float, val maximum: Float)

    private fun temperatureDelta(
        units: ThermostatDisplayUnits,
        slot: TemperatureSlot,
        amount: Float,
        step: Float,
    ): Float {
        val classic = slot == TemperatureSlot.CLASSIC_SINGLE || slot == TemperatureSlot.CLASSIC_RANGE
        return if (classic && !units.hasOverride) amount * step else units.adjustment(amount, step)
    }

    private fun adjustRangeLocked(
        target: Target,
        slot: TemperatureSlot,
        controls: ClimateControls,
        delta: Float,
        bounds: TemperatureBounds,
    ) {
        val pending = waiting(target, CommandKind.TEMPERATURE)
        val low = pending?.low ?: controls.targetTemperatureLow ?: return
        val high = pending?.high ?: controls.targetTemperatureHigh ?: return
        val setpoints = SetpointRange(low, high)
        val (step, min, max) = bounds
        val revised = when (slot) {
            TemperatureSlot.CLASSIC_RANGE -> ThermostatTemperatureLogic.shiftRange(delta, setpoints, min, max, step)
            TemperatureSlot.HEAT -> ThermostatTemperatureLogic.adjustRange(
                low + delta,
                false,
                setpoints,
                min,
                max,
                step,
            )
            TemperatureSlot.COOL -> ThermostatTemperatureLogic.adjustRange(
                high + delta,
                true,
                setpoints,
                min,
                max,
                step,
            )
            else -> setpoints
        }
        if (revised != setpoints) submitTemperatureLocked(target, true, revised.low, revised.high, null)
    }

    private fun adjustSingleLocked(target: Target, controls: ClimateControls, delta: Float, bounds: TemperatureBounds) {
        val pending = waiting(target, CommandKind.TEMPERATURE)
        val baseline = pending?.single ?: controls.targetTemperature ?: return
        val proposed = ThermostatTemperatureLogic.snap(baseline + delta, bounds.step, bounds.minimum, bounds.maximum)
        if (proposed != baseline) submitTemperatureLocked(target, false, null, null, proposed)
    }

    suspend fun cycleFan(target: Target) {
        withFreshBaseline(target, CommandKind.FAN) { entity ->
            if (entity.rawState in listOf("unknown", "unavailable")) return@withFreshBaseline
            val controls = entity.climateControls ?: return@withFreshBaseline
            val modes = controls.fanModes
            if (modes.isEmpty()) return@withFreshBaseline
            val current = waiting(target, CommandKind.FAN)?.value ?: controls.fanMode
            val next = if ("on" in modes && "auto" in modes) {
                if (current == "on") "auto" else "on"
            } else {
                modes[(modes.indexOf(current) + 1) % modes.size]
            }
            submitFan(target, next)
        }
    }

    // Nullable single/range values match the existing tile and test call sites.
    @Suppress("LongParameterList")
    suspend fun submitTemperature(target: Target, isRange: Boolean, low: Float?, high: Float?, single: Float?) =
        lock.withLock { submitTemperatureLocked(target, isRange, low, high, single) }

    @Suppress("LongParameterList")
    private fun submitTemperatureLocked(target: Target, isRange: Boolean, low: Float?, high: Float?, single: Float?) {
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
        submit(target, CommandKind.TEMPERATURE, "set_temperature", data, displayValue, single, low, high) { entity ->
            val state = entity.climateControls ?: return@submit false
            if (isRange) {
                approximately(state.targetTemperatureLow, low) && approximately(state.targetTemperatureHigh, high)
            } else {
                approximately(state.targetTemperature, single)
            }
        }
    }

    suspend fun submitHvac(target: Target, mode: String) = lock.withLock {
        submit(
            target,
            CommandKind.HVAC,
            "set_hvac_mode",
            mapOf("entity_id" to target.entityId, "hvac_mode" to mode),
            mode,
        ) { entity -> entity.rawState == mode }
    }

    private fun submitFan(target: Target, mode: String) = submit(
        target,
        CommandKind.FAN,
        "set_fan_mode",
        mapOf("entity_id" to target.entityId, "fan_mode" to mode),
        mode,
    ) { entity -> entity.climateControls?.fanMode == mode }

    private fun approximately(a: Float?, b: Float?): Boolean =
        a != null && b != null && kotlin.math.abs(a - b) < TEMPERATURE_CONFIRMATION_TOLERANCE

    // Called under lock. Dispatch and confirmation run in a separate coroutine.
    @Suppress("LongParameterList", "LongMethod")
    private fun submit(
        target: Target,
        kind: CommandKind,
        action: String,
        data: Map<String, Any?>,
        value: String,
        single: Float? = null,
        low: Float? = null,
        high: Float? = null,
        confirmed: (EntityDisplay) -> Boolean,
    ) {
        val key = PendingKey(target, kind)
        val dispatched = CompletableDeferred<Unit>()
        val token = ++nextToken
        latest[key] = token
        // Invalidate only this target's outstanding refresh, even if the command completes first.
        refreshInFlight.remove(target)
        // Only registered after dispatch; never cancel a queued or in-flight service call.
        acknowledgementJobs.remove(key)?.cancel()
        store(key, Pending(value, Pending.Status.WAITING, single, low, high), updateTile = false)
        val predecessor = dispatchTails.put(target, dispatched)
        scope.launch {
            try {
                try {
                    predecessor?.await()
                    serverManager.integrationRepository(target.serverId).callAction("climate", action, data)
                } finally {
                    // Always release the following command, including after a failed service call.
                    withContext(NonCancellable) {
                        lock.withLock {
                            if (dispatchTails[target] === dispatched) dispatchTails.remove(target)
                            dispatched.complete(Unit)
                        }
                    }
                }
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
                            clear(key)
                        } else {
                            Timber.w("Thermostat %s unconfirmed for %s", action, target.entityId)
                            store(key, Pending(value, Pending.Status.FAILED))
                            latest.remove(key)
                        }
                    }
                } finally {
                    withContext(NonCancellable) {
                        lock.withLock {
                            if (acknowledgementJobs[key] === monitor) acknowledgementJobs.remove(key)
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Thermostat %s command failed for %s", action, target.entityId)
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
        suspend fun checkState(entity: EntityDisplayWithoutContext): Boolean = lock.withLock {
            if (latest[key] != token || entity.entityId != target.entityId) return false
            if (!rememberEntityLocked(target, entity)) return false
            confirmed(entity)
        }
        return readConfirmation(target, repository, ::checkState) ||
            subscribeForConfirmation(key, token, repository, ::checkState) ||
            pollForConfirmation(key, token, repository, ::checkState)
    }

    private suspend fun readConfirmation(
        target: Target,
        repository: IntegrationRepository,
        checkState: suspend (EntityDisplayWithoutContext) -> Boolean,
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
        checkState: suspend (EntityDisplayWithoutContext) -> Boolean,
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
        checkState: suspend (EntityDisplayWithoutContext) -> Boolean,
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

    private suspend fun isLatest(key: PendingKey, token: Long): Boolean = lock.withLock { latest[key] == token }

    private fun refresh() {
        // Redraw is a notification, not part of the protected state transaction.
        scope.launch {
            try {
                requestTileUpdate()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "Thermostat tile redraw request failed")
            }
        }
    }
}
