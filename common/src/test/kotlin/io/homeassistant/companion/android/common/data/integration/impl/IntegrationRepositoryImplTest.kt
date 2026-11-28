package io.homeassistant.companion.android.common.data.integration.impl

import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityChange
import io.homeassistant.companion.android.common.data.integration.EntityFilter
import io.homeassistant.companion.android.common.data.integration.IntegrationException
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.impl.IntegrationRepositoryImpl.Companion.PREF_ASK_NOTIFICATION_PERMISSION
import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedEntityState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateDiff
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.TriggerEvent
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import java.io.IOException
import java.time.LocalDateTime
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import retrofit2.Response

class IntegrationRepositoryImplTest {

    private val integrationService = mockk<IntegrationService>()
    private val serverManager = mockk<ServerManager>()
    private val serverID = 42
    private val server = mockk<Server>(relaxed = true)
    private val serverConnection = mockk<ServerConnectionInfo>()
    private val connectionStateProvider = mockk<ServerConnectionStateProvider>()
    private val localStorage = mockk<LocalStorage>()

    private lateinit var repository: IntegrationRepository

    @BeforeEach
    fun setUp() {
        coEvery { serverManager.getServer(serverID) } returns server
        every { server.connection } returns serverConnection
        every { server.deviceName } returns "Device name"
        coEvery { serverManager.connectionStateProvider(serverID) } returns connectionStateProvider

        val url = "http://homeassistant:8123".toHttpUrl()
        coEvery { connectionStateProvider.getApiUrls() } returns listOf(url)
        coEvery { connectionStateProvider.urlFlow(any()) } returns flowOf(UrlState.HasUrl(url.toUrl()))

        repository = IntegrationRepositoryImpl(integrationService, serverManager, serverID, localStorage, "", "", "", "")
    }

