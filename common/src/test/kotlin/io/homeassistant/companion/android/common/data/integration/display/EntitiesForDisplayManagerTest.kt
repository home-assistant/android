package io.homeassistant.companion.android.common.data.integration.display

import androidx.compose.ui.unit.LayoutDirection
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryDisplayEntry
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryDisplayResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryOptions
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistrySensorOptions
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryUpdatedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.FloorRegistryResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
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

class EntitiesForDisplayManagerTest {

    private val serverManager: ServerManager = mockk()
    private val webSocketRepository: WebSocketRepository = mockk()
    private val integrationRepository: IntegrationRepository = mockk()
    private lateinit var manager: EntitiesForDisplayManager

    private val serverId = 1

    @AfterEach
    fun tearDown() = unmockkAll()

    private fun entity(entityId: String, friendlyName: String? = null) = Entity(
        entityId = entityId,
        state = "on",
        attributes = friendlyName?.let { mapOf<String, Any?>("friendly_name" to it) }.orEmpty(),
        lastChanged = LocalDateTime.MIN,
        lastUpdated = LocalDateTime.MIN,
    )

    /**
     * Suspends until the flow is subscribed: the initial snapshot is emitted before the
     * subscriptions are established, so emitting right after it races.
     */
    private suspend fun MutableSharedFlow<*>.awaitSubscribed() = subscriptionCount.first { it > 0 }

    private val entityUpdates = MutableSharedFlow<Entity>()
    private val entityRegistryUpdates = MutableSharedFlow<EntityRegistryUpdatedEvent>()
    private val entityIds = listOf("light.bed")

