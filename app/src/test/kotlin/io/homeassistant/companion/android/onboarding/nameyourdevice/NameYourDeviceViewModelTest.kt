package io.homeassistant.companion.android.onboarding.nameyourdevice

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.authentication.ServerRegistrationRepository
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.common.util.MessagingToken
import io.homeassistant.companion.android.common.util.MessagingTokenProvider
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import io.homeassistant.companion.android.database.server.TemporaryServer
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.NameYourDeviceRoute
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import retrofit2.HttpException
import retrofit2.Response

private const val DEFAULT_DEVICE_NAME = "Pixel 42"

@ExtendWith(MainDispatcherJUnit5Extension::class, ConsoleLogExtension::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NameYourDeviceViewModelTest {

    private val route: NameYourDeviceRoute = NameYourDeviceRoute("http://ha.local", "test_auth_code")
    private val serverManager: ServerManager = mockk()

    private val serverRegistrationRepository: ServerRegistrationRepository = mockk()
    private val authenticationRepository: AuthenticationRepository = mockk()
    private val appVersionProvider: AppVersionProvider = AppVersionProvider {
        AppVersion.from("test", 42)
    }
    private val messagingTokenProvider: MessagingTokenProvider = MessagingTokenProvider {
        return@MessagingTokenProvider MessagingToken("test_messaging_token")
    }
    private val integrationRepository: IntegrationRepository = mockk()

    private lateinit var viewModel: NameYourDeviceViewModel

    private fun createServer(serverId: Int, externalUrl: String = route.url) = Server(
        id = serverId,
        _name = "Test Server",
        connection = ServerConnectionInfo(externalUrl = externalUrl),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )

    private fun createTemporaryServer(
        externalUrl: String = route.url,
        allowInsecureConnection: Boolean? = null,
    ) = TemporaryServer(
        externalUrl = externalUrl,
        allowInsecureConnection = allowInsecureConnection,
        session = ServerSessionInfo(),
    )

    @BeforeEach
    fun setup() {
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { serverManager.authenticationRepository(any()) } returns authenticationRepository
        coEvery { serverManager.activateServer(any()) } just Runs
        coEvery { serverManager.getServer(any<Int>()) } answers { createServer(firstArg<Int>()) }
        coEvery { serverManager.updateServer(any()) } just Runs

        viewModel = NameYourDeviceViewModel(
            route,
            serverManager,
            serverRegistrationRepository,
            appVersionProvider,
            messagingTokenProvider,
            defaultName = DEFAULT_DEVICE_NAME,
        )
    }

    @Test
    fun `Given viewModelInitialized when observingDeviceNameFlow then emits DefaultDeviceName`() = runTest {
        assertEquals(DEFAULT_DEVICE_NAME, viewModel.deviceNameFlow.value)
    }

    @Test
    fun `Given viewModelInitialized when isValidNameFlow then emits true`() = runTest {
        viewModel.isValidNameFlow.test {
            assertTrue(awaitItem())
        }
    }

    @Test
    fun `Given viewModelInitialized when isSaveClickableFlow then emits true`() = runTest {
        viewModel.isSaveClickableFlow.test {
            assertTrue(awaitItem())
        }
    }

    @Test
    fun `Given viewModel when onDeviceNameChangeCalled then deviceName and isValidName flows are updated`() = runTest {
        turbineScope {
            val deviceNameFlow = viewModel.deviceNameFlow.testIn(backgroundScope)
            val isValidNameFlow = viewModel.isValidNameFlow.testIn(backgroundScope)

            assertEquals(DEFAULT_DEVICE_NAME, deviceNameFlow.awaitItem()) // Initial value
            assertTrue(isValidNameFlow.awaitItem()) // Initial value

            viewModel.onDeviceNameChange("My Test Device")
            advanceUntilIdle()
            assertEquals("My Test Device", deviceNameFlow.awaitItem())
            isValidNameFlow.expectNoEvents() // Name is still valid

            viewModel.onDeviceNameChange("")
            advanceUntilIdle()
            assertEquals("", deviceNameFlow.awaitItem())
            assertFalse(isValidNameFlow.awaitItem())

            viewModel.onDeviceNameChange("Super")
            advanceUntilIdle()
            assertEquals("Super", deviceNameFlow.awaitItem())
            assertTrue(isValidNameFlow.awaitItem())
        }
    }

    @Test
    fun `Given successful add server when onSaveClick then emits DeviceNameSaved event`() = runTest {
        val testServerId = 1
        val tempServerSlot = slot<TemporaryServer>()
        coEvery {
            serverRegistrationRepository.registerAuthorizationCode(
                url = route.url,
                authorizationCode = route.authCode,
                allowInsecureConnection = null,
            )
        } returns createTemporaryServer()
        coEvery { serverManager.addServer(capture(tempServerSlot)) } returns testServerId
        coEvery {
            integrationRepository.registerDevice(
                DeviceRegistration(
                    appVersionProvider(),
                    DEFAULT_DEVICE_NAME,
                    messagingTokenProvider(),
                ),
            )
        } just Runs

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)

            viewModel.onSaveClick()
            advanceUntilIdle()

            val event = navEvents.awaitItem()
            assertTrue(event is NameYourDeviceNavigationEvent.DeviceNameSaved)
            assertEquals(testServerId, (event as NameYourDeviceNavigationEvent.DeviceNameSaved).serverId)

            coVerify(exactly = 1) {
                serverRegistrationRepository.registerAuthorizationCode(route.url, route.authCode, null)
                serverManager.addServer(any())
                integrationRepository.registerDevice(any())
                serverManager.activateServer(testServerId)
            }
            // HTTP URL means allowInsecureConnection is null (not enforced)
            assertEquals(null, tempServerSlot.captured.allowInsecureConnection)
        }
    }

    @Test
    fun `Given custom deviceName and successful add server when onSaveClick then emits DeviceNameSaved event and registered with custom name and server activated`() = runTest {
        val customDeviceName = "Pixel"
        viewModel.onDeviceNameChange(customDeviceName)
        advanceUntilIdle()

        val testServerId = 1
        coEvery {
            serverRegistrationRepository.registerAuthorizationCode(
                url = route.url,
                authorizationCode = route.authCode,
                allowInsecureConnection = null,
            )
        } returns createTemporaryServer()
        coEvery { serverManager.addServer(any()) } returns testServerId
        coEvery {
            integrationRepository.registerDevice(
                DeviceRegistration(
                    appVersionProvider(),
                    customDeviceName,
                    messagingTokenProvider(),
                ),
            )
        } just Runs

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)

            viewModel.onSaveClick()
            advanceUntilIdle()

            val event = navEvents.awaitItem()
            assertTrue(event is NameYourDeviceNavigationEvent.DeviceNameSaved)
            assertEquals(testServerId, (event as NameYourDeviceNavigationEvent.DeviceNameSaved).serverId)
            // Based on default url used in this test
            assertTrue(event.hasPlainTextAccess)
            assertFalse(event.isPubliclyAccessible)

            coVerify {
                integrationRepository.registerDevice(
                    DeviceRegistration(
                        appVersionProvider(),
                        customDeviceName,
                        messagingTokenProvider(),
                    ),
                )
                serverManager.activateServer(testServerId)
            }
        }
    }

    @Test
    fun `Given public secure url when onSaveClick then emits DeviceNameSaved with hasPlainTextAccess to false and isPubliclyAccessible true and enforces secure connection`() = runTest {
        val secureRoute = NameYourDeviceRoute("https://www.home-assistant.io", "auth_code")
        viewModel = NameYourDeviceViewModel(
            secureRoute,
            serverManager,
            serverRegistrationRepository,
            appVersionProvider,
            messagingTokenProvider,
            defaultName = DEFAULT_DEVICE_NAME,
        )

        val testServerId = 1
        val tempServerSlot = slot<TemporaryServer>()
        coEvery {
            serverRegistrationRepository.registerAuthorizationCode(
                url = secureRoute.url,
                authorizationCode = secureRoute.authCode,
                allowInsecureConnection = false,
            )
        } returns createTemporaryServer(externalUrl = secureRoute.url, allowInsecureConnection = false)
        coEvery { serverManager.addServer(capture(tempServerSlot)) } returns testServerId
        coEvery {
            integrationRepository.registerDevice(
                DeviceRegistration(
                    appVersionProvider(),
                    DEFAULT_DEVICE_NAME,
                    messagingTokenProvider(),
                ),
            )
        } just Runs

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)

            viewModel.onSaveClick()
            advanceUntilIdle()

            val event = navEvents.awaitItem()
            assertTrue(event is NameYourDeviceNavigationEvent.DeviceNameSaved)
            assertEquals(testServerId, (event as NameYourDeviceNavigationEvent.DeviceNameSaved).serverId)
            assertFalse(event.hasPlainTextAccess)
            assertTrue(event.isPubliclyAccessible)

            coVerify(exactly = 1) {
                serverManager.activateServer(testServerId)
            }
            // Secure connection is enforced during server creation
            assertEquals(false, tempServerSlot.captured.allowInsecureConnection)
        }
    }

    @Test
    fun `Given serverRegistrationRepository registerAuthorizationCode returns null when onSaveClick then emits Error event and attempts no cleanup`() = runTest {
        coEvery {
            serverRegistrationRepository.registerAuthorizationCode(
                url = route.url,
                authorizationCode = route.authCode,
                allowInsecureConnection = null,
            )
        } returns null

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)

            viewModel.onSaveClick()
            advanceUntilIdle()

            assertError(navEvents.awaitItem(), commonR.string.webview_error)

            coVerify(exactly = 0) {
                serverManager.addServer(any<TemporaryServer>())
                integrationRepository.registerDevice(any())
                serverManager.activateServer(any())
                authenticationRepository.revokeSession()
                serverManager.removeServer(any())
            }
        }
    }

    @Test
    fun `Given serverRegistrationRepository Throws when onSaveClick then emits Error event and attempts no cleanup`() = runTest {
        coEvery {
            serverRegistrationRepository.registerAuthorizationCode(
                url = route.url,
                authorizationCode = route.authCode,
                allowInsecureConnection = null,
            )
        } throws RuntimeException("Auth code registration failed")

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)

            viewModel.onSaveClick()
            advanceUntilIdle()

            assertError(navEvents.awaitItem(), commonR.string.webview_error)

            coVerify(exactly = 1) {
                serverRegistrationRepository.registerAuthorizationCode(route.url, route.authCode, null)
            }
            coVerify(exactly = 0) {
                serverManager.addServer(any<TemporaryServer>())
                integrationRepository.registerDevice(any())
                serverManager.activateServer(any())
            }
        }
    }

    @Test
    fun `Given integrationRepository registerDevice throws when onSaveClick then emits error event and attempts cleanup`() = runTest {
        val testServerId = 1
        coEvery {
            serverRegistrationRepository.registerAuthorizationCode(
                url = route.url,
                authorizationCode = route.authCode,
                allowInsecureConnection = null,
            )
        } returns createTemporaryServer()
        coEvery { serverManager.addServer(any()) } returns testServerId
        coEvery { integrationRepository.registerDevice(any()) } throws RuntimeException("Device registration failed")
        coEvery { authenticationRepository.revokeSession() } just Runs
        coEvery { serverManager.removeServer(testServerId) } just Runs

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)

            viewModel.onSaveClick()
            advanceUntilIdle()

            assertError(navEvents.awaitItem(), commonR.string.webview_error)
            coVerify(exactly = 1) {
                serverRegistrationRepository.registerAuthorizationCode(route.url, route.authCode, null)
                serverManager.addServer(any<TemporaryServer>())
                integrationRepository.registerDevice(any())
                authenticationRepository.revokeSession()
                serverManager.removeServer(testServerId)
            }
            coVerify(exactly = 0) {
                serverManager.activateServer(any())
            }
        }
    }

    @Test
    fun `Given registerAuthorizationCode throws HttpException 404 when onSaveClick then emits error with error_with_registration message`() = runTest {
        coEvery {
            serverRegistrationRepository.registerAuthorizationCode(
                url = route.url,
                authorizationCode = route.authCode,
                allowInsecureConnection = null,
            )
        } throws HttpException(Response.error<Any>(404, mockk(relaxed = true)))

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)
            viewModel.onSaveClick()
            advanceUntilIdle()
            assertError(navEvents.awaitItem(), commonR.string.error_with_registration)
        }
    }

    @Test
    fun `Given registerAuthorizationCode throws SSLHandshakeException when onSaveClick then emits error with webview_error_FAILED_SSL_HANDSHAKE message`() = runTest {
        val exception = SSLHandshakeException("SSL handshake failed")
        coEvery {
            serverRegistrationRepository.registerAuthorizationCode(
                url = route.url,
                authorizationCode = route.authCode,
                allowInsecureConnection = null,
            )
        } throws exception

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)
            viewModel.onSaveClick()
            advanceUntilIdle()
            assertError(navEvents.awaitItem(), commonR.string.webview_error_FAILED_SSL_HANDSHAKE)
        }
    }

    @Test
    fun `Given registerAuthorizationCode throws SSLException when onSaveClick then emits error with webview_error_SSL_INVALID message`() = runTest {
        coEvery {
            serverRegistrationRepository.registerAuthorizationCode(
                url = route.url,
                authorizationCode = route.authCode,
                allowInsecureConnection = null,
            )
        } throws SSLException("SSL invalid")

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)
            viewModel.onSaveClick()
            advanceUntilIdle()
            assertError(navEvents.awaitItem(), commonR.string.webview_error_SSL_INVALID)
        }
    }

    @Test
    fun `Given registering server when onSaveClick then isSavingFlow is properly updated`() = runTest {
        viewModel.isSavingFlow.test {
            assertFalse(awaitItem())
            coEvery {
                serverRegistrationRepository.registerAuthorizationCode(
                    url = route.url,
                    authorizationCode = route.authCode,
                    allowInsecureConnection = null,
                )
            } coAnswers {
                assertTrue(awaitItem())
                throw RuntimeException("Failed to register")
            }
            viewModel.onSaveClick()
            advanceUntilIdle()
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `Given currently saving when isSaveClickableFlow collecting then isSaveClickable is updating accordingly`() = runTest {
        viewModel.isSaveClickableFlow.test {
            assertTrue(awaitItem())
            coEvery {
                serverRegistrationRepository.registerAuthorizationCode(
                    url = route.url,
                    authorizationCode = route.authCode,
                    allowInsecureConnection = null,
                )
            } coAnswers {
                assertFalse(awaitItem())
                throw RuntimeException("Failed to register")
            }
            viewModel.onSaveClick()
            advanceUntilIdle()
            assertTrue(awaitItem())
            coVerify { serverRegistrationRepository.registerAuthorizationCode(route.url, route.authCode, null) }
        }
    }

    @Test
    fun `Given updating device name when isSaveClickableFlow collecting then isSaveClickable is updating according to isValidDeviceName`() = runTest {
        viewModel.isSaveClickableFlow.test {
            assertTrue(awaitItem())

            viewModel.onDeviceNameChange("")
            advanceUntilIdle()
            assertFalse(awaitItem())

            viewModel.onDeviceNameChange("valid")
            advanceUntilIdle()
            assertTrue(awaitItem())
        }
    }

    private fun assertError(event: NameYourDeviceNavigationEvent, expectedResId: Int) {
        assertTrue(event is NameYourDeviceNavigationEvent.Error)
        assertEquals(expectedResId, (event as NameYourDeviceNavigationEvent.Error).messageRes)
    }
}
