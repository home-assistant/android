package io.homeassistant.companion.android.tiles

import android.content.Context
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityChange
import io.homeassistant.companion.android.common.data.integration.EntityFilter
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.wear.ThermostatTemperatureUnit
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class ThermostatCommandCoordinatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val servers = mockk<ServerManager>()
    private val repository = mockk<IntegrationRepository>()
    private val target = ThermostatCommandCoordinator.Target(1, "climate.living_room")

    @Before
    fun setUp() {
        coEvery { servers.integrationRepository(1) } returns repository
        coEvery { servers.getServer(1) } returns mockk<Server> {
            every { version } returns HomeAssistantVersion(2026, 9, 2)
        }
    }

    private fun TestScope.testClock(): Clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(100_000L + testScheduler.currentTime)
    }

    private fun observationContext(interactive: () -> Boolean = { true }): Context {
        val powerManager = mockk<PowerManager>()
        every { powerManager.isInteractive } answers { interactive() }
        return mockk<Context>().also {
            every { it.getSystemService(PowerManager::class.java) } returns powerManager
        }
    }

    private fun state(
        temperature: Float,
        updated: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0),
    ) = Entity(
        entityId = target.entityId,
        state = "heat",
        attributes = mapOf("temperature" to temperature, "supported_features" to 1),
        lastChanged = LocalDateTime.of(2026, 1, 1, 0, 0),
        lastUpdated = updated,
    )

    @Test
    fun `Given immediate confirmation when a command completes then skip subscription and refresh`() = runTest {
        var refreshes = 0
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, clock) { refreshes++ }
        coEvery { repository.callAction("climate", "set_temperature", any()) } returns Unit
        coEvery { repository.getEntity(target.entityId) } returns state(72f)
        coordinator.submitTemperature(target, false, null, null, 72f)
        assertEquals(72f, coordinator.get(target)?.single)
        assertEquals(0, refreshes)
        runCurrent()
        assertNull(coordinator.get(target))
        assertEquals(1, refreshes)
        assertEquals(EntityDisplayWithoutContext(state(72f)), coordinator.cachedEntity(target))
        coVerify(exactly = 1) { repository.getEntity(target.entityId) }
        coVerify(exactly = 0) { repository.getEntityChanges(any()) }
    }

    @Test
    fun `Given a redraw failure when a command confirms then its pending state is still cleared`() = runTest {
        val clock = testClock()
        var redraws = 0
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, clock) {
            redraws++
            error("Simulated redraw failure")
        }
        coEvery { repository.callAction("climate", "set_temperature", any()) } returns Unit
        coEvery { repository.getEntity(target.entityId) } returns state(72f)

        coordinator.submitTemperature(target, false, null, null, 72f)
        runCurrent()

        assertNull(coordinator.get(target))
        assertEquals(EntityDisplayWithoutContext(state(72f)), coordinator.cachedEntity(target))
        assertEquals(1, redraws)
    }

    @Test
    fun `Given queued commands when dispatched then preserve order without waiting for confirmation`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, clock) {}
        val gate = CompletableDeferred<Unit>()
        val sent = mutableListOf<Float>()
        coEvery { repository.callAction("climate", "set_temperature", any()) } coAnswers {
            val temperature = thirdArg<Map<String, Any?>>()["temperature"] as Float
            sent.add(temperature)
            if (temperature == 71f) gate.await()
        }
        coEvery { repository.getEntity(target.entityId) } returns state(70f)
        coEvery { repository.getEntityChanges(any()) } returns flowOf(EntityChange.StateChanged(state(73f)))

        coordinator.submitTemperature(target, false, null, null, 71f)
        coordinator.submitTemperature(target, false, null, null, 72f)
        coordinator.submitTemperature(target, false, null, null, 73f)
        runCurrent()
        assertEquals(listOf(71f), sent)
        assertEquals(73f, coordinator.get(target)?.single)

        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(71f, 72f, 73f), sent)
        assertNull(coordinator.get(target))
        coVerify(exactly = 1) { repository.getEntityChanges(EntityFilter(entityIds = setOf(target.entityId))) }
        coVerify(exactly = 1) { repository.getEntity(any()) }
    }

    @Test
    fun `Given a failed dispatch when the next command runs then fallback read confirms it`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, clock) {}
        val sent = mutableListOf<Float>()
        coEvery { repository.callAction("climate", "set_temperature", any()) } coAnswers {
            val temperature = thirdArg<Map<String, Any?>>()["temperature"] as Float
            sent.add(temperature)
            if (temperature == 71f) error("Simulated service failure")
        }
        coEvery { repository.getEntityChanges(any()) } returns null
        coEvery { repository.getEntity(target.entityId) } returnsMany listOf(null, state(72f))
        coordinator.submitTemperature(target, false, null, null, 71f)
        coordinator.submitTemperature(target, false, null, null, 72f)
        runCurrent()
        assertEquals(listOf(71f, 72f), sent)
        assertNull(coordinator.get(target))
        coVerify(exactly = 2) { repository.getEntity(target.entityId) }
    }

    @Test
    fun `Given matching entity IDs when using separate servers then keep cache and pending independent`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, clock) {}
        val other = target.copy(serverId = 2)
        val firstEntity = EntityDisplayWithoutContext(state(71f))
        val secondEntity = EntityDisplayWithoutContext(state(76f))
        coordinator.rememberEntity(target, firstEntity)
        coordinator.rememberEntity(other, secondEntity)
        assertEquals(firstEntity, coordinator.cachedEntity(target))
        assertEquals(secondEntity, coordinator.cachedEntity(other))
        coordinator.submitTemperature(target, false, null, null, 72f)
        assertEquals(72f, coordinator.get(target)?.single)
        assertNull(coordinator.get(other))
        val otherRepository = mockk<IntegrationRepository>()
        coEvery { servers.integrationRepository(2) } returns otherRepository
        coEvery { servers.getServer(2) } returns null
        coEvery { servers.getServer(1) } returns null
        coEvery { repository.callAction("climate", "set_temperature", any()) } returns Unit
        coEvery { otherRepository.callAction("climate", "set_temperature", any()) } returns Unit
        coEvery { repository.getEntity(target.entityId) } returns state(72f)
        coEvery { otherRepository.getEntity(target.entityId) } returns state(77f)
        coordinator.submitTemperature(other, false, null, null, 77f)
        runCurrent()
        coVerify(exactly = 1) { repository.callAction("climate", "set_temperature", mapOf("entity_id" to target.entityId, "temperature" to 72f)) }
        coVerify(exactly = 1) { otherRepository.callAction("climate", "set_temperature", mapOf("entity_id" to target.entityId, "temperature" to 77f)) }
        coVerify(exactly = 0) { repository.getEntityChanges(any()) }
        coVerify(exactly = 0) { otherRepository.getEntityChanges(any()) }
        assertNull(coordinator.get(target))
        assertNull(coordinator.get(other))
    }

    @Test
    fun `Given live observation when external state changes then cache and tile update without a command`() = runTest {
        var refreshes = 0
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(observationContext(), servers, backgroundScope, clock) {
            refreshes++
        }
        val changes = MutableSharedFlow<EntityChange>(extraBufferCapacity = 8)
        val initial = state(70f)
        val newer = state(72f, LocalDateTime.of(2026, 1, 1, 0, 0, 2))
        coEvery { repository.getEntity(target.entityId) } returns initial
        coEvery { repository.getEntityChanges(any()) } returns changes
        coordinator.rememberEntity(target, EntityDisplayWithoutContext(initial))

        coordinator.observeVisibleTile(10, target, clock.now().toEpochMilliseconds())
        runCurrent()
        assertEquals(1, changes.subscriptionCount.value)
        val initialRefreshes = refreshes

        check(changes.tryEmit(EntityChange.StateChanged(newer)))
        runCurrent()
        assertEquals(EntityDisplayWithoutContext(newer), coordinator.cachedEntity(target))
        assertEquals(initialRefreshes + 1, refreshes)

        // Identical and older snapshots must not redraw or roll back confirmed state.
        check(changes.tryEmit(EntityChange.StateChanged(newer)))
        check(changes.tryEmit(EntityChange.StateChanged(initial)))
        runCurrent()
        assertEquals(EntityDisplayWithoutContext(newer), coordinator.cachedEntity(target))
        assertEquals(initialRefreshes + 1, refreshes)
        coVerify(exactly = 0) { repository.callAction(any(), any(), any()) }
    }

    @Test
    fun `Given active observation when visibility repeats and tile leaves then cancel one subscription`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(observationContext(), servers, backgroundScope, clock) {}
        val changes = MutableSharedFlow<EntityChange>()
        coEvery { repository.getEntity(target.entityId) } returns state(70f)
        coEvery { repository.getEntityChanges(any()) } returns changes
        val enteredAt = clock.now().toEpochMilliseconds() - 100L

        coordinator.observeVisibleTile(10, target, enteredAt)
        runCurrent()
        coordinator.observeVisibleTile(10, target, enteredAt)
        coordinator.observeVisibleTile(10, target, enteredAt + 1L)
        runCurrent()
        assertEquals(1, changes.subscriptionCount.value)
        coVerify(exactly = 1) { repository.getEntityChanges(EntityFilter(entityIds = setOf(target.entityId))) }

        coordinator.stopObservingTile(11, enteredAt + 2L)
        coordinator.stopObservingTile(10, enteredAt)
        runCurrent()
        assertEquals(1, changes.subscriptionCount.value)

        coordinator.stopObservingTile(10, enteredAt + 2L)
        runCurrent()
        assertEquals(0, changes.subscriptionCount.value)
    }

    @Test
    fun `Given live observation when the screen sleeps then the subscription is cancelled`() = runTest {
        var interactive = true
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(
            observationContext { interactive },
            servers,
            backgroundScope,
            clock,
        ) {}
        val changes = MutableSharedFlow<EntityChange>()
        coEvery { repository.getEntity(target.entityId) } returns state(70f)
        coEvery { repository.getEntityChanges(any()) } returns changes

        coordinator.observeVisibleTile(10, target, clock.now().toEpochMilliseconds())
        runCurrent()
        assertEquals(1, changes.subscriptionCount.value)

        interactive = false
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(0, changes.subscriptionCount.value)
    }

    @Test
    fun `Given expired observation when visibility is stale then observation does not restart`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(observationContext(), servers, backgroundScope, clock) {}
        val changes = MutableSharedFlow<EntityChange>()
        coEvery { repository.getEntity(target.entityId) } returns state(70f)
        coEvery { repository.getEntityChanges(any()) } returns changes
        val enteredAt = clock.now().toEpochMilliseconds()

        coordinator.observeVisibleTile(10, target, enteredAt)
        runCurrent()
        assertEquals(1, changes.subscriptionCount.value)

        advanceTimeBy(90_000L)
        runCurrent()
        assertEquals(0, changes.subscriptionCount.value)

        coordinator.observeVisibleTile(10, target, enteredAt)
        coordinator.observeVisibleTile(10, target, clock.now().toEpochMilliseconds() - 20_000L)
        runCurrent()
        assertEquals(0, changes.subscriptionCount.value)
        coVerify(exactly = 1) { repository.getEntityChanges(any()) }
    }

    @Test
    fun `Given an unavailable subscription when observing then retry within the observation window`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(observationContext(), servers, backgroundScope, clock) {}
        val changes = MutableSharedFlow<EntityChange>()
        coEvery { repository.getEntity(target.entityId) } returns state(70f)
        coEvery { repository.getEntityChanges(any()) } returnsMany listOf(null, changes)

        coordinator.observeVisibleTile(10, target, clock.now().toEpochMilliseconds())
        runCurrent()
        assertEquals(0, changes.subscriptionCount.value)
        coVerify(exactly = 1) { repository.getEntityChanges(any()) }

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(1, changes.subscriptionCount.value)
        coVerify(exactly = 2) { repository.getEntityChanges(any()) }
    }

    @Test
    fun `Given a command in flight when live events arrive then pending state is preserved`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(observationContext(), servers, backgroundScope, clock) {}
        val changes = MutableSharedFlow<EntityChange>(extraBufferCapacity = 8)
        val initial = state(70f)
        val intermediate = state(71f, LocalDateTime.of(2026, 1, 1, 0, 0, 1))
        val confirmed = state(72f, LocalDateTime.of(2026, 1, 1, 0, 0, 2))
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.getEntity(target.entityId) } returns initial
        coEvery { repository.getEntityChanges(any()) } returns changes
        coEvery { repository.callAction("climate", "set_temperature", any()) } coAnswers { gate.await() }
        coordinator.rememberEntity(target, EntityDisplayWithoutContext(initial))

        coordinator.observeVisibleTile(10, target, clock.now().toEpochMilliseconds())
        runCurrent()
        coordinator.submitTemperature(target, false, null, null, 72f)
        runCurrent()
        check(changes.tryEmit(EntityChange.StateChanged(intermediate)))
        runCurrent()
        assertEquals(72f, coordinator.get(target)?.single)
        assertEquals(EntityDisplayWithoutContext(initial), coordinator.cachedEntity(target))

        coEvery { repository.getEntity(target.entityId) } returns confirmed
        gate.complete(Unit)
        runCurrent()
        assertNull(coordinator.get(target))
        assertEquals(EntityDisplayWithoutContext(confirmed), coordinator.cachedEntity(target))

        check(changes.tryEmit(EntityChange.StateChanged(intermediate)))
        runCurrent()
        assertEquals(EntityDisplayWithoutContext(confirmed), coordinator.cachedEntity(target))
        coVerify(exactly = 1) { repository.callAction("climate", "set_temperature", any()) }
    }

    @Test
    fun `Given cached state when virtual cache lifetime elapses then it expires`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, clock) {}
        val entity = EntityDisplayWithoutContext(state(70f))
        coordinator.rememberEntity(target, entity)

        advanceTimeBy(119_999L)
        assertEquals(entity, coordinator.cachedEntity(target))
        advanceTimeBy(1L)
        assertNull(coordinator.cachedEntity(target))
    }

    @Test
    fun `Given another server refresh when a command is submitted then refresh is retained`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, clock) {}
        val other = target.copy(serverId = 2)
        val otherRepository = mockk<IntegrationRepository>()
        val response = CompletableDeferred<Entity>()
        coEvery { servers.integrationRepository(2) } returns otherRepository
        coEvery { otherRepository.getEntity(other.entityId) } coAnswers { response.await() }
        coEvery { repository.callAction("climate", "set_temperature", any()) } returns Unit
        coEvery { repository.getEntity(target.entityId) } returns state(72f)

        coordinator.refreshEntityIfStale(other, 0L)
        runCurrent()
        coordinator.submitTemperature(target, false, null, null, 72f)
        runCurrent()
        response.complete(state(76f))
        runCurrent()

        assertEquals(EntityDisplayWithoutContext(state(76f)), coordinator.cachedEntity(other))
    }

    @Test
    fun `Given a fresh cache when manual refresh is requested then bypass age and coalesce reads`() = runTest {
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, testClock()) {}
        coordinator.rememberEntity(target, EntityDisplayWithoutContext(state(70f)))
        val response = CompletableDeferred<Entity>()
        coEvery { repository.getEntity(target.entityId) } coAnswers { response.await() }
        coordinator.refreshEntityIfStale(target, 5_000L)
        runCurrent()
        coVerify(exactly = 0) { repository.getEntity(target.entityId) }
        coordinator.refreshEntityIfStale(target, 0L)
        coordinator.refreshEntityIfStale(target, 0L)
        runCurrent()
        coVerify(exactly = 1) { repository.getEntity(target.entityId) }
        response.complete(state(75f))
        runCurrent()
        assertEquals(EntityDisplayWithoutContext(state(75f)), coordinator.cachedEntity(target))
        coordinator.refreshEntityIfStale(target, 0L)
        runCurrent()
        coVerify(exactly = 2) { repository.getEntity(target.entityId) }
    }

    @Test
    fun `Given outstanding refresh when same target command fails then old refresh is discarded`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, clock) {}
        val response = CompletableDeferred<Entity>()
        coEvery { repository.getEntity(target.entityId) } coAnswers { response.await() }
        coEvery { repository.callAction("climate", "set_temperature", any()) } throws IllegalStateException()

        coordinator.refreshEntityIfStale(target, 0L)
        runCurrent()
        coordinator.submitTemperature(target, false, null, null, 72f)
        runCurrent()
        response.complete(state(70f))
        runCurrent()

        assertNull(coordinator.cachedEntity(target))
        assertEquals(ThermostatCommandCoordinator.Pending.Status.FAILED, coordinator.get(target)?.status)
    }

    @Test
    fun `Given newer cached state when stale matching response arrives then command remains pending`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, clock) {}
        val newer = LocalDateTime.of(2026, 1, 1, 0, 0, 2)
        val changes = MutableSharedFlow<EntityChange>(extraBufferCapacity = 1)
        coordinator.rememberEntity(target, EntityDisplayWithoutContext(state(70f, newer)))
        coEvery { repository.callAction("climate", "set_temperature", any()) } returns Unit
        coEvery { repository.getEntity(target.entityId) } returns state(72f, newer.minusSeconds(1))
        coEvery { repository.getEntityChanges(any()) } returns changes

        coordinator.submitTemperature(target, false, null, null, 72f)
        runCurrent()

        assertEquals(ThermostatCommandCoordinator.Pending.Status.WAITING, coordinator.get(target)?.status)
        assertEquals(EntityDisplayWithoutContext(state(70f, newer)), coordinator.cachedEntity(target))

        check(changes.tryEmit(EntityChange.StateChanged(state(72f, newer.plusSeconds(1)))))
        runCurrent()
        assertNull(coordinator.get(target))
        assertEquals(EntityDisplayWithoutContext(state(72f, newer.plusSeconds(1))), coordinator.cachedEntity(target))
    }

    @Test
    fun `Given rapid adjustments when fan command intervenes then both increments are retained`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, clock) {}
        val gate = CompletableDeferred<Unit>()
        val snapshot = EntityDisplayWithoutContext(
            state(70f).copy(
                attributes = mapOf(
                    "temperature" to 70f,
                    "fan_mode" to "auto",
                    "fan_modes" to listOf("auto", "on"),
                    "supported_features" to 9,
                ),
            ),
        )
        val sent = mutableListOf<Pair<String, Any?>>()
        coEvery { repository.callAction("climate", any(), any()) } coAnswers {
            val action = secondArg<String>()
            val data = thirdArg<Map<String, Any?>>()
            sent.add(action to (data["temperature"] ?: data["fan_mode"]))
            gate.await()
        }
        coEvery { repository.getEntity(target.entityId) } returns state(70f)
        coEvery { repository.getEntityChanges(any()) } returns null
        coordinator.rememberEntity(target, snapshot)
        val units = ThermostatDisplayUnits("°F", ThermostatTemperatureUnit.DEFAULT)
        val slot = ThermostatCommandCoordinator.TemperatureSlot.SINGLE
        coordinator.adjustTemperature(target, units, slot, 1f)
        coordinator.cycleFan(target)
        coordinator.adjustTemperature(target, units, slot, 1f)
        coordinator.cycleFan(target)
        assertEquals(72f, coordinator.get(target)?.single)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(
            listOf(
                "set_temperature" to 71f,
                "set_fan_mode" to "on",
                "set_temperature" to 72f,
                "set_fan_mode" to "auto",
            ),
            sent,
        )
    }

    @Test
    fun `Given simultaneous taps when both use the same snapshot then neither adjustment is lost`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, clock) {}
        val gate = CompletableDeferred<Unit>()
        val snapshot = EntityDisplayWithoutContext(state(70f))
        coordinator.rememberEntity(target, snapshot)
        val units = ThermostatDisplayUnits("°F", ThermostatTemperatureUnit.DEFAULT)
        val slot = ThermostatCommandCoordinator.TemperatureSlot.SINGLE
        val sent = mutableListOf<Float>()
        coEvery { repository.callAction("climate", "set_temperature", any()) } coAnswers {
            sent.add(thirdArg<Map<String, Any?>>()["temperature"] as Float)
            gate.await()
        }
        coEvery { repository.getEntity(target.entityId) } returns state(72f)
        val taps = List(2) {
            async(Dispatchers.Default) { coordinator.adjustTemperature(target, units, slot, 1f) }
        }
        taps.awaitAll()
        assertEquals(72f, coordinator.get(target)?.single)
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(71f, 72f), sent)
        assertNull(coordinator.get(target))
    }

    @Test
    fun `Given a pending range when adjusting its other bound then both values are retained`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, clock) {}
        val snapshot = EntityDisplayWithoutContext(
            state(70f).copy(
                state = "heat_cool",
                attributes = mapOf("target_temp_low" to 67f, "target_temp_high" to 85f, "supported_features" to 2),
            ),
        )
        coordinator.rememberEntity(target, snapshot)
        val units = ThermostatDisplayUnits("°F", ThermostatTemperatureUnit.DEFAULT)
        coordinator.adjustTemperature(target, units, ThermostatCommandCoordinator.TemperatureSlot.HEAT, 1f)
        coordinator.adjustTemperature(target, units, ThermostatCommandCoordinator.TemperatureSlot.COOL, -1f)
        assertEquals(68f, coordinator.get(target)?.low)
        assertEquals(84f, coordinator.get(target)?.high)
    }

    @Test
    fun `Given an expired baseline when adjusting then uses current server temperature`() = runTest {
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, testClock()) {}
        coordinator.rememberEntity(target, EntityDisplayWithoutContext(state(70f)))
        advanceTimeBy(6_000L)
        coEvery { repository.getEntity(target.entityId) } returns state(75f)
        coEvery { repository.getEntityChanges(any()) } returns null
        coEvery { repository.callAction("climate", "set_temperature", any()) } returns Unit
        val units = ThermostatDisplayUnits("°F", ThermostatTemperatureUnit.DEFAULT)
        val slot = ThermostatCommandCoordinator.TemperatureSlot.SINGLE

        coordinator.adjustTemperature(target, units, slot, 1f)
        coordinator.adjustTemperature(target, units, slot, 1f)

        assertEquals(77f, coordinator.get(target)?.single)
        coVerify(exactly = 1) { repository.getEntity(target.entityId) }
        runCurrent()
        coVerify(exactly = 1) {
            repository.callAction("climate", "set_temperature", mapOf("entity_id" to target.entityId, "temperature" to 76f))
        }
        coVerify(exactly = 1) {
            repository.callAction("climate", "set_temperature", mapOf("entity_id" to target.entityId, "temperature" to 77f))
        }
    }

    @Test
    fun `Given an expired baseline when refresh fails then does not send an adjustment`() = runTest {
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, testClock()) {}
        coordinator.rememberEntity(target, EntityDisplayWithoutContext(state(70f)))
        advanceTimeBy(6_000L)
        coEvery { repository.getEntity(target.entityId) } returns null

        coordinator.adjustTemperature(
            target,
            ThermostatDisplayUnits("°F", ThermostatTemperatureUnit.DEFAULT),
            ThermostatCommandCoordinator.TemperatureSlot.SINGLE,
            1f,
        )
        runCurrent()

        assertEquals(ThermostatCommandCoordinator.Pending.Status.FAILED, coordinator.get(target)?.status)
        coVerify(exactly = 0) { repository.callAction(any(), any(), any()) }
    }

    @Test
    fun `Given failed command when display timeout elapses then failure is cleared`() = runTest {
        val clock = testClock()
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope, clock) {}
        coEvery { repository.callAction("climate", "set_temperature", any()) } throws IllegalStateException()
        coordinator.submitTemperature(target, false, null, null, 72f)
        runCurrent()
        assertEquals(ThermostatCommandCoordinator.Pending.Status.FAILED, coordinator.get(target)?.status)
        advanceTimeBy(15_000L)
        runCurrent()
        assertNull(coordinator.get(target))
    }

    @Test
    fun `Given an active server placeholder when creating a target then reject it`() {
        assertThrows(IllegalArgumentException::class.java) {
            ThermostatCommandCoordinator.Target(ServerManager.SERVER_ID_ACTIVE, target.entityId)
        }
    }
}