    @BeforeEach
    fun setUp() {
        manager = EntitiesForDisplayManager(serverManager)
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.webSocketRepository(serverId) } returns webSocketRepository
        coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
        coEvery { integrationRepository.getEntities() } returns listOf(entity("light.bed", "Bed Light"))
        coEvery { integrationRepository.getEntityUpdates(entityIds) } returns entityUpdates
        coEvery { webSocketRepository.getEntityRegistryUpdates() } returns entityRegistryUpdates
    }

    private fun givenRegistryName(name: String) {
        coEvery { webSocketRepository.getEntityRegistryDisplay() } returns EntityRegistryDisplayResponse(
            entities = listOf(EntityRegistryDisplayEntry(entityId = "light.bed", name = name)),
        )
    }

    /** Asserts the flow emits Loading then the initial snapshot, returning it. */
    private suspend fun ReceiveTurbine<EntityDisplayState<EntityDisplayWithoutContext>>.awaitInitialLoad(): EntityDisplayState.Loaded<EntityDisplayWithoutContext> {
        assertEquals(EntityDisplayState.Loading, awaitItem())
        return awaitLoaded()
    }

    /** Asserts the next emission is a snapshot, returning it. */
    @Suppress("UNCHECKED_CAST")
    private suspend fun ReceiveTurbine<EntityDisplayState<EntityDisplayWithoutContext>>.awaitLoaded(): EntityDisplayState.Loaded<EntityDisplayWithoutContext> {
        val state = awaitItem()
        assertInstanceOf(EntityDisplayState.Loaded::class.java, state)
        return state as EntityDisplayState.Loaded<EntityDisplayWithoutContext>
    }

    @Test
    fun `Given a registry name when snapshotting then it is used over the friendly name`() = runTest {
        givenRegistryName("Bed")

        val items = manager.snapshot(serverId, listOf("light.bed")).awaitLoadedOrNull()?.entities.orEmpty()

        assertEquals(listOf("Bed"), items.map { it.name })
    }

    @Test
    fun `Given no display registry when snapshotting then the friendly name is used`() = runTest {
        coEvery { webSocketRepository.getEntityRegistryDisplay() } returns null

        val items = manager.snapshot(serverId, listOf("light.bed")).awaitLoadedOrNull()?.entities.orEmpty()

        assertEquals(listOf("Bed Light"), items.map { it.name })
    }

    @Test
    fun `Given an entity without registry entry when snapshotting then the friendly name is used`() = runTest {
        givenRegistryName("Bed")
        coEvery { integrationRepository.getEntities() } returns listOf(entity("switch.fan", "Fan"))

        val items = manager.snapshot(serverId, listOf("switch.fan")).awaitLoadedOrNull()?.entities.orEmpty()

        assertEquals(listOf("Fan"), items.map { it.name })
    }

    @Test
    fun `Given several ids when snapshotting then the registry and the entities are each fetched once`() = runTest {
        givenRegistryName("Bed")
        coEvery { integrationRepository.getEntities() } returns listOf(entity("light.bed"), entity("switch.fan", "Fan"))

        val items = manager.snapshot(serverId, listOf("light.bed", "switch.fan")).awaitLoadedOrNull()?.entities.orEmpty()

        assertEquals(listOf("Bed", "Fan"), items.map { it.name })
        coVerify(exactly = 1) { webSocketRepository.getEntityRegistryDisplay() }
        coVerify(exactly = 1) { integrationRepository.getEntities() }
    }

    @Test
    fun `Given no registered server when snapshotting then the flow completes with Error`() = runTest {
        coEvery { serverManager.isRegistered() } returns false

        manager.snapshot(serverId, listOf("light.bed")).test {
            assertEquals(EntityDisplayState.Loading, awaitItem())
            assertEquals(EntityDisplayState.Error, awaitItem())
            awaitComplete()
        }

        coVerify(exactly = 0) { serverManager.integrationRepository(any()) }
    }

    @Test
    fun `Given no entity that can be retrieved when snapshotting then the flow completes with Error`() = runTest {
        coEvery { integrationRepository.getEntities() } returns emptyList()

        manager.snapshot(serverId, listOf("light.bed")).test {
            assertEquals(EntityDisplayState.Loading, awaitItem())
            assertEquals(EntityDisplayState.Error, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `Given no registered server when observing then the flow completes with Error without asking the server`() = runTest {
        coEvery { serverManager.isRegistered() } returns false

        manager.observe(serverId, entityIds).test {
            assertEquals(EntityDisplayState.Loading, awaitItem())
            assertEquals(EntityDisplayState.Error, awaitItem())
            awaitComplete()
        }

        coVerify(exactly = 0) { serverManager.integrationRepository(any()) }
    }

    @Test
    fun `Given a server without integration repository when observing then the flow completes with Error`() = runTest {
        coEvery { serverManager.integrationRepository(serverId) } throws IllegalStateException(
            "Impossible to determine the serverID from -1",
        )

        manager.observe(serverId, entityIds).test {
            assertEquals(EntityDisplayState.Loading, awaitItem())
            assertEquals(EntityDisplayState.Error, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `Given a registry name when observing then it is emitted over the friendly name`() = runTest {
        givenRegistryName("Bed")

        manager.observe(serverId, entityIds).test {
            assertEquals("Bed", awaitInitialLoad().entities.single().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given no display registry when observing then the friendly name is emitted`() = runTest {
        coEvery { webSocketRepository.getEntityRegistryDisplay() } returns null

        manager.observe(serverId, entityIds).test {
            assertEquals("Bed Light", awaitInitialLoad().entity("light.bed")?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given an entity state change when observing then a snapshot is emitted again`() = runTest {
        givenRegistryName("Bed")

        manager.observe(serverId, entityIds).test {
            awaitInitialLoad()
            entityUpdates.awaitSubscribed()

            // The name is unchanged, the snapshot is emitted again so consumers refresh
            entityUpdates.emit(entity("light.bed", "Ignored friendly name").copy(state = "off"))

            assertEquals("Bed", awaitLoaded().entities.single().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given a registry rename when observing then the tracked entity is emitted again`() = runTest {
        givenRegistryName("Bed")

        manager.observe(serverId, entityIds).test {
            assertEquals("Bed", awaitInitialLoad().entities.single().name)
            entityRegistryUpdates.awaitSubscribed()
            givenRegistryName("Renamed bed")

            entityRegistryUpdates.emit(EntityRegistryUpdatedEvent(action = "update", entityId = "light.bed"))

            assertEquals("Renamed bed", awaitLoaded().entities.single().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given a registry update that renames nothing when observing then the same snapshot is emitted`() = runTest {
        givenRegistryName("Bed")

        manager.observe(serverId, entityIds).test {
            val initial = awaitInitialLoad()
            entityRegistryUpdates.awaitSubscribed()

            entityRegistryUpdates.emit(EntityRegistryUpdatedEvent(action = "update", entityId = "light.other"))

            // Emitted again unchanged: deduplicating is left to the consumer
            assertEquals(initial, awaitLoaded())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given entity ids when observing then the devices areas and floors are not fetched`() = runTest {
        givenRegistryName("Bed")

        manager.observe(serverId, entityIds).test {
            awaitInitialLoad()
            entityUpdates.awaitSubscribed()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) {
            integrationRepository.getEntityUpdates()
            webSocketRepository.getDeviceRegistry()
            webSocketRepository.getAreaRegistry()
            webSocketRepository.getFloorRegistry()
            webSocketRepository.getAreaRegistryUpdates()
            webSocketRepository.getDeviceRegistryUpdates()
        }
    }

    @Test
    fun `Given no entity ids when observing then every entity of the server is observed`() = runTest {
        givenRegistryName("Bed")
        coEvery { integrationRepository.getEntities() } returns listOf(entity("light.bed", "Bed Light"))
        coEvery { integrationRepository.getEntityUpdates() } returns entityUpdates

        manager.observe(serverId).test {
            assertEquals(listOf("light.bed"), awaitInitialLoad().entities.map { it.entityId })
            entityUpdates.awaitSubscribed()

            // A new entity of the server is picked up, not only the initially fetched ones
            entityUpdates.emit(entity("switch.fan", "Fan"))

            assertEquals(listOf("light.bed", "switch.fan"), awaitLoaded().entities.map { it.entityId })
            cancelAndIgnoreRemainingEvents()
        }

        // Subscribed to every entity, not to a filtered id list
        coVerify(exactly = 1) { integrationRepository.getEntityUpdates() }
        coVerify(exactly = 0) { integrationRepository.getEntityUpdates(any<List<String>>()) }
    }

    @Test
    fun `Given no entity that can be retrieved when observing then the flow completes with Error`() = runTest {
        givenRegistryName("Bed")
        coEvery { integrationRepository.getEntities() } returns null

        manager.observe(serverId, entityIds).test {
            assertEquals(EntityDisplayState.Loading, awaitItem())
            assertEquals(EntityDisplayState.Error, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `Given a server that does not know the entity when observing then the flow completes with Error`() = runTest {
        givenRegistryName("Bed")
        coEvery { integrationRepository.getEntities() } returns listOf(entity("light.other"))

        manager.observe(serverId, entityIds).test {
            assertEquals(EntityDisplayState.Loading, awaitItem())
            assertEquals(EntityDisplayState.Error, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `Given several entity ids when observing then each snapshot holds them all`() = runTest {
        val ids = listOf("light.bed", "switch.fan")
        coEvery { webSocketRepository.getEntityRegistryDisplay() } returns EntityRegistryDisplayResponse(
            entities = listOf(
                EntityRegistryDisplayEntry(entityId = "light.bed", name = "Bed"),
                EntityRegistryDisplayEntry(entityId = "switch.fan", name = "Fan"),
            ),
        )
        coEvery { integrationRepository.getEntities() } returns listOf(entity("light.bed", "Bed Light"), entity("switch.fan"))
        coEvery { integrationRepository.getEntityUpdates(ids) } returns entityUpdates

        manager.observe(serverId, ids).test {
            assertEquals(listOf("Bed", "Fan"), awaitInitialLoad().entities.map { it.name })
            entityUpdates.awaitSubscribed()

            entityUpdates.emit(entity("switch.fan").copy(state = "off"))

            // The snapshot still holds both entities
            val updated = awaitLoaded()
            assertEquals(listOf("Bed", "Fan"), updated.entities.map { it.name })
            assertEquals(listOf("light.bed", "switch.fan"), updated.entities.map { it.entityId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Nested
    inner class InContext {

        /** The context variants also resolve the registries the entity registry doesn't hold. */
        @BeforeEach
        fun setUpContextRegistries() {
            coEvery { webSocketRepository.getDeviceRegistry() } returns emptyList()
            coEvery { webSocketRepository.getAreaRegistry() } returns emptyList()
            coEvery { webSocketRepository.getFloorRegistry() } returns emptyList()
            coEvery { webSocketRepository.getEntityRegistry() } returns emptyList()
            coEvery { webSocketRepository.getEntityRegistryDisplay() } returns EntityRegistryDisplayResponse()
        }

        /** Resolves [entities] through the server, as the only snapshot variant does. */
        private suspend fun resolve(vararg entities: Entity): List<EntityDisplayWithContext> {
            coEvery { integrationRepository.getEntities() } returns entities.toList()

            return manager.snapshotInContext(serverId = serverId).awaitLoaded()
        }

        /** Asserts the next emission is a Loaded state, returning its items. */
        private suspend fun ReceiveTurbine<EntityDisplayState<EntityDisplayWithContext>>.awaitLoaded(): Collection<EntityDisplayWithContext> {
            val state = awaitItem()
            assertInstanceOf(EntityDisplayState.Loaded::class.java, state)
            return (state as EntityDisplayState.Loaded<EntityDisplayWithContext>).entities
        }

        /** Asserts the flow emits Loading then a terminal Loaded state, returning the items. */
        private suspend fun Flow<EntityDisplayState<EntityDisplayWithContext>>.awaitLoaded(): List<EntityDisplayWithContext> {
            var items: List<EntityDisplayWithContext> = emptyList()
            test {
                assertEquals(EntityDisplayState.Loading, awaitItem())
                items = awaitLoaded().toList()
                awaitComplete()
            }
            return items
        }

        @Test
        fun `Given a state dependent icon when resolving then the stateless icon ignores the state`() = runTest {
            val off = entity("automation.wake_up").copy(state = "off")

            val item = resolve(off).single()

            // The rendered icon follows the state, the persisted one stays the same as it changes
            assertEquals(CommunityMaterial.Icon3.cmd_robot_off, item.icon)
            assertEquals(CommunityMaterial.Icon3.cmd_robot, item.statelessIcon)
        }

        @Test
        fun `Given a custom icon when resolving then both icons use it`() = runTest {
            coEvery { webSocketRepository.getEntityRegistryDisplay() } returns EntityRegistryDisplayResponse(
                entities = listOf(EntityRegistryDisplayEntry(entityId = "automation.wake_up", icon = "mdi:heart")),
            )

            val item = resolve(entity("automation.wake_up"))
                .single()

            assertEquals(CommunityMaterial.Icon2.cmd_heart, item.icon)
            assertEquals(CommunityMaterial.Icon2.cmd_heart, item.statelessIcon)
        }

        @Test
        fun `Given no registered server when invoking then the flow completes with Error without asking the server`() = runTest {
            coEvery { serverManager.isRegistered() } returns false

            manager.snapshotInContext(serverId = serverId).test {
                assertEquals(EntityDisplayState.Loading, awaitItem())
                assertEquals(EntityDisplayState.Error, awaitItem())
                awaitComplete()
            }

            coVerify(exactly = 0) { serverManager.integrationRepository(any()) }
        }

        @Test
        fun `Given a server without integration repository when invoking then the flow completes with Error`() = runTest {
            coEvery { serverManager.integrationRepository(serverId) } throws IllegalStateException(
                "Impossible to determine the serverID from -1",
            )

            manager.snapshotInContext(serverId = serverId).test {
                assertEquals(EntityDisplayState.Loading, awaitItem())
                assertEquals(EntityDisplayState.Error, awaitItem())
                awaitComplete()
            }
        }

        @Test
        fun `Given a server without integration repository when observing then the flow completes with Error`() = runTest {
            coEvery { serverManager.integrationRepository(serverId) } throws IllegalStateException(
                "Impossible to determine the serverID from -1",
            )

            manager.observeInContext(serverId).test {
                assertEquals(EntityDisplayState.Loading, awaitItem())
                assertEquals(EntityDisplayState.Error, awaitItem())
                awaitComplete()
            }
        }

        @Test
        fun `Given a display registry when invoking then it is used and the classic registry is not fetched`() = runTest {
            coEvery { webSocketRepository.getEntityRegistryDisplay() } returns EntityRegistryDisplayResponse(
                entities = listOf(EntityRegistryDisplayEntry(entityId = "light.bed", name = "Bed")),
            )

            val items = resolve(entity("light.bed", "Bed Light"))

            assertEquals("Bed", items.single().name)
            coVerify(exactly = 0) { webSocketRepository.getEntityRegistry() }
        }

        @Test
        fun `Given no display registry when invoking then the classic registry is used`() = runTest {
            coEvery { webSocketRepository.getEntityRegistryDisplay() } returns null
            coEvery { webSocketRepository.getEntityRegistry() } returns listOf(
                EntityRegistryResponse(entityId = "light.bed", areaId = "bedroom"),
            )
            coEvery { webSocketRepository.getAreaRegistry() } returns listOf(
                AreaRegistryResponse(areaId = "bedroom", name = "Bedroom"),
            )

            val items = resolve(entity("light.bed", "Bed Light"))

            assertEquals("Bed Light", items.single().name)
            assertEquals("Bedroom", items.single().areaName)
        }

        @Test
        fun `Given a floor registry when invoking then it is fetched`() = runTest {
            coEvery { webSocketRepository.getFloorRegistry() } returns listOf(
                FloorRegistryResponse(floorId = "first", name = "First floor"),
            )

            resolve(entity("light.bed", "Bed Light"))

            coVerify(exactly = 1) { webSocketRepository.getFloorRegistry() }
        }

        @Test
        fun `Given display registry failure when invoking then falls back to classic registry`() = runTest {
            coEvery { webSocketRepository.getEntityRegistryDisplay() } returns null
            coEvery { webSocketRepository.getEntityRegistry() } returns listOf(
                EntityRegistryResponse(entityId = "light.bed", hiddenBy = "user"),
            )

            val items = resolve(entity("light.bed", "Bed Light"))

            assertEquals(true, items.single().isHidden)
            coVerify(exactly = 1) { webSocketRepository.getEntityRegistry() }
        }

        @Test
        fun `Given all registries failing when invoking then metadata degrades without an error state`() = runTest {
            coEvery { webSocketRepository.getEntityRegistryDisplay() } throws IllegalStateException("boom")
            coEvery { webSocketRepository.getEntityRegistry() } throws IllegalStateException("boom")
            coEvery { webSocketRepository.getDeviceRegistry() } throws IllegalStateException("boom")
            coEvery { webSocketRepository.getAreaRegistry() } throws IllegalStateException("boom")
            coEvery { webSocketRepository.getFloorRegistry() } throws IllegalStateException("boom")

            val items = resolve(entity("light.bed", "Bed Light"))

            assertEquals("Bed Light", items.single().name)
            assertNull(items.single().areaName)
        }

        @Test
        fun `Given no entity list when invoking then entities are fetched from the server`() = runTest {
            coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
            coEvery { integrationRepository.getEntities() } returns listOf(entity("light.bed", "Bed Light"))
            coEvery { webSocketRepository.getEntityRegistryDisplay() } returns EntityRegistryDisplayResponse(
                entities = listOf(EntityRegistryDisplayEntry(entityId = "light.bed", name = "Bed")),
            )

            val items = manager.snapshotInContext(serverId = serverId).awaitLoaded()

            assertEquals("Bed", items.single().name)
            coVerify(exactly = 1) { integrationRepository.getEntities() }
        }

        @Test
        fun `Given a filter when invoking without list then only matching entities are resolved`() = runTest {
            coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
            coEvery { integrationRepository.getEntities() } returns listOf(
                entity("light.bed", "Bed Light"),
                entity("switch.fan", "Fan"),
            )

            val items = manager.snapshotInContext(serverId = serverId) { it.domain == "light" }.awaitLoaded()

            assertEquals(listOf("light.bed"), items.map { it.entityId })
        }

        @Test
        fun `Given entities fetch failure when invoking without list then flow completes with error`() = runTest {
            coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
            coEvery { integrationRepository.getEntities() } throws IllegalStateException("boom")

            manager.snapshotInContext(serverId = serverId).test {
                assertEquals(EntityDisplayState.Loading, awaitItem())
                assertEquals(EntityDisplayState.Error, awaitItem())
                awaitComplete()
            }
            coVerify(exactly = 0) { webSocketRepository.getEntityRegistryDisplay() }
        }

        @Test
        fun `Given null entities response when invoking without list then flow completes with error`() = runTest {
            coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
            coEvery { integrationRepository.getEntities() } returns null

            manager.snapshotInContext(serverId = serverId).test {
                assertEquals(EntityDisplayState.Loading, awaitItem())
                assertEquals(EntityDisplayState.Error, awaitItem())
                awaitComplete()
            }
        }

        @Test
        fun `Given no entities when invoking then loaded empty is emitted without registry fetch`() = runTest {
            val items = resolve()

            assertEquals(emptyList<EntityDisplayWithoutContext>(), items)
            coVerify(exactly = 0) { webSocketRepository.getEntityRegistryDisplay() }
            coVerify(exactly = 0) { webSocketRepository.getDeviceRegistry() }
        }

        @Nested
        inner class Resolution {

            private fun givenDisplayEntries(
                vararg entries: EntityRegistryDisplayEntry,
                categories: Map<Int, String> = emptyMap(),
            ) {
                coEvery { webSocketRepository.getEntityRegistryDisplay() } returns EntityRegistryDisplayResponse(
                    entityCategories = categories,
                    entities = entries.toList(),
                )
            }

            @Test
            fun `Given display entry without name when resolving then name falls back to friendly name`() = runTest {
                givenDisplayEntries(EntityRegistryDisplayEntry(entityId = "light.bed"))

                val items = resolve(entity("light.bed", "Bed Light"))

                assertEquals("Bed Light", items.single().name)
            }

            @Test
            fun `Given display entry with a blank name when resolving then name falls back to friendly name`() = runTest {
                givenDisplayEntries(EntityRegistryDisplayEntry(entityId = "light.bed", name = " "))

                val items = resolve(entity("light.bed", "Bed Light"))

                assertEquals("Bed Light", items.single().name)
            }

            @Test
            fun `Given no friendly name when resolving then name falls back to entity id`() = runTest {
                val items = resolve(entity("light.bed"))

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

                val items = resolve(entity("light.bed"))

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

                val items = resolve(entity("light.bed"))

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

                val items = resolve(entity("light.bed"))

                assertEquals("First floor", items.single().floorName)
            }

            @Test
            fun `Given device with user name when resolving then user name wins over device name`() = runTest {
                givenDisplayEntries(EntityRegistryDisplayEntry(entityId = "light.bed", deviceId = "device1"))
                coEvery { webSocketRepository.getDeviceRegistry() } returns listOf(
                    DeviceRegistryResponse(id = "device1", name = "Hub", nameByUser = "My Hub"),
                )

                val items = resolve(entity("light.bed"))

                assertEquals("My Hub", items.single().deviceName)
            }

            @Test
            fun `Given classic entry when resolving then hidden category and precision are mapped`() = runTest {
                coEvery { webSocketRepository.getEntityRegistryDisplay() } returns null
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

                val item = resolve(entity("sensor.temp", "Temp"))
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

                val item = resolve(entity("sensor.temp"))
                    .single()

                assertEquals(true, item.isHidden)
                assertEquals(EntityCategory.CONFIG, item.entityCategory)
                assertEquals(1, item.displayPrecision)
                assertEquals(listOf("label1"), item.labels)
            }

            @Test
            fun `Given no custom icon when resolving then icon derives from the entity`() = runTest {
                val lightEntity = entity("light.bed")

                val items = resolve(lightEntity)

                assertEquals(lightEntity.getIcon(), items.single().icon)
            }

            @Test
            fun `Given entities when resolving then order and count are preserved`() = runTest {
                val items = resolve(entity("light.bed"), entity("switch.fan"), entity("sensor.temp"))

                assertEquals(listOf("light.bed", "switch.fan", "sensor.temp"), items.map { it.entityId })
            }

            @Test
            fun `Given an entity alone when building an item from it then entity fields are resolved`() {
                val lightEntity = entity("light.bed", "Bed Light")

                val item = EntityDisplayWithoutContext(lightEntity)

                assertEquals("Bed Light", item.name)
                assertEquals(lightEntity.getIcon(), item.icon)
                assertEquals("on", item.rawState)
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
                val item = EntityDisplayWithContext(
                    item = EntityDisplayWithoutContext(
                        entityId = entityId,
                        name = "Name",
                        icon = CommunityMaterial.Icon.cmd_bookmark,
                    ),
                )
                assertEquals(expectedDomain, item.domain)
            }

            @Test
            fun `Given area and device names when reading the subtitle then they are joined for the layout direction`() {
                val item = EntityDisplayWithContext(
                    item = EntityDisplayWithoutContext(
                        entityId = "light.bed",
                        name = "Bed",
                        icon = CommunityMaterial.Icon.cmd_bookmark,
                    ),
                    areaName = "Bedroom",
                    deviceName = "Hub",
                )

                assertEquals("Bedroom ▸ Hub", item.subtitle(LayoutDirection.Ltr))
                assertEquals("Bedroom ◂ Hub", item.subtitle(LayoutDirection.Rtl))
            }

            @Test
            fun `Given no area and device name when reading the subtitle then it is null`() {
                val item = EntityDisplayWithContext(
                    item = EntityDisplayWithoutContext(
                        entityId = "light.bed",
                        name = "Bed",
                        icon = CommunityMaterial.Icon.cmd_bookmark,
                    ),
                )

                assertNull(item.subtitle(LayoutDirection.Ltr))
            }

            @Test
            fun `Given a subtitle equal to the name when reading the subtitle then it is null`() {
                val item = EntityDisplayWithContext(
                    item = EntityDisplayWithoutContext(
                        entityId = "light.bed",
                        name = "Bed",
                        icon = CommunityMaterial.Icon.cmd_bookmark,
                    ),
                    deviceName = "Bed",
                )

                assertNull(item.subtitle(LayoutDirection.Ltr))
            }
        }

        @Nested
        inner class Observe {

            private val entityUpdates = MutableSharedFlow<Entity>()
            private val entityRegistryUpdates = MutableSharedFlow<EntityRegistryUpdatedEvent>()

            @BeforeEach
            fun setUpObserve() {
                coEvery { serverManager.integrationRepository(serverId) } returns integrationRepository
                coEvery { integrationRepository.getEntities() } returns listOf(entity("light.bed", "Bed Light"))
                coEvery { integrationRepository.getEntityUpdates() } returns entityUpdates
                coEvery { webSocketRepository.getAreaRegistryUpdates() } returns null
                coEvery { webSocketRepository.getDeviceRegistryUpdates() } returns null
                coEvery { webSocketRepository.getEntityRegistryUpdates() } returns entityRegistryUpdates
            }

            /** Asserts the flow emits Loading then the initial Loaded state, returning the items. */
            private suspend fun ReceiveTurbine<EntityDisplayState<EntityDisplayWithContext>>.awaitInitialLoad(): List<EntityDisplayWithContext> {
                assertEquals(EntityDisplayState.Loading, awaitItem())
                return awaitLoaded().toList()
            }

            @Test
            fun `Given an entity state change when observing then a new loaded state is emitted`() = runTest {
                manager.observeInContext(serverId).test {
                    assertEquals("Bed Light", awaitInitialLoad().single().name)
                    entityUpdates.awaitSubscribed()

                    entityUpdates.emit(entity("light.bed", "Renamed Light"))

                    val updated = EntityDisplayState.Loaded(awaitLoaded().toList())
                    assertEquals("Renamed Light", updated.entities.single().name)
                    cancelAndIgnoreRemainingEvents()
                }
            }

            @Test
            fun `Given an update that does not change the items when observing then nothing is emitted`() = runTest {
                manager.observeInContext(serverId).test {
                    awaitInitialLoad()
                    entityUpdates.awaitSubscribed()

                    entityUpdates.emit(entity("light.bed", "Bed Light"))
                    entityUpdates.emit(entity("light.bed", "Renamed Light"))

                    // Only the renaming update emits: the unchanged one before it was skipped
                    val updated = EntityDisplayState.Loaded(awaitLoaded().toList())
                    assertEquals("Renamed Light", updated.entities.single().name)
                    cancelAndIgnoreRemainingEvents()
                }
            }

            @Test
            fun `Given a new entity when observing then it is appended to the items`() = runTest {
                manager.observeInContext(serverId).test {
                    awaitInitialLoad()
                    entityUpdates.awaitSubscribed()

                    entityUpdates.emit(entity("switch.fan", "Fan"))

                    val updated = EntityDisplayState.Loaded(awaitLoaded().toList())
                    assertEquals(listOf("light.bed", "switch.fan"), updated.entities.map { it.entityId })
                    cancelAndIgnoreRemainingEvents()
                }
            }

            @Test
            fun `Given a filter when observing then non matching updates are ignored`() = runTest {
                manager.observeInContext(serverId) { it.domain == "light" }.test {
                    awaitInitialLoad()
                    entityUpdates.awaitSubscribed()

                    entityUpdates.emit(entity("switch.fan", "Fan"))
                    entityUpdates.emit(entity("light.bed", "Renamed Light"))

                    // Only the light update emits: the switch was filtered out
                    val updated = EntityDisplayState.Loaded(awaitLoaded().toList())
                    assertEquals(listOf("Renamed Light"), updated.entities.map { it.name })
                    cancelAndIgnoreRemainingEvents()
                }
            }

            @Test
            fun `Given an entity registry update when observing then only the entity registry is refetched`() = runTest {
                coEvery { webSocketRepository.getEntityRegistryDisplay() } returns
                    EntityRegistryDisplayResponse(
                        entities = listOf(EntityRegistryDisplayEntry(entityId = "light.bed", name = "Bed")),
                    ) andThen
                    EntityRegistryDisplayResponse(
                        entities = listOf(EntityRegistryDisplayEntry(entityId = "light.bed", name = "Bed renamed")),
                    )

                manager.observeInContext(serverId).test {
                    assertEquals("Bed", awaitInitialLoad().single().name)
                    entityRegistryUpdates.awaitSubscribed()

                    entityRegistryUpdates.emit(EntityRegistryUpdatedEvent("update", "light.bed"))

                    val updated = EntityDisplayState.Loaded(awaitLoaded().toList())
                    assertEquals("Bed renamed", updated.entities.single().name)
                    cancelAndIgnoreRemainingEvents()
                }
                coVerify(exactly = 2) { webSocketRepository.getEntityRegistryDisplay() }
                coVerify(exactly = 1) { webSocketRepository.getAreaRegistry() }
                coVerify(exactly = 1) { webSocketRepository.getDeviceRegistry() }
            }

            @Test
            fun `Given an area registry update when observing then only the areas and floors are refetched`() = runTest {
                val areaUpdates = MutableSharedFlow<AreaRegistryUpdatedEvent>()
                coEvery { webSocketRepository.getAreaRegistryUpdates() } returns areaUpdates
                coEvery { webSocketRepository.getEntityRegistryDisplay() } returns EntityRegistryDisplayResponse(
                    entities = listOf(EntityRegistryDisplayEntry(entityId = "light.bed", areaId = "bedroom")),
                )
                coEvery { webSocketRepository.getAreaRegistry() } returns
                    listOf(AreaRegistryResponse(areaId = "bedroom", name = "Bedroom")) andThen
                    listOf(AreaRegistryResponse(areaId = "bedroom", name = "Bedroom renamed"))

                manager.observeInContext(serverId).test {
                    assertEquals("Bedroom", awaitInitialLoad().single().areaName)
                    areaUpdates.awaitSubscribed()

                    areaUpdates.emit(AreaRegistryUpdatedEvent("update", "bedroom"))

                    val updated = EntityDisplayState.Loaded(awaitLoaded().toList())
                    assertEquals("Bedroom renamed", updated.entities.single().areaName)
                    cancelAndIgnoreRemainingEvents()
                }
                coVerify(exactly = 2) { webSocketRepository.getAreaRegistry() }
                coVerify(exactly = 2) { webSocketRepository.getFloorRegistry() }
                coVerify(exactly = 1) { webSocketRepository.getEntityRegistryDisplay() }
                coVerify(exactly = 1) { webSocketRepository.getDeviceRegistry() }
            }

            @Test
            fun `Given entities fetch failure when observing then flow completes with error`() = runTest {
                coEvery { integrationRepository.getEntities() } throws IllegalStateException("boom")

                manager.observeInContext(serverId).test {
                    assertEquals(EntityDisplayState.Loading, awaitItem())
                    assertEquals(EntityDisplayState.Error, awaitItem())
                    awaitComplete()
                }
            }

            @Test
            fun `Given no update subscriptions when observing then flow completes after the loaded state`() = runTest {
                coEvery { integrationRepository.getEntityUpdates() } returns null
                coEvery { webSocketRepository.getEntityRegistryUpdates() } returns null

                manager.observeInContext(serverId).test {
                    awaitInitialLoad()
                    awaitComplete()
                }
            }
        }
    }
}
