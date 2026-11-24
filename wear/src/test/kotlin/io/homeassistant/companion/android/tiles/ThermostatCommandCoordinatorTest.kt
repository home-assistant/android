package io.homeassistant.companion.android.tiles

import android.content.Context
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
@OptIn(ExperimentalCoroutinesApi::class)
class ThermostatCommandCoordinatorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val servers = mockk<ServerManager>()
    private val repository = mockk<IntegrationRepository>()
    private val target = ThermostatCommandCoordinator.Target(1, "climate.living_room")

    @Before
    fun clearPendingState() {
        context.getSharedPreferences("thermostat_tile_commands", Context.MODE_PRIVATE).edit().clear().commit()
        coEvery { servers.integrationRepository(1) } returns repository
        coEvery { servers.getServer(1) } returns mockk<Server> {
            every { version } returns HomeAssistantVersion(2026, 9, 2)
        }
    }

    private fun state(temperature: Float) = Entity(
        entityId = target.entityId,
        state = "heat",
        attributes = mapOf("temperature" to temperature, "supported_features" to 1),
        lastChanged = LocalDateTime.of(2026, 1, 1, 0, 0),
        lastUpdated = LocalDateTime.of(2026, 1, 1, 0, 0),
    )

    @Test
    fun `queued calls dispatch in order without waiting for older acknowledgement`() = runTest {
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope) {}
        val gate = CompletableDeferred<Unit>()
        val sent = mutableListOf<Float>()
        coEvery { repository.callAction("climate", "set_temperature", any()) } coAnswers {
            val temperature = thirdArg<Map<String, Any?>>()["temperature"] as Float
            sent.add(temperature)
            if (temperature == 71f) gate.await()
        }
        coEvery { repository.getEntityChanges(any()) } returns flowOf(EntityChange.StateChanged(state(73f)))

        coordinator.submitTemperature(10, target, false, null, null, 71f)
        coordinator.submitTemperature(10, target, false, null, null, 72f)
        coordinator.submitTemperature(10, target, false, null, null, 73f)
        runCurrent()
        assertEquals(listOf(71f), sent)
        assertEquals(73f, coordinator.get(10, target)?.single)

        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(71f, 72f, 73f), sent)
        assertNull(coordinator.get(10, target))
        coVerify(exactly = 1) { repository.getEntityChanges(EntityFilter(entityIds = setOf(target.entityId))) }
        coVerify(exactly = 0) { repository.getEntity(any()) }
    }

    @Test
    fun `failed dispatch releases next command and fallback read confirms it`() = runTest {
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope) {}
        val sent = mutableListOf<Float>()
        coEvery { repository.callAction("climate", "set_temperature", any()) } coAnswers {
            val temperature = thirdArg<Map<String, Any?>>()["temperature"] as Float
            sent.add(temperature)
            if (temperature == 71f) error("Simulated service failure")
        }
        coEvery { repository.getEntityChanges(any()) } returns null
        coEvery { repository.getEntity(target.entityId) } returns state(72f)
        coordinator.submitTemperature(10, target, false, null, null, 71f)
        coordinator.submitTemperature(10, target, false, null, null, 72f)
        runCurrent()
        assertEquals(listOf(71f, 72f), sent)
        assertNull(coordinator.get(10, target))
        coVerify(exactly = 1) { repository.getEntity(target.entityId) }
    }

    @Test
    fun `same entity ID on another server has independent cache and pending state`() = runTest {
        val coordinator = ThermostatCommandCoordinator(context, servers, backgroundScope) {}
        val other = target.copy(serverId = 2)
        val firstEntity = EntityDisplayWithoutContext(state(71f))
        val secondEntity = EntityDisplayWithoutContext(state(76f))
        coordinator.rememberEntity(target, firstEntity)
        coordinator.rememberEntity(other, secondEntity)
        assertEquals(firstEntity, coordinator.cachedEntity(target))
        assertEquals(secondEntity, coordinator.cachedEntity(other))
        coordinator.submitTemperature(10, target, false, null, null, 72f)
        assertEquals(72f, coordinator.get(10, target)?.single)
        assertNull(coordinator.get(10, other))
        assertNull(coordinator.get(11, target))
        val otherRepository = mockk<IntegrationRepository>()
        coEvery { servers.integrationRepository(2) } returns otherRepository
        coEvery { servers.getServer(2) } returns null
        coEvery { servers.getServer(1) } returns null
        coEvery { repository.callAction("climate", "set_temperature", any()) } returns Unit
        coEvery { otherRepository.callAction("climate", "set_temperature", any()) } returns Unit
        coEvery { repository.getEntity(target.entityId) } returns state(72f)
        coEvery { otherRepository.getEntity(target.entityId) } returns state(77f)
        coordinator.submitTemperature(10, other, false, null, null, 77f)
        runCurrent()
        coVerify(exactly = 1) { repository.callAction("climate", "set_temperature", mapOf("entity_id" to target.entityId, "temperature" to 72f)) }
        coVerify(exactly = 1) { otherRepository.callAction("climate", "set_temperature", mapOf("entity_id" to target.entityId, "temperature" to 77f)) }
        coVerify(exactly = 0) { repository.getEntityChanges(any()) }
        coVerify(exactly = 0) { otherRepository.getEntityChanges(any()) }
        assertNull(coordinator.get(10, target))
        assertNull(coordinator.get(10, other))
    }

    @Test
    fun `active server placeholder cannot be used as a command target`() {
        assertThrows(IllegalArgumentException::class.java) {
            ThermostatCommandCoordinator.Target(ServerManager.SERVER_ID_ACTIVE, target.entityId)
        }
    }
}