    @Test
    fun `Given an empty response when invoking renderTemplate then it returns an empty string`() = runTest {
        val expectedResult = ""
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult, result)
    }

    @Test
    fun `Given a valid response with a number when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = 42
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult.toString(), result)
    }

    @Test
    fun `Given a valid response with a string when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = "hello world"
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult, result)
    }

    @Test
    fun `Given a valid response with a boolean when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = true
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonPrimitive(expectedResult)))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals(expectedResult.toString(), result)
    }

    @Test
    fun `Given a valid response with a list when invoking renderTemplate then it returns a valid string`() = runTest {
        val expectedResult = listOf(true, false)
        coEvery { integrationService.getTemplate(any(), any()) } returns JsonObject(mapOf("template" to JsonArray(expectedResult.map { JsonPrimitive(it) })))

        val result = repository.renderTemplate("whatever", emptyMap())

        assertEquals("[true,false]", result)
    }

    @Test
    fun `Given no preference set when checking shouldAskNotificationPermission then returns null`() = runTest {
        coEvery { localStorage.getBooleanOrNull("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION") } returns null

        val result = repository.shouldAskNotificationPermission()

        assertNull(result)
    }

    @Test
    fun `Given preference set to true when checking shouldAskNotificationPermission then returns true`() = runTest {
        coEvery { localStorage.getBooleanOrNull("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION") } returns true

        val result = repository.shouldAskNotificationPermission()

        assertTrue(result == true)
    }

    @Test
    fun `Given preference set to false when checking shouldAskNotificationPermission then returns false`() = runTest {
        coEvery { localStorage.getBooleanOrNull("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION") } returns false

        val result = repository.shouldAskNotificationPermission()

        assertEquals(false, result)
    }

    @Test
    fun `Given setAskNotificationPermission called with true then stores true for server`() = runTest {
        coEvery { localStorage.putBoolean(any(), any()) } returns Unit

        repository.setAskNotificationPermission(true)

        coVerify { localStorage.putBoolean("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION", true) }
    }

    @Test
    fun `Given setAskNotificationPermission called with false then stores false for server`() = runTest {
        coEvery { localStorage.putBoolean(any(), any()) } returns Unit

        repository.setAskNotificationPermission(false)

        coVerify { localStorage.putBoolean("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION", false) }
    }

    @Test
    fun `Given different server IDs then notification permission is stored separately per server`() = runTest {
        val otherServerId = 99
        coEvery { serverManager.getServer(otherServerId) } returns server
        val otherRepository = IntegrationRepositoryImpl(
            integrationService,
            serverManager,
            otherServerId,
            localStorage,
            "",
            "",
            "",
            "",
        )

        coEvery { localStorage.putBoolean(any(), any()) } returns Unit

        repository.setAskNotificationPermission(true)
        otherRepository.setAskNotificationPermission(false)

        coVerify { localStorage.putBoolean("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION", true) }
        coVerify { localStorage.putBoolean("${otherServerId}_$PREF_ASK_NOTIFICATION_PERMISSION", false) }
    }

    @Nested
    inner class UpdateRegistrationTests {

        @Test
        fun `Given success when updating registration then registration is persisted`() = runTest {
            val body = "content".toResponseBody()
            coEvery { integrationService.callWebhook(any(), any()) } returns Response.success(body)

            coEvery { localStorage.getString(any()) } returns null
            coEvery { serverManager.updateServer(any()) } returns Unit

            val registration = DeviceRegistration(deviceName = "New device name")
            repository.updateRegistration(
                registration,
                allowReregistration = true,
            )

            coVerify { serverManager.updateServer(any()) }
            coVerify(exactly = 0) { integrationService.registerDevice(any(), any(), any()) }
        }

        @Test
        fun `Given success code but empty body when reregistration is allowed then new registration is tried`() = runTest {
            val body = "".toResponseBody()
            coEvery { integrationService.callWebhook(any(), any()) } returns Response.success(body)

            coEvery { localStorage.getString(any()) } returns null

            // spy to be able to mock registerDevice - we only care that it is called
            // but don't test registerDevice internals in this test
            val spyRepository = spyk(repository)
            coEvery { spyRepository.registerDevice(any()) } just Runs

            val registration = DeviceRegistration(deviceName = "New device name")
            spyRepository.updateRegistration(
                registration,
                allowReregistration = true,
            )

            coVerify { spyRepository.registerDevice(any()) }
        }

        @Test
        fun `Given known broken registration response when reregistration is not allowed then throws`() = runTest {
            val body = "".toResponseBody()
            coEvery { integrationService.callWebhook(any(), any()) } returns Response.success(body)

            coEvery { localStorage.getString(any()) } returns null
            coEvery { serverManager.updateServer(any()) } returns Unit

            // spy to be able to mock registerDevice - we only care that it is called
            // but don't test registerDevice internals in this test
            val spyRepository = spyk(repository)
            coEvery { spyRepository.registerDevice(any()) } just Runs

            val registration = DeviceRegistration(deviceName = "New device name")
            try {
                spyRepository.updateRegistration(
                    registration,
                    allowReregistration = false,
                )
                fail("Expected IntegrationException to be thrown")
            } catch (e: IntegrationException) {
                assertEquals("Device registration broken and reregistration not allowed.", e.message)
            }

            coVerify(exactly = 0) { serverManager.updateServer(any()) }
            coVerify(exactly = 0) { spyRepository.registerDevice(any()) }
        }

        @ParameterizedTest
        @ValueSource(ints = [404, 410])
        fun `Given known error code when reregistration is allowed then new registration is tried`(code: Int) = runTest {
            val body = "".toResponseBody()
            coEvery { integrationService.callWebhook(any(), any()) } returns Response.error(code, body)

            coEvery { localStorage.getString(any()) } returns null

            // spy to be able to mock registerDevice - we only care that it is called
            // but don't test registerDevice internals in this test
            val spyRepository = spyk(repository)
            coEvery { spyRepository.registerDevice(any()) } just Runs

            val registration = DeviceRegistration(deviceName = "New device name")
            spyRepository.updateRegistration(
                registration,
                allowReregistration = true,
            )

            coVerify { spyRepository.registerDevice(any()) }
        }
    }

    @Nested
    inner class UpdateRegistrationOnSeveralUrlsTests {
        private val cloudhookUrl = "https://hooks.nabu.casa/cloudhook".toHttpUrl()
        private val externalUrl = "http://homeassistant:8123/api/webhook/id".toHttpUrl()

        @BeforeEach
        fun setUpUrls() {
            coEvery { connectionStateProvider.getApiUrls() } returns listOf(cloudhookUrl, externalUrl)
            coEvery { localStorage.getString(any()) } returns null
            coEvery { serverManager.updateServer(any()) } returns Unit
            coEvery { integrationService.callWebhook(cloudhookUrl, any()) } returns
                Response.error(404, "".toResponseBody())
        }

        @Test
        fun `Given broken cloudhook and valid registration on another url when updating then registration is kept`() = runTest {
            coEvery { integrationService.callWebhook(externalUrl, any()) } returns
                Response.success("content".toResponseBody())
            val spyRepository = spyk(repository)

            spyRepository.updateRegistration(
                DeviceRegistration(deviceName = "New device name"),
                allowReregistration = true,
            )

            coVerify { serverManager.updateServer(any()) }
            coVerify(exactly = 0) { spyRepository.registerDevice(any()) }
        }

        @Test
        fun `Given broken cloudhook and missing config entry on another url when updating then new registration is tried`() = runTest {
            coEvery { integrationService.callWebhook(externalUrl, any()) } returns
                Response.error(410, "".toResponseBody())
            val spyRepository = spyk(repository)
            coEvery { spyRepository.registerDevice(any()) } just Runs

            spyRepository.updateRegistration(
                DeviceRegistration(deviceName = "New device name"),
                allowReregistration = true,
            )

            coVerify { spyRepository.registerDevice(any()) }
        }

        @Test
        fun `Given broken cloudhook and no other url answering when updating then new registration is tried`() = runTest {
            coEvery { integrationService.callWebhook(externalUrl, any()) } throws IOException("Unreachable")
            val spyRepository = spyk(repository)
            coEvery { spyRepository.registerDevice(any()) } just Runs

            spyRepository.updateRegistration(
                DeviceRegistration(deviceName = "New device name"),
                allowReregistration = true,
            )

            coVerify { spyRepository.registerDevice(any()) }
        }

        @ParameterizedTest
        @ValueSource(ints = [429, 500, 503])
        fun `Given broken cloudhook and another url failing with an HTTP error when updating then throws without trying a new registration`(
            code: Int,
        ) = runTest {
            coEvery { integrationService.callWebhook(externalUrl, any()) } returns
                Response.error(code, "".toResponseBody())
            val spyRepository = spyk(repository)

            try {
                spyRepository.updateRegistration(
                    DeviceRegistration(deviceName = "New device name"),
                    allowReregistration = true,
                )
                fail("Expected IntegrationException to be thrown")
            } catch (e: IntegrationException) {
                assertEquals("All URLs failed for update_registration", e.message)
            }

            coVerify(exactly = 0) { spyRepository.registerDevice(any()) }
            coVerify(exactly = 0) { serverManager.updateServer(any()) }
        }

        @Test
        fun `Given no url answering when updating then throws without trying a new registration`() = runTest {
            coEvery { integrationService.callWebhook(cloudhookUrl, any()) } throws IOException("Unreachable")
            coEvery { integrationService.callWebhook(externalUrl, any()) } throws IOException("Unreachable")
            val spyRepository = spyk(repository)

            try {
                spyRepository.updateRegistration(
                    DeviceRegistration(deviceName = "New device name"),
                    allowReregistration = true,
                )
                fail("Expected IntegrationException to be thrown")
            } catch (e: IntegrationException) {
                assertEquals("All URLs failed for update_registration", e.message)
            }

            coVerify(exactly = 0) { spyRepository.registerDevice(any()) }
        }
    }

    @Nested
    inner class EntityChanges {
        private val webSocketRepository = mockk<WebSocketRepository>()
        private val entityId = "light.bed"

        @BeforeEach
        fun setUpWebSocket() {
            coEvery { serverManager.webSocketRepository(serverID) } returns webSocketRepository
            every { server.version } returns HomeAssistantVersion(2022, 4, 0)
            // The current states are always fetched before collecting the subscription
            coEvery { webSocketRepository.getStates() } returns emptyList()
        }

        private fun compressedState(state: String, attributes: Map<String, Any?> = emptyMap()) = CompressedEntityState(
            state = JsonPrimitive(state),
            attributes = attributes,
            lastChanged = 1_700_000_000.0,
        )

        private fun addedEvent(state: String, attributes: Map<String, Any?> = emptyMap()) = CompressedStateChangedEvent(
            added = mapOf(entityId to compressedState(state, attributes)),
        )

        private fun changedEvent(state: String) = CompressedStateChangedEvent(
            changed = mapOf(entityId to CompressedStateDiff(plus = CompressedEntityState(state = JsonPrimitive(state)))),
        )

        /** The states of the changes, leaving the removals out. */
        private fun List<EntityChange>.states() = filterIsInstance<EntityChange.StateChanged>().map { it.entity }

        private fun givenCurrentStatesFetchReturns(state: String) {
            coEvery { webSocketRepository.getStates() } returns listOf(entity(entityId, state))
        }

        private fun entity(entityId: String, state: String = "on") = EntityResponse(
            entityId = entityId,
            state = state,
            attributes = emptyMap(),
            lastChanged = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
            lastUpdated = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
        )

        @Test
        fun `Given compressed state changes when collecting entity changes then added entities and resolved diffs are emitted`() = runTest {
            coEvery { webSocketRepository.getCompressedStateAndChanges() } returns flowOf(
                addedEvent("on", attributes = mapOf("brightness" to 100)),
                changedEvent("off"),
            )

            val updates = checkNotNull(repository.getEntityChanges()).toList().states()

            assertEquals(listOf("on", "off"), updates.map { it.state })
            // The diff keeps the attributes of the state it applies to
            assertEquals(mapOf<String, Any?>("brightness" to 100), updates[1].attributes)
            coVerify(exactly = 1) { webSocketRepository.getStates() }
        }

        @Test
        fun `Given a collector joining after the initial states when collecting entity changes then the seeded states resolve the diff`() = runTest {
            coEvery { webSocketRepository.getCompressedStateAndChanges() } returns flowOf(changedEvent("off"))
            givenCurrentStatesFetchReturns("off")

            val updates = checkNotNull(repository.getEntityChanges()).toList().states()

            assertEquals("off", updates.single().state)
        }

        @Test
        fun `Given a diff for an unknown entity and a failing states fetch when collecting entity changes then the diff is skipped`() = runTest {
            coEvery { webSocketRepository.getCompressedStateAndChanges() } returns flowOf(changedEvent("off"))
            coEvery { webSocketRepository.getStates() } returns null

            val updates = checkNotNull(repository.getEntityChanges()).toList().states()

            assertTrue(updates.isEmpty())
        }

        @Test
        fun `Given a collector joining after the initial states when collecting entity changes for ids then only their states are fetched to resolve the diff`() = runTest {
            val filter = EntityFilter(entityIds = setOf(entityId))
            coEvery { webSocketRepository.getCompressedStateAndChanges(filter) } returns flowOf(changedEvent("off"))
            coEvery { webSocketRepository.getStates(filter) } returns listOf(entity(entityId))

            val updates = checkNotNull(repository.getEntityChanges(filter)).toList().states()

            assertEquals(listOf(entityId to "off"), updates.map { it.entityId to it.state })
            coVerify(exactly = 0) { webSocketRepository.getStates() }
        }

        @Test
        fun `Given a pending seed fetch when collection times out then the subscription is not collected`() = runTest {
            val filter = EntityFilter(entityIds = setOf(entityId))
            coEvery { webSocketRepository.getStates(filter) } coAnswers { awaitCancellation() }
            every { webSocketRepository.getCompressedStateAndChanges(filter) } returns flow {
                error("Subscription collected before the seed fetch completed")
            }

            val changes = withTimeoutOrNull(1_000) { repository.getEntityChanges(filter).toList() }

            assertNull(changes)
            coVerify(exactly = 1) { webSocketRepository.getStates(filter) }
        }

        @Test
        fun `Given compressed state changes when collecting entity changes for ids then the subscription filters without admin rights`() = runTest {
            every { server.user.isAdmin } returns false
            val filter = EntityFilter(entityIds = setOf(entityId))
            coEvery { webSocketRepository.getCompressedStateAndChanges(filter) } returns flowOf(addedEvent("on"))
            coEvery { webSocketRepository.getStates(filter) } returns emptyList()

            val updates = checkNotNull(repository.getEntityChanges(filter)).toList().states()

            assertEquals("on", updates.single().state)
        }

        @Test
        fun `Given a removed entity when collecting entity changes then its removal follows its state and later diffs are dropped`() = runTest {
            coEvery { webSocketRepository.getCompressedStateAndChanges() } returns flowOf(
                addedEvent("on"),
                CompressedStateChangedEvent(removed = listOf(entityId)),
                changedEvent("off"),
            )

            val changes = checkNotNull(repository.getEntityChanges()).toList()

            assertEquals(listOf("on"), changes.states().map { it.state })
            assertEquals(EntityChange.Removed(entityId), changes.last())
            assertEquals(2, changes.size)
            coVerify(exactly = 1) { webSocketRepository.getStates() }
        }

        @Test
        fun `Given an older server when collecting entity changes then an event without new state is a removal`() = runTest {
            every { server.version } returns HomeAssistantVersion(2022, 3, 0)
            coEvery { webSocketRepository.getStateChanges() } returns flowOf(StateChangedEvent(entityId, newState = null))

            val changes = checkNotNull(repository.getEntityChanges()).toList()

            assertEquals(listOf(EntityChange.Removed(entityId)), changes)
        }

        @Test
        fun `Given an older server and an admin when collecting entity changes for ids then a state trigger without new state is a removal`() = runTest {
            every { server.version } returns HomeAssistantVersion(2022, 3, 0)
            every { server.user.isAdmin } returns true
            val entity = Entity(
                entityId = entityId,
                state = "on",
                attributes = emptyMap(),
                lastChanged = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                lastUpdated = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
            )
            coEvery { webSocketRepository.getStateChanges(listOf(entityId)) } returns flowOf(
                TriggerEvent(platform = "state", entityId = entityId, toState = entity),
                TriggerEvent(platform = "state", entityId = entityId, fromState = entity),
            )

            val changes = checkNotNull(repository.getEntityChanges(EntityFilter(entityIds = setOf(entityId)))).toList()

            assertEquals(listOf(EntityChange.StateChanged(entity), EntityChange.Removed(entityId)), changes)
        }

        @Test
        fun `Given an older server and a non admin when collecting entity changes for ids then only the removal of a subscribed entity is emitted`() = runTest {
            every { server.version } returns HomeAssistantVersion(2022, 3, 0)
            every { server.user.isAdmin } returns false
            coEvery { webSocketRepository.getStateChanges() } returns flowOf(
                StateChangedEvent("light.other", newState = null),
                StateChangedEvent(entityId, newState = null),
            )

            val changes = checkNotNull(repository.getEntityChanges(EntityFilter(entityIds = setOf(entityId)))).toList()

            assertEquals(listOf(EntityChange.Removed(entityId)), changes)
            // The state trigger requires admin rights
            coVerify(exactly = 0) { webSocketRepository.getStateChanges(any()) }
        }

        @Test
        fun `Given a server filtering domains when collecting entity changes for domains then the subscription and its seed are limited to them`() = runTest {
            every { server.version } returns HomeAssistantVersion(2024, 9, 0)
            val filter = EntityFilter(domains = setOf("light"))
            coEvery { webSocketRepository.getCompressedStateAndChanges(filter) } returns flowOf(changedEvent("off"))
            coEvery { webSocketRepository.getStates(filter) } returns listOf(entity(entityId))

            val updates = checkNotNull(repository.getEntityChanges(filter)).toList().states()

            assertEquals("off", updates.single().state)
            coVerify(exactly = 0) {
                webSocketRepository.getStates()
                webSocketRepository.getCompressedStateAndChanges()
            }
        }

        @Test
        fun `Given a server not filtering domains when collecting entity changes for domains then every change is followed and filtered on the device`() = runTest {
            val filter = EntityFilter(domains = setOf("light"))
            every { server.version } returns HomeAssistantVersion(2024, 8, 0)
            coEvery { webSocketRepository.getStates(filter) } returns emptyList()
            coEvery { webSocketRepository.getCompressedStateAndChanges() } returns flowOf(
                CompressedStateChangedEvent(
                    added = mapOf(entityId to compressedState("on"), "sensor.power" to compressedState("12")),
                    removed = listOf("sensor.gone"),
                ),
            )

            val changes = checkNotNull(repository.getEntityChanges(filter)).toList()

            assertEquals(listOf(entityId), changes.states().map { it.entityId })
            assertEquals(1, changes.size)
            coVerify(exactly = 0) { webSocketRepository.getCompressedStateAndChanges(filter) }
        }

        @Test
        fun `Given a filter when getting entities then its states are returned sorted`() = runTest {
            val filter = EntityFilter(domains = setOf("light"))
            coEvery { webSocketRepository.getStates(filter) } returns listOf(entity("light.desk"), entity(entityId))

            val entities = repository.getEntities(filter)

            assertEquals(listOf(entityId, "light.desk"), entities?.map { it.entityId })
        }

        @Test
        fun `Given states that can't be fetched when getting entities for a filter then null is returned`() = runTest {
            val filter = EntityFilter(entityIds = setOf(entityId))
            coEvery { webSocketRepository.getStates(filter) } returns null

            assertNull(repository.getEntities(filter))
        }

        @Test
        fun `Given an older server when collecting entity updates then state changed events are used`() = runTest {
            every { server.version } returns HomeAssistantVersion(2022, 3, 0)
            val entity = Entity(
                entityId = entityId,
                state = "on",
                attributes = emptyMap(),
                lastChanged = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                lastUpdated = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
            )
            coEvery { webSocketRepository.getStateChanges() } returns flowOf(StateChangedEvent(entityId, newState = entity))

            val updates = checkNotNull(repository.getEntityChanges()).toList().states()

            assertEquals(entity, updates.single())
            coVerify(exactly = 0) { webSocketRepository.getCompressedStateAndChanges() }
        }
    }
}
