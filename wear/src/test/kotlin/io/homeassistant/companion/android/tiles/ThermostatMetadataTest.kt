package io.homeassistant.companion.android.tiles

import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithoutContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetConfigResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTime::class)
class ThermostatMetadataTest {
    private val serverManager = mockk<ServerManager>()
    private val displayManager = mockk<EntitiesForDisplayManager>()
    private val repository = mockk<WebSocketRepository>()
    private val target = ThermostatCommandCoordinator.Target(1, "climate.test")
    private var now = 1000L
    private val clock = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(now)
    }

    private fun entity(name: String, unit: String? = null) = EntityDisplayWithoutContext(
        Entity(
            entityId = target.entityId,
            state = "heat",
            attributes = mapOf("friendly_name" to name, "temperature_unit" to unit, "temperature" to 21f),
            lastChanged = LocalDateTime.MIN,
            lastUpdated = LocalDateTime.MIN,
        ),
    )

    @Test
    fun `Given registry metadata when state changes then the name and server fallback remain cached`() = runTest {
        coEvery { displayManager.registryDisplayName(1, target.entityId) } returns "Registry name"
        coEvery { serverManager.webSocketRepository(1) } returns repository
        coEvery { repository.getConfig() } returns mockk<GetConfigResponse> {
            every { unitSystem } returns mapOf("temperature" to "°C")
        }
        val metadata = ThermostatMetadata(serverManager, displayManager, clock)
        assertEquals(ThermostatMetadata.Resolved("Registry name", "°C"), metadata.resolve(target, entity("State one")))
        assertEquals(ThermostatMetadata.Resolved("Registry name", "°C"), metadata.resolve(target, entity("State two")))
        assertEquals(
            ThermostatMetadata.Resolved("Registry name", "°F"),
            metadata.resolve(target, entity("State three", "°F")),
        )
        coVerify(exactly = 1) { displayManager.registryDisplayName(1, target.entityId) }
        coVerify(exactly = 1) { repository.getConfig() }
    }

    @Test
    fun `Given no registry name when cached metadata expires then the new registry name is read`() = runTest {
        coEvery { displayManager.registryDisplayName(1, target.entityId) } returnsMany listOf(null, "Renamed")
        val metadata = ThermostatMetadata(serverManager, displayManager, clock)
        assertEquals(
            ThermostatMetadata.Resolved("Friendly name", "°F"),
            metadata.resolve(target, entity("Friendly name", "°F")),
        )
        now = 122_000L
        assertEquals(
            ThermostatMetadata.Resolved("Renamed", "°F"),
            metadata.resolve(target, entity("Friendly name", "°F")),
        )
        coVerify(exactly = 0) { serverManager.webSocketRepository(any()) }
    }
}
