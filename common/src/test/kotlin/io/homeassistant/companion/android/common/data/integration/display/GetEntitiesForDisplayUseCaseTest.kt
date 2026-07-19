package io.homeassistant.companion.android.common.data.integration.display

import android.content.Context
import app.cash.turbine.test
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryDisplayEntry
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryDisplayResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryOptions
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistrySensorOptions
import io.homeassistant.companion.android.common.data.websocket.impl.entities.FloorRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class GetEntitiesForDisplayUseCaseTest {

    private val context: Context = mockk()
    private val serverManager: ServerManager = mockk()
    private val webSocketRepository: WebSocketRepository = mockk()
    private val integrationRepository: IntegrationRepository = mockk()
    private lateinit var useCase: GetEntitiesForDisplayUseCase

    private val serverId = 1

    @BeforeEach
    fun setUp() {
        coEvery { serverManager.webSocketRepository(serverId) } returns webSocketRepository
        coEvery { webSocketRepository.getDeviceRegistry() } returns emptyList()
        coEvery { webSocketRepository.getAreaRegistry() } returns emptyList()
        coEvery { webSocketRepository.getFloorRegistry() } returns emptyList()
        coEvery { webSocketRepository.getEntityRegistry() } returns emptyList()
        coEvery { webSocketRepository.getEntityRegistryDisplay() } returns EntityRegistryDisplayResponse()
        useCase = GetEntitiesForDisplayUseCase(context, serverManager)
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    private fun givenServerVersion(version: String?) {
        val server = mockk<Server>()
        every { server.version } returns version?.let { HomeAssistantVersion.fromString(it) }
        coEvery { serverManager.getServer(serverId) } returns server
    }

    // No mdi icon attribute so Entity.getIcon stays on the context-free domain default branch
    private fun entity(entityId: String, friendlyName: String? = null) = Entity(
        entityId = entityId,
        state = "on",
        attributes = friendlyName?.let { mapOf<String, Any?>("friendly_name" to it) }.orEmpty(),
        lastChanged = LocalDateTime.MIN,
        lastUpdated = LocalDateTime.MIN,
    )

    /** Asserts the flow emits Loading then a terminal Loaded state, returning the items. */
    private suspend fun Flow<EntityDisplayState>.awaitLoaded(): List<EntityDisplayItem> {
        var items: List<EntityDisplayItem> = emptyList()
        test {
            assertEquals(EntityDisplayState.Loading, awaitItem())
            items = assertInstanceOf(EntityDisplayState.Loaded::class.java, awaitItem()).entities.toList()
            awaitComplete()
        }
        return items
    }

    @Test
    fun `Given server 2024 10 when invoking then display registry is used and classic is not fetched`() = runTest {
        givenServerVersion("2024.10.0")
        coEvery { webSocketRepository.getEntityRegistryDisplay() } returns EntityRegistryDisplayResponse(
            entities = listOf(EntityRegistryDisplayEntry(entityId = "light.bed", name = "Bed")),
        )

        val items = useCase(serverId = serverId, entities = listOf(entity("light.bed", "Bed Light"))).awaitLoaded()

        assertEquals("Bed", items.single().name)
        coVerify(exactly = 0) { webSocketRepository.getEntityRegistry() }
    }

    @Test
    fun `Given server 2024 9 when invoking then classic registry is used and display is not fetched`() = runTest {
        givenServerVersion("2024.9.3")
        coEvery { webSocketRepository.getEntityRegistry() } returns listOf(
            EntityRegistryResponse(entityId = "light.bed", areaId = "bedroom"),
        )
        coEvery { webSocketRepository.getAreaRegistry() } returns listOf(
            AreaRegistryResponse(areaId = "bedroom", name = "Bedroom"),
        )

        val items = useCase(serverId = serverId, entities = listOf(entity("light.bed", "Bed Light"))).awaitLoaded()

        assertEquals("Bed Light", items.single().name)
        assertEquals("Bedroom", items.single().areaName)
        coVerify(exactly = 0) { webSocketRepository.getEntityRegistryDisplay() }
    }

    @Test
    fun `Given server older than 2024 3 when invoking then floor registry is not fetched`() = runTest {
        givenServerVersion("2024.2.0")

        useCase(serverId = serverId, entities = listOf(entity("light.bed", "Bed Light"))).awaitLoaded()

        coVerify(exactly = 0) { webSocketRepository.getFloorRegistry() }
    }

    @Test
    fun `Given server 2024 3 when invoking then floor registry is fetched`() = runTest {
        givenServerVersion("2024.3.0")
        coEvery { webSocketRepository.getFloorRegistry() } returns listOf(
            FloorRegistryResponse(floorId = "first", name = "First floor"),
        )

        useCase(serverId = serverId, entities = listOf(entity("light.bed", "Bed Light"))).awaitLoaded()

        coVerify(exactly = 1) { webSocketRepository.getFloorRegistry() }
    }

    @Test
    fun `Given display registry failure when invoking then falls back to classic registry`() = runTest {
        givenServerVersion("2025.1.0")
        coEvery { webSocketRepository.getEntityRegistryDisplay() } returns null
        coEvery { webSocketRepository.getEntityRegistry() } returns listOf(
            EntityRegistryResponse(entityId = "light.bed", hiddenBy = "user"),
        )

        val items = useCase(serverId = serverId, entities = listOf(entity("light.bed", "Bed Light"))).awaitLoaded()

        assertEquals(true, items.single().isHidden)
        coVerify(exactly = 1) { webSocketRepository.getEntityRegistry() }
    }

    @Test
    fun `Given all registries failing when invoking then metadata degrades without an error state`() = runTest {
        givenServerVersion("2025.1.0")
        coEvery { webSocketRepository.getEntityRegistryDisplay() } throws IllegalStateException("boom")
        coEvery { webSocketRepository.getEntityRegistry() } throws IllegalStateException("boom")
        coEvery { webSocketRepository.getDeviceRegistry() } throws IllegalStateException("boom")
        coEvery { webSocketRepository.getAreaRegistry() } throws IllegalStateException("boom")
        coEvery { webSocketRepository.getFloorRegistry() } throws IllegalStateException("boom")

        val items = useCase(serverId = serverId, entities = listOf(entity("light.bed", "Bed Light"))).awaitLoaded()

        assertEquals("Bed Light", items.single().name)
        assertNull(items.single().areaName)
    }

    @Test
    fun `Given unknown server version when invoking then classic registry is used`() = runTest {
        givenServerVersion(null)

        useCase(serverId = serverId, entities = listOf(entity("light.bed", "Bed Light"))).awaitLoaded()

        coVerify(exactly = 0) { webSocketRepository.getEntityRegistryDisplay() }
        coVerify(exactly = 1) { webSocketRepository.getEntityRegistry() }
    }

    @Test
    fun `Given no entity list when invoking then entities are fetched from the server`() = runTest {
        givenServerVersion("2024.10.0")
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { integrationRepository.getEntities() } returns listOf(entity("light.bed", "Bed Light"))
        coEvery { webSocketRepository.getEntityRegistryDisplay() } returns EntityRegistryDisplayResponse(
            entities = listOf(EntityRegistryDisplayEntry(entityId = "light.bed", name = "Bed")),
        )

        val items = useCase(serverId = serverId).awaitLoaded()

        assertEquals("Bed", items.single().name)
        coVerify(exactly = 1) { integrationRepository.getEntities() }
    }

    @Test
    fun `Given a filter when invoking without list then only matching entities are resolved`() = runTest {
        givenServerVersion("2024.10.0")
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { integrationRepository.getEntities() } returns listOf(
            entity("light.bed", "Bed Light"),
            entity("switch.fan", "Fan"),
        )

        val items = useCase(serverId = serverId) { it.domain == "light" }.awaitLoaded()

        assertEquals(listOf("light.bed"), items.map { it.entityId })
    }

    @Test
    fun `Given entities fetch failure when invoking without list then flow completes with error`() = runTest {
        givenServerVersion("2024.10.0")
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { integrationRepository.getEntities() } throws IllegalStateException("boom")

        useCase(serverId = serverId).test {
            assertEquals(EntityDisplayState.Loading, awaitItem())
            assertEquals(EntityDisplayState.Error, awaitItem())
            awaitComplete()
        }
        coVerify(exactly = 0) { webSocketRepository.getEntityRegistryDisplay() }
    }

    @Test
    fun `Given null entities response when invoking without list then flow completes with error`() = runTest {
        givenServerVersion("2024.10.0")
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { integrationRepository.getEntities() } returns null

        useCase(serverId = serverId).test {
            assertEquals(EntityDisplayState.Loading, awaitItem())
            assertEquals(EntityDisplayState.Error, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `Given no entities when invoking then loaded empty is emitted without registry fetch`() = runTest {
        givenServerVersion("2025.1.0")

        val items = useCase(serverId = serverId, entities = emptyList()).awaitLoaded()

        assertEquals(emptyList<EntityDisplayItem>(), items)
        coVerify(exactly = 0) { webSocketRepository.getEntityRegistryDisplay() }
        coVerify(exactly = 0) { webSocketRepository.getDeviceRegistry() }
    }

    @Nested
    inner class Resolution {

        private fun givenDisplayEntries(
            vararg entries: EntityRegistryDisplayEntry,
            categories: Map<Int, String> = emptyMap(),
        ) {
            givenServerVersion("2024.10.0")
            coEvery { webSocketRepository.getEntityRegistryDisplay() } returns EntityRegistryDisplayResponse(
                entityCategories = categories,
                entities = entries.toList(),
            )
        }

        @Test
        fun `Given display entry without name when resolving then name falls back to friendly name`() = runTest {
            givenDisplayEntries(EntityRegistryDisplayEntry(entityId = "light.bed"))

            val items = useCase(serverId = serverId, entities = listOf(entity("light.bed", "Bed Light"))).awaitLoaded()

            assertEquals("Bed Light", items.single().name)
        }

        @Test
        fun `Given no friendly name when resolving then name falls back to entity id`() = runTest {
            givenServerVersion("2025.1.0")

            val items = useCase(serverId = serverId, entities = listOf(entity("light.bed"))).awaitLoaded()

            assertEquals("light.bed", items.single().name)
        }

        @Test
        fun `Given entity area when resolving then entity area wins over device area`() = runTest {
            givenDisplayEntries(
                EntityRegistryDisplayEntry(entityId = "light.bed", areaId = "bedroom", deviceId = "device1"),
            )
            coEvery { webSocketRepository.getDeviceRegistry() } returns listOf(
                DeviceRegistryResponse(id = "device1", name = "Hub", areaId = "kitchen"),
            )
            coEvery { webSocketRepository.getAreaRegistry() } returns listOf(
                AreaRegistryResponse(areaId = "bedroom", name = "Bedroom"),
                AreaRegistryResponse(areaId = "kitchen", name = "Kitchen"),
            )

            val items = useCase(serverId = serverId, entities = listOf(entity("light.bed"))).awaitLoaded()

            assertEquals("Bedroom", items.single().areaName)
        }

        @Test
        fun `Given entity without area when resolving then area falls back to device area`() = runTest {
            givenDisplayEntries(EntityRegistryDisplayEntry(entityId = "light.bed", deviceId = "device1"))
            coEvery { webSocketRepository.getDeviceRegistry() } returns listOf(
                DeviceRegistryResponse(id = "device1", name = "Hub", areaId = "kitchen"),
            )
            coEvery { webSocketRepository.getAreaRegistry() } returns listOf(
                AreaRegistryResponse(areaId = "kitchen", name = "Kitchen"),
            )

            val items = useCase(serverId = serverId, entities = listOf(entity("light.bed"))).awaitLoaded()

            assertEquals("Kitchen", items.single().areaName)
        }

        @Test
        fun `Given area with floor when resolving then floor name is resolved`() = runTest {
            givenDisplayEntries(EntityRegistryDisplayEntry(entityId = "light.bed", areaId = "bedroom"))
            coEvery { webSocketRepository.getAreaRegistry() } returns listOf(
                AreaRegistryResponse(areaId = "bedroom", name = "Bedroom", floorId = "first"),
            )
            coEvery { webSocketRepository.getFloorRegistry() } returns listOf(
                FloorRegistryResponse(floorId = "first", name = "First floor"),
            )

            val items = useCase(serverId = serverId, entities = listOf(entity("light.bed"))).awaitLoaded()

            assertEquals("First floor", items.single().floorName)
        }

        @Test
        fun `Given device with user name when resolving then user name wins over device name`() = runTest {
            givenDisplayEntries(EntityRegistryDisplayEntry(entityId = "light.bed", deviceId = "device1"))
            coEvery { webSocketRepository.getDeviceRegistry() } returns listOf(
                DeviceRegistryResponse(id = "device1", name = "Hub", nameByUser = "My Hub"),
            )

            val items = useCase(serverId = serverId, entities = listOf(entity("light.bed"))).awaitLoaded()

            assertEquals("My Hub", items.single().deviceName)
        }

        @Test
        fun `Given classic entry when resolving then hidden category and precision are mapped`() = runTest {
            givenServerVersion("2024.9.0")
            coEvery { webSocketRepository.getEntityRegistry() } returns listOf(
                EntityRegistryResponse(
                    entityId = "sensor.temp",
                    hiddenBy = "user",
                    entityCategory = "diagnostic",
                    options = EntityRegistryOptions(
                        sensor = EntityRegistrySensorOptions(
                            displayPrecision = null,
                            suggestedDisplayPrecision = 2,
                        ),
                    ),
                ),
            )

            val item = useCase(serverId = serverId, entities = listOf(entity("sensor.temp", "Temp")))
                .awaitLoaded()
                .single()

            assertEquals(true, item.isHidden)
            assertEquals(EntityCategory.DIAGNOSTIC, item.entityCategory)
            assertEquals(2, item.displayPrecision)
        }

        @Test
        fun `Given display entry when resolving then hidden category precision and labels are mapped`() = runTest {
            givenDisplayEntries(
                EntityRegistryDisplayEntry(
                    entityId = "sensor.temp",
                    entityCategory = 0,
                    hidden = true,
                    displayPrecision = 1,
                    labels = listOf("label1"),
                ),
                categories = mapOf(0 to "config"),
            )

            val item = useCase(serverId = serverId, entities = listOf(entity("sensor.temp")))
                .awaitLoaded()
                .single()

            assertEquals(true, item.isHidden)
            assertEquals(EntityCategory.CONFIG, item.entityCategory)
            assertEquals(1, item.displayPrecision)
            assertEquals(listOf("label1"), item.labels)
        }

        @Test
        fun `Given no custom icon when resolving then icon derives from the entity`() = runTest {
            givenServerVersion("2025.1.0")
            val lightEntity = entity("light.bed")

            val items = useCase(serverId = serverId, entities = listOf(lightEntity)).awaitLoaded()

            // The domain default branch of Entity.getIcon does not touch the context
            assertEquals(lightEntity.getIcon(context), items.single().icon)
        }

        @Test
        fun `Given entities when resolving then order and count are preserved`() = runTest {
            givenServerVersion("2025.1.0")

            val items = useCase(
                serverId = serverId,
                entities = listOf(entity("light.bed"), entity("switch.fan"), entity("sensor.temp")),
            ).awaitLoaded()

            assertEquals(listOf("light.bed", "switch.fan", "sensor.temp"), items.map { it.entityId })
        }

        @ParameterizedTest
        @CsvSource(
            "light.bed, light",
            "sensor.temperature, sensor",
            "binary_sensor.motion, binary_sensor",
        )
        fun `Given entity id when reading item domain then it is derived from the entity id`(
            entityId: String,
            expectedDomain: String,
        ) {
            val item = EntityDisplayItem(
                entityId = entityId,
                name = "Name",
                icon = CommunityMaterial.Icon.cmd_bookmark,
            )
            assertEquals(expectedDomain, item.domain)
        }
    }
}
