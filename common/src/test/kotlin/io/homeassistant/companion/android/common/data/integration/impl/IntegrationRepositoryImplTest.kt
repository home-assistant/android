package io.homeassistant.companion.android.common.data.integration.impl

import io.homeassistant.companion.android.common.BuildConfig
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.integration.CloudPushTransport
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationException
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.impl.IntegrationRepositoryImpl.Companion.PREF_ASK_NOTIFICATION_PERMISSION
import io.homeassistant.companion.android.common.data.integration.impl.entities.CheckRateLimits
import io.homeassistant.companion.android.common.data.integration.impl.entities.EntityResponse
import io.homeassistant.companion.android.common.data.integration.impl.entities.IntegrationRequest
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitRequest
import io.homeassistant.companion.android.common.data.integration.impl.entities.RateLimitResponse
import io.homeassistant.companion.android.common.data.integration.impl.entities.RegisterDeviceIntegrationRequest
import io.homeassistant.companion.android.common.data.integration.impl.entities.RegisterDeviceRequest
import io.homeassistant.companion.android.common.data.servers.ServerConnectionStateProvider
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.servers.UrlState
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedEntityState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateChangedEvent
import io.homeassistant.companion.android.common.data.websocket.impl.entities.CompressedStateDiff
import io.homeassistant.companion.android.common.data.websocket.impl.entities.StateChangedEvent
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.MessagingToken
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import java.time.LocalDateTime
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import retrofit2.Response

private const val STORAGE_KEY_APP_VERSION = "app_version"
private const val STORAGE_KEY_PUSH_TOKEN = "push_token"
private const val STORAGE_KEY_PUSH_URL = "push_url"

class IntegrationRepositoryImplTest {

    private val integrationService = mockk<IntegrationService>()
    private val serverManager = mockk<ServerManager>()
    private val serverID = 42
    private val server = mockk<Server>(relaxed = true)
    private val serverConnection = mockk<ServerConnectionInfo>()
    private val connectionStateProvider = mockk<ServerConnectionStateProvider>()
    private val localStorage = mockk<LocalStorage>()
    private val cloudPushRegistrationMutex = Mutex()

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

        repository = IntegrationRepositoryImpl(
            integrationService,
            serverManager,
            serverID,
            localStorage,
            "",
            "",
            "",
            "",
            cloudPushRegistrationMutex,
        )
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
            cloudPushRegistrationMutex,
        )

        coEvery { localStorage.putBoolean(any(), any()) } returns Unit

        repository.setAskNotificationPermission(true)
        otherRepository.setAskNotificationPermission(false)

        coVerify { localStorage.putBoolean("${serverID}_$PREF_ASK_NOTIFICATION_PERMISSION", true) }
        coVerify { localStorage.putBoolean("${otherServerId}_$PREF_ASK_NOTIFICATION_PERMISSION", false) }
    }

    @Nested
    inner class UpdateRegistrationTests {

        @BeforeEach
        fun stubCloudPushPersistence() {
            coEvery { localStorage.putStrings(any()) } returns Unit
        }

        /**
         * Lets the reregistration of a broken registration reach the server, so that tests can
         * verify it through [IntegrationService] instead of the repository internals.
         */
        private fun stubReregistration() {
            coEvery { serverManager.authenticationRepository(any()) } returns mockk(relaxed = true)
            coEvery { serverManager.webSocketRepository(any()) } returns mockk(relaxed = true)
            coEvery { serverManager.updateServer(any()) } returns Unit
            coEvery { localStorage.putString(any(), any()) } returns Unit
            coEvery { localStorage.remove(any()) } returns Unit
            coEvery { integrationService.registerDevice(any(), any(), any()) } returns mockk(relaxed = true)
            coEvery { integrationService.getConfig(any(), any()) } returns mockk(relaxed = true)
        }

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
            stubReregistration()

            val registration = DeviceRegistration(deviceName = "New device name")
            repository.updateRegistration(
                registration,
                allowReregistration = true,
            )

            coVerify { integrationService.registerDevice(any(), any(), any()) }
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
            stubReregistration()

            val registration = DeviceRegistration(deviceName = "New device name")
            repository.updateRegistration(
                registration,
                allowReregistration = true,
            )

            coVerify { integrationService.registerDevice(any(), any(), any()) }
        }
    }

    @Nested
    inner class CloudPushTransportTests {

        private val requestSlot = slot<IntegrationRequest>()
        private val endpointUrl = "https://push.example.com/up1234"

        /** Every atomic cloud push update, in the order they were persisted. */
        private val persistedCloudPush = mutableListOf<Map<String, String?>>()

        @BeforeEach
        fun setUpRegistrationCapture() {
            coEvery {
                integrationService.callWebhook(any(), capture(requestSlot))
            } returns Response.success("content".toResponseBody())
            coEvery { serverManager.updateServer(any()) } returns Unit
            coEvery { localStorage.putString(any(), any()) } returns Unit
            coEvery { localStorage.remove(any()) } returns Unit
            coEvery { localStorage.putStrings(capture(persistedCloudPush)) } returns Unit
            coEvery { localStorage.getString(STORAGE_KEY_APP_VERSION) } returns null
            storedPushToken(null)
            storedPushUrl(null)
        }

        /**
         * The cloud push state of the single update that was persisted. Reading it also asserts that
         * the token and the endpoint URL were written together, never one after the other.
         */
        private fun storedCloudPush(): Map<String, String?> {
            assertEquals(1, persistedCloudPush.size, "The cloud push state must be stored in one update")
            return persistedCloudPush.single()
        }

        private fun storedPushToken(token: String?) {
            coEvery { localStorage.getString(STORAGE_KEY_PUSH_TOKEN) } returns token
        }

        private fun storedPushUrl(url: String?) {
            coEvery { localStorage.getString(STORAGE_KEY_PUSH_URL) } returns url
        }

        private fun sentAppData(): Map<String, Any?> {
            val request = requestSlot.captured as RegisterDeviceIntegrationRequest
            return checkNotNull(request.data.appData) { "No app data was sent" }
        }

        @Test
        fun `Given no registered endpoint when updating registration with a messaging token then the built-in push URL is sent`() = runTest {
            repository.updateRegistration(DeviceRegistration(pushToken = MessagingToken("fcm-token")))

            assertEquals(BuildConfig.PUSH_URL, sentAppData()["push_url"])
            assertEquals(MessagingToken("fcm-token"), sentAppData()["push_token"])
        }

        @Test
        fun `Given no registered endpoint and no messaging token when updating registration then no push fields are sent`() = runTest {
            repository.updateRegistration(DeviceRegistration(deviceName = "Device"))

            assertFalse(sentAppData().containsKey("push_url"))
            assertFalse(sentAppData().containsKey("push_token"))
            assertEquals(true, sentAppData()["push_websocket_channel"])
        }

        @Test
        fun `Given a blank messaging token when updating registration then no push fields are sent`() = runTest {
            repository.updateRegistration(DeviceRegistration(pushToken = MessagingToken("")))

            assertFalse(sentAppData().containsKey("push_url"))
            assertFalse(sentAppData().containsKey("push_token"))
        }

        @Test
        fun `Given an endpoint when registering it then its URL is sent and stored`() = runTest {
            repository.updateRegistration(
                DeviceRegistration(
                    pushToken = MessagingToken("endpoint-token"),
                    cloudPush = CloudPushTransport.Endpoint(endpointUrl),
                ),
            )

            assertEquals(endpointUrl, sentAppData()["push_url"])
            assertEquals(MessagingToken("endpoint-token"), sentAppData()["push_token"])
            // The URL and the token that owns it are stored as one state.
            assertEquals(
                mapOf(STORAGE_KEY_PUSH_TOKEN to "endpoint-token", STORAGE_KEY_PUSH_URL to endpointUrl),
                storedCloudPush(),
            )
        }

        @Test
        fun `Given a registered endpoint when updating registration without a transport then the endpoint is kept`() = runTest {
            storedPushUrl(endpointUrl)
            storedPushToken("endpoint-token")
            repository.updateRegistration(DeviceRegistration(appVersion = AppVersion("1.0.0", 1)))

            assertEquals(endpointUrl, sentAppData()["push_url"])
            assertEquals(MessagingToken("endpoint-token"), sentAppData()["push_token"])
            assertEquals(endpointUrl, storedCloudPush()[STORAGE_KEY_PUSH_URL])
        }

        @Test
        fun `Given a registered endpoint when handing the registration back to Firebase then the built-in push URL is sent and the endpoint is removed`() = runTest {
            storedPushUrl(endpointUrl)
            storedPushToken("endpoint-token")
            repository.updateRegistration(
                DeviceRegistration(
                    pushToken = MessagingToken("fcm-token"),
                    cloudPush = CloudPushTransport.Firebase,
                ),
            )

            assertEquals(BuildConfig.PUSH_URL, sentAppData()["push_url"])
            assertEquals(MessagingToken("fcm-token"), sentAppData()["push_token"])
            // The transport is declared, so the token belongs to Firebase again. Dropping the
            // endpoint and storing the new token is one update.
            assertEquals(
                mapOf(STORAGE_KEY_PUSH_TOKEN to "fcm-token", STORAGE_KEY_PUSH_URL to null),
                storedCloudPush(),
            )
        }

        @Test
        fun `Given a registered endpoint when the messaging token is refreshed then the token is not applied`() = runTest {
            storedPushUrl(endpointUrl)
            storedPushToken("endpoint-token")

            // Firebase rotating its token updates the registration with only the new token.
            repository.updateRegistration(DeviceRegistration(pushToken = MessagingToken("new-messaging-token")))

            assertEquals(endpointUrl, sentAppData()["push_url"])
            assertEquals(MessagingToken("endpoint-token"), sentAppData()["push_token"])
            assertEquals(true, sentAppData()["push_websocket_channel"])
            // The refreshed token is not the endpoint's, so it is not stored and the URL stays.
            assertEquals(endpointUrl, storedCloudPush()[STORAGE_KEY_PUSH_URL])
            assertFalse(storedCloudPush().containsKey(STORAGE_KEY_PUSH_TOKEN))
        }

        @Test
        fun `Given a registered endpoint when switching to Firebase without a token then cloud push is unregistered`() = runTest {
            storedPushUrl(endpointUrl)
            storedPushToken("endpoint-token")

            repository.updateRegistration(DeviceRegistration(cloudPush = CloudPushTransport.Firebase))

            // The endpoint token does not belong to Firebase, so it must not be reused.
            assertFalse(sentAppData().containsKey("push_url"))
            assertFalse(sentAppData().containsKey("push_token"))
            // Both keys are dropped in one update, so no mixed state can be observed or kept.
            assertEquals(
                mapOf(STORAGE_KEY_PUSH_TOKEN to null, STORAGE_KEY_PUSH_URL to null),
                storedCloudPush(),
            )
        }

        @Test
        fun `Given Firebase when switching to an endpoint without a token then cloud push is unregistered`() = runTest {
            storedPushToken("fcm-token")

            repository.updateRegistration(
                DeviceRegistration(cloudPush = CloudPushTransport.Endpoint(endpointUrl)),
            )

            // The messaging token does not belong to the endpoint, so it must not be handed over.
            assertFalse(sentAppData().containsKey("push_url"))
            assertFalse(sentAppData().containsKey("push_token"))
            // Nothing was registered, so the endpoint URL must not be stored without its token
            // either. Storing it would leave a registration the server does not know about.
            assertEquals(
                mapOf(STORAGE_KEY_PUSH_TOKEN to null, STORAGE_KEY_PUSH_URL to null),
                storedCloudPush(),
            )
        }

        @Test
        fun `Given a registered endpoint when its URL is renewed then the same token is kept`() = runTest {
            storedPushUrl(endpointUrl)
            storedPushToken("endpoint-token")
            val renewedUrl = "https://push.example.com/up5678"

            repository.updateRegistration(
                DeviceRegistration(cloudPush = CloudPushTransport.Endpoint(renewedUrl)),
            )

            // Renewing the URL is the same registration, so its token stays valid.
            assertEquals(renewedUrl, sentAppData()["push_url"])
            assertEquals(MessagingToken("endpoint-token"), sentAppData()["push_token"])
            // The new URL is stored while the owning token is left untouched.
            assertEquals(renewedUrl, storedCloudPush()[STORAGE_KEY_PUSH_URL])
            assertFalse(storedCloudPush().containsKey(STORAGE_KEY_PUSH_TOKEN))
        }

        @Test
        fun `Given a registered endpoint when a blank messaging token is sent then the endpoint is kept`() = runTest {
            storedPushUrl(endpointUrl)
            storedPushToken("endpoint-token")

            // Builds without Firebase resync with a blank token.
            repository.updateRegistration(DeviceRegistration(pushToken = MessagingToken("")))

            assertEquals(endpointUrl, sentAppData()["push_url"])
            assertEquals(MessagingToken("endpoint-token"), sentAppData()["push_token"])
            // The blank token belongs to no transport, so the endpoint and its token stay.
            assertEquals(endpointUrl, storedCloudPush()[STORAGE_KEY_PUSH_URL])
            assertFalse(storedCloudPush().containsKey(STORAGE_KEY_PUSH_TOKEN))
        }

        @Test
        fun `Given a registered endpoint when deleting the preferences of one server then the endpoint is kept for the other servers`() = runTest {
            storedPushUrl(endpointUrl)
            storedPushToken("messaging-token")
            coEvery { localStorage.getStringSet(any()) } returns emptySet()

            repository.deletePreferences()

            coVerify(exactly = 0) {
                localStorage.remove(STORAGE_KEY_PUSH_URL)
                localStorage.remove(STORAGE_KEY_PUSH_TOKEN)
            }
        }

        @Test
        fun `Given a registered endpoint when getting the registration then it reports the endpoint`() = runTest {
            storedPushUrl(endpointUrl)
            storedPushToken("endpoint-token")
            assertEquals(CloudPushTransport.Endpoint(endpointUrl), repository.getRegistration().cloudPush)
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = ["", " "])
        fun `Given no usable stored endpoint when getting the registration then it reports Firebase`(storedUrl: String?) = runTest {
            storedPushUrl(storedUrl)
            storedPushToken("fcm-token")
            assertEquals(CloudPushTransport.Firebase, repository.getRegistration().cloudPush)
        }

        @Test
        fun `Given a registration with a messaging token when serializing the request then the push fields are unchanged`() = runTest {
            repository.updateRegistration(DeviceRegistration(pushToken = MessagingToken("fcm-token")))

            val json = kotlinJsonMapper.encodeToString(
                (requestSlot.captured as RegisterDeviceIntegrationRequest).data,
            )
            assertTrue(
                json.contains(
                    """"app_data":{"push_websocket_channel":true,"push_url":"${BuildConfig.PUSH_URL}","push_token":"fcm-token"}""",
                ),
                "Unexpected app data in $json",
            )
        }
    }

    @Nested
    inner class CloudPushRegistrationSerialization {

        private val otherServerId = 43
        private val endpointUrl = "https://push.example.com/up1234"
        private val stored = mutableMapOf<String, String>()
        private val sentRequests = mutableListOf<IntegrationRequest>()

        /** Repository of a second server, sharing the device-wide storage and lock. */
        private lateinit var otherRepository: IntegrationRepository

        @BeforeEach
        fun setUpSecondServer() {
            coEvery { serverManager.getServer(otherServerId) } returns server
            coEvery { serverManager.connectionStateProvider(otherServerId) } returns connectionStateProvider
            coEvery { serverManager.updateServer(any()) } returns Unit
            otherRepository = IntegrationRepositoryImpl(
                integrationService,
                serverManager,
                otherServerId,
                localStorage,
                "",
                "",
                "",
                "",
                cloudPushRegistrationMutex,
            )

            // A stateful storage so that the order of reads and writes is observable.
            coEvery { localStorage.getString(any()) } answers { stored[firstArg()] }
            coEvery { localStorage.putString(any(), any()) } answers {
                val value = secondArg<String?>()
                if (value == null) stored.remove(firstArg()) else stored[firstArg()] = value
                Unit
            }
            coEvery { localStorage.remove(any()) } answers {
                stored.remove(firstArg())
                Unit
            }
            coEvery { localStorage.putStrings(any()) } answers {
                firstArg<Map<String, String?>>().forEach { (key, value) ->
                    if (value == null) stored.remove(key) else stored[key] = value
                }
                Unit
            }
        }

        private fun sentAppDataOf(index: Int): Map<String, Any?> {
            val request = sentRequests[index] as RegisterDeviceIntegrationRequest
            return checkNotNull(request.data.appData) { "No app data was sent" }
        }

        @Test
        fun `Given a registration update in progress when another server updates then it waits and resolves afterwards`() = runTest {
            stored[STORAGE_KEY_PUSH_TOKEN] = "old-messaging-token"
            coEvery {
                integrationService.callWebhook(any(), capture(sentRequests))
            } returns Response.success("content".toResponseBody())

            // Stand in for a registration update that is in flight on another server.
            cloudPushRegistrationMutex.lock()

            // Firebase refreshes its token meanwhile. It must not resolve against the old state.
            val refresh = launch {
                otherRepository.updateRegistration(
                    DeviceRegistration(pushToken = MessagingToken("new-messaging-token")),
                )
            }
            runCurrent()

            assertTrue(sentRequests.isEmpty(), "The refresh must wait for the update in progress")

            // The update in flight registered an endpoint before releasing the lock.
            stored[STORAGE_KEY_PUSH_URL] = endpointUrl
            stored[STORAGE_KEY_PUSH_TOKEN] = "endpoint-token"
            cloudPushRegistrationMutex.unlock()
            refresh.join()

            // The refresh resolved against the endpoint and left it alone.
            assertEquals(1, sentRequests.size)
            assertEquals(endpointUrl, sentAppDataOf(0)["push_url"])
            assertEquals(MessagingToken("endpoint-token"), sentAppDataOf(0)["push_token"])
            assertEquals(endpointUrl, stored[STORAGE_KEY_PUSH_URL])
            assertEquals("endpoint-token", stored[STORAGE_KEY_PUSH_TOKEN])
        }

        @Test
        fun `Given a registered endpoint when a broken registration is reregistered then the endpoint is kept`() = runTest {
            stored[STORAGE_KEY_PUSH_URL] = endpointUrl
            stored[STORAGE_KEY_PUSH_TOKEN] = "endpoint-token"
            coEvery {
                integrationService.callWebhook(any(), any())
            } returns Response.error(410, "".toResponseBody())
            val registrationSlot = slot<RegisterDeviceRequest>()
            coEvery {
                integrationService.registerDevice(any(), any(), capture(registrationSlot))
            } returns mockk(relaxed = true)
            coEvery { serverManager.authenticationRepository(any()) } returns mockk(relaxed = true)
            coEvery { serverManager.webSocketRepository(any()) } returns mockk(relaxed = true)
            coEvery { integrationService.getConfig(any(), any()) } returns mockk(relaxed = true)

            // Reregistration runs while the update that detected the breakage holds the lock.
            repository.updateRegistration(
                DeviceRegistration(pushToken = MessagingToken("new-messaging-token")),
                allowReregistration = true,
            )

            val appData = checkNotNull(registrationSlot.captured.appData)
            assertEquals(endpointUrl, appData["push_url"])
            assertEquals(MessagingToken("endpoint-token"), appData["push_token"])
            assertEquals(endpointUrl, stored[STORAGE_KEY_PUSH_URL])
            assertEquals("endpoint-token", stored[STORAGE_KEY_PUSH_TOKEN])
        }
    }

    @Nested
    inner class NotificationRateLimits {

        private val rateLimits = RateLimitResponse(
            attempts = 1,
            successful = 1,
            errors = 0,
            total = 1,
            maximum = 500,
            remaining = 499,
            resetsAt = "2026-10-03T00:00:00.000Z",
        )

        @BeforeEach
        fun setUpRateLimits() {
            coEvery {
                integrationService.getRateLimit(any(), any())
            } returns CheckRateLimits(target = "target", rateLimits = rateLimits)
            coEvery { localStorage.getString(STORAGE_KEY_PUSH_URL) } returns null
            coEvery { localStorage.getString(STORAGE_KEY_PUSH_TOKEN) } returns null
        }

        @Test
        fun `Given Firebase with a messaging token when getting rate limits then the token is sent`() = runTest {
            coEvery { localStorage.getString(STORAGE_KEY_PUSH_TOKEN) } returns "fcm-token"

            assertEquals(rateLimits, repository.getNotificationRateLimits())

            coVerify {
                integrationService.getRateLimit(BuildConfig.RATE_LIMIT_URL, RateLimitRequest("fcm-token"))
            }
        }

        @Test
        fun `Given a registered endpoint when getting rate limits then its token is never sent`() = runTest {
            coEvery { localStorage.getString(STORAGE_KEY_PUSH_URL) } returns "https://push.example.com/up1234"
            coEvery { localStorage.getString(STORAGE_KEY_PUSH_TOKEN) } returns "endpoint-token"

            // The rate limits describe the built-in push proxy, which does not own this token.
            val failure = try {
                repository.getNotificationRateLimits()
                null
            } catch (e: IntegrationException) {
                e
            }
            assertNotNull(failure, "Rate limits must not be read while a push endpoint is registered")

            coVerify(exactly = 0) { integrationService.getRateLimit(any(), any()) }
        }

        @Test
        fun `Given no endpoint and no token when getting rate limits then the existing request is sent`() = runTest {
            assertEquals(rateLimits, repository.getNotificationRateLimits())

            coVerify { integrationService.getRateLimit(BuildConfig.RATE_LIMIT_URL, RateLimitRequest("")) }
        }
    }

    @Nested
    inner class EntityUpdates {
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

        private fun givenCurrentStatesFetchReturns(state: String) {
            coEvery { webSocketRepository.getStates() } returns listOf(
                EntityResponse(
                    entityId = entityId,
                    state = state,
                    attributes = emptyMap(),
                    lastChanged = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                    lastUpdated = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                ),
            )
        }

        @Test
        fun `Given compressed state changes when collecting entity updates then added entities and resolved diffs are emitted`() = runTest {
            coEvery { webSocketRepository.getCompressedStateAndChanges() } returns flowOf(
                addedEvent("on", attributes = mapOf("brightness" to 100)),
                changedEvent("off"),
            )

            val updates = checkNotNull(repository.getEntityUpdates()).toList()

            assertEquals(listOf("on", "off"), updates.map { it.state })
            // The diff keeps the attributes of the state it applies to
            assertEquals(mapOf<String, Any?>("brightness" to 100), updates[1].attributes)
            coVerify(exactly = 1) { webSocketRepository.getStates() }
        }

        @Test
        fun `Given a collector joining after the initial states when collecting entity updates then the seeded states resolve the diff`() = runTest {
            coEvery { webSocketRepository.getCompressedStateAndChanges() } returns flowOf(changedEvent("off"))
            givenCurrentStatesFetchReturns("off")

            val updates = checkNotNull(repository.getEntityUpdates()).toList()

            assertEquals("off", updates.single().state)
        }

        @Test
        fun `Given a diff for an unknown entity and a failing states fetch when collecting entity updates then the diff is skipped`() = runTest {
            coEvery { webSocketRepository.getCompressedStateAndChanges() } returns flowOf(changedEvent("off"))
            coEvery { webSocketRepository.getStates() } returns null

            val updates = checkNotNull(repository.getEntityUpdates()).toList()

            assertTrue(updates.isEmpty())
        }

        @Test
        fun `Given a diff for a removed entity when collecting entity updates then the diff is dropped`() = runTest {
            coEvery { webSocketRepository.getCompressedStateAndChanges() } returns flowOf(
                addedEvent("on"),
                CompressedStateChangedEvent(removed = listOf(entityId)),
                changedEvent("off"),
            )

            val updates = checkNotNull(repository.getEntityUpdates()).toList()

            assertEquals(listOf("on"), updates.map { it.state })
            coVerify(exactly = 1) { webSocketRepository.getStates() }
        }

        @Test
        fun `Given a states fetch when collecting entity updates for ids then only the subscribed entities are kept`() = runTest {
            coEvery { webSocketRepository.getCompressedStateAndChanges(listOf(entityId)) } returns flowOf(
                changedEvent("off"),
            )
            coEvery { webSocketRepository.getStates() } returns listOf(
                EntityResponse(
                    entityId = entityId,
                    state = "off",
                    attributes = emptyMap(),
                    lastChanged = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                    lastUpdated = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                ),
                EntityResponse(
                    entityId = "light.other",
                    state = "on",
                    attributes = emptyMap(),
                    lastChanged = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                    lastUpdated = LocalDateTime.of(2024, 1, 1, 12, 0, 0),
                ),
            )

            val updates = checkNotNull(repository.getEntityUpdates(listOf(entityId))).toList()

            assertEquals(listOf(entityId), updates.map { it.entityId })
        }

        @Test
        fun `Given compressed state changes when collecting entity updates for ids then the subscription filters without admin rights`() = runTest {
            every { server.user.isAdmin } returns false
            coEvery { webSocketRepository.getCompressedStateAndChanges(listOf(entityId)) } returns flowOf(addedEvent("on"))

            val updates = checkNotNull(repository.getEntityUpdates(listOf(entityId))).toList()

            assertEquals("on", updates.single().state)
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

            val updates = checkNotNull(repository.getEntityUpdates()).toList()

            assertEquals(entity, updates.single())
            coVerify(exactly = 0) { webSocketRepository.getCompressedStateAndChanges() }
        }
    }
}
