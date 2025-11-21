package io.homeassistant.companion.android.onboarding.nameyourdevice

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.DeviceRegistration
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.common.util.MessagingToken
import io.homeassistant.companion.android.common.util.MessagingTokenProvider
import io.homeassistant.companion.android.onboarding.nameyourdevice.navigation.NameYourDeviceRoute
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
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
    private val appVersionProvider: AppVersionProvider = AppVersionProvider {
        AppVersion.from("test", 42)
    }
    private val messagingTokenProvider: MessagingTokenProvider = MessagingTokenProvider {
        return@MessagingTokenProvider MessagingToken("test_messaging_token")
    }
    private val authRepository: AuthenticationRepository = mockk()
    private val integrationRepository: IntegrationRepository = mockk()

    private lateinit var viewModel: NameYourDeviceViewModel

    @BeforeEach
    fun setup() {
        coEvery { serverManager.authenticationRepository(any()) } returns authRepository
        coEvery { serverManager.integrationRepository(any()) } returns integrationRepository
        coEvery { integrationRepository.setAllowInsecureConnection(any()) } just Runs

        viewModel = NameYourDeviceViewModel(
            route,
            serverManager,
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
        val tempServerId = 0
        coEvery { serverManager.addServer(any()) } returns tempServerId
        coEvery { authRepository.registerAuthorizationCode(route.authCode) } just Runs
        coEvery {
            integrationRepository.registerDevice(
                DeviceRegistration(
                    appVersionProvider(),
                    DEFAULT_DEVICE_NAME,
                    messagingTokenProvider(),
                ),
            )
        } just Runs
        coEvery { serverManager.convertTemporaryServer(tempServerId) } returns testServerId

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)

            viewModel.onSaveClick()
            advanceUntilIdle()

            val event = navEvents.awaitItem()
            assertTrue(event is NameYourDeviceNavigationEvent.DeviceNameSaved)
            assertEquals(testServerId, (event as NameYourDeviceNavigationEvent.DeviceNameSaved).serverId)

            coVerify(exactly = 1) {
                serverManager.addServer(any())
                authRepository.registerAuthorizationCode(route.authCode)
                integrationRepository.registerDevice(any())
                serverManager.convertTemporaryServer(tempServerId)
            }
            coVerify(exactly = 0) {
                integrationRepository.setAllowInsecureConnection(any())
            }
        }
    }

    @Test
    fun `Given custom deviceName and successful add server when onSaveClick then emits DeviceNameSaved event and registered with custom name`() = runTest {
        val customDeviceName = "Pixel"
        viewModel.onDeviceNameChange(customDeviceName)
        advanceUntilIdle()

        val testServerId = 1
        val tempServerId = 0
        coEvery { serverManager.addServer(any()) } returns tempServerId
        coEvery { authRepository.registerAuthorizationCode(route.authCode) } just Runs
        coEvery {
            integrationRepository.registerDevice(
                DeviceRegistration(
                    appVersionProvider(),
                    customDeviceName,
                    messagingTokenProvider(),
                ),
            )
        } just Runs
        coEvery { serverManager.convertTemporaryServer(tempServerId) } returns testServerId

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
            }
        }
    }

    @Test
    fun `Given public secure url when onSaveClick then emits DeviceNameSaved with hasPlainTextAccess to false and isPubliclyAccessible true and enforces secure connection`() = runTest {
        viewModel = NameYourDeviceViewModel(
            NameYourDeviceRoute("https://www.home-assistant.io", "auth_code"),
            serverManager,
            appVersionProvider,
            messagingTokenProvider,
            defaultName = DEFAULT_DEVICE_NAME,
        )

        val testServerId = 1
        val tempServerId = 0
        coEvery { serverManager.addServer(any()) } returns tempServerId
        coEvery { authRepository.registerAuthorizationCode("auth_code") } just Runs
        coEvery {
            integrationRepository.registerDevice(
                DeviceRegistration(
                    appVersionProvider(),
                    DEFAULT_DEVICE_NAME,
                    messagingTokenProvider(),
                ),
            )
        } just Runs
        coEvery { serverManager.convertTemporaryServer(tempServerId) } returns testServerId

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
                integrationRepository.setAllowInsecureConnection(false)
            }
        }
    }

    @Test
    fun `Given setAllowInsecureConnection throws when onSaveClick with HTTPS then logs error but continues successfully`() = runTest {
        viewModel = NameYourDeviceViewModel(
            NameYourDeviceRoute("https://ha.local", "auth_code"),
            serverManager,
            appVersionProvider,
            messagingTokenProvider,
            defaultName = DEFAULT_DEVICE_NAME,
        )

        val testServerId = 1
        val tempServerId = 0
        coEvery { serverManager.addServer(any()) } returns tempServerId
        coEvery { authRepository.registerAuthorizationCode("auth_code") } just Runs
        coEvery {
            integrationRepository.registerDevice(
                DeviceRegistration(
                    appVersionProvider(),
                    DEFAULT_DEVICE_NAME,
                    messagingTokenProvider(),
                ),
            )
        } just Runs
        coEvery { serverManager.convertTemporaryServer(tempServerId) } returns testServerId
        coEvery { integrationRepository.setAllowInsecureConnection(false) } throws RuntimeException("Failed to set connection security")

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)

            viewModel.onSaveClick()
            advanceUntilIdle()

            val event = navEvents.awaitItem()
            assertTrue(event is NameYourDeviceNavigationEvent.DeviceNameSaved)
            assertEquals(testServerId, (event as NameYourDeviceNavigationEvent.DeviceNameSaved).serverId)
            assertFalse(event.hasPlainTextAccess)

            coVerify(exactly = 1) {
                integrationRepository.setAllowInsecureConnection(false)
                serverManager.convertTemporaryServer(tempServerId)
            }
        }
    }

    @Test
    fun `Given serverManager addServer throws when onSaveClick then emits Error event and attempts no cleanup`() = runTest {
        coEvery { serverManager.addServer(any()) } throws RuntimeException("Failed to add server")

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)

            viewModel.onSaveClick()
            advanceUntilIdle()

            assertError(navEvents.awaitItem(), commonR.string.webview_error)

            coVerify(exactly = 0) {
                authRepository.registerAuthorizationCode(any())
                integrationRepository.registerDevice(any())
                serverManager.convertTemporaryServer(any())
                authRepository.revokeSession()
                serverManager.removeServer(any())
            }
        }
    }

    @Test
    fun `Given authRepository Throws when onSaveClick then emits Error event and attempts cleanup`() = runTest {
        val tempServerId = 0
        coEvery { serverManager.addServer(any()) } returns tempServerId
        coEvery { authRepository.registerAuthorizationCode(route.authCode) } throws RuntimeException("Auth code registration failed")
        coEvery { authRepository.revokeSession() } just Runs
        coEvery { serverManager.removeServer(tempServerId) } just Runs

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)

            viewModel.onSaveClick()
            advanceUntilIdle()

            assertError(navEvents.awaitItem(), commonR.string.webview_error)

            coVerify(exactly = 1) {
                serverManager.addServer(any())
                authRepository.registerAuthorizationCode(route.authCode)
                authRepository.revokeSession()
                serverManager.removeServer(tempServerId)
            }
            coVerify(exactly = 0) {
                integrationRepository.registerDevice(any())
                serverManager.convertTemporaryServer(any())
            }
        }
    }

    @Test
    fun `Given integrationRepository registerDevice throws when onSaveClick then emits error event and attempts cleanup`() = runTest {
        val tempServerId = 0
        coEvery { serverManager.addServer(any()) } returns tempServerId
        coEvery { authRepository.registerAuthorizationCode(route.authCode) } just Runs
        coEvery { integrationRepository.registerDevice(any()) } throws RuntimeException("Device registration failed")
        coEvery { authRepository.revokeSession() } just Runs
        coEvery { serverManager.removeServer(tempServerId) } just Runs

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)

            viewModel.onSaveClick()
            advanceUntilIdle()

            assertError(navEvents.awaitItem(), commonR.string.webview_error)
            coVerify(exactly = 1) {
                serverManager.addServer(any())
                authRepository.registerAuthorizationCode(route.authCode)
                integrationRepository.registerDevice(any())
                authRepository.revokeSession()
                serverManager.removeServer(tempServerId)
            }
            coVerify(exactly = 0) { serverManager.convertTemporaryServer(any()) }
        }
    }

    @Test
    fun `Given serverManager convertTemporaryServer throws when onSaveClick then emits error event and attempts cleanup`() = runTest {
        val tempServerId = 0
        coEvery { serverManager.addServer(any()) } returns tempServerId
        coEvery { authRepository.registerAuthorizationCode(route.authCode) } just Runs
        coEvery { integrationRepository.registerDevice(any()) } just Runs
        coEvery { serverManager.convertTemporaryServer(tempServerId) } throws IllegalStateException("Server still temporary")
        coEvery { authRepository.revokeSession() } just Runs
        coEvery { serverManager.removeServer(tempServerId) } just Runs

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)

            viewModel.onSaveClick()
            advanceUntilIdle()

            assertError(navEvents.awaitItem(), commonR.string.webview_error)
            coVerify(exactly = 1) {
                serverManager.addServer(any())
                authRepository.registerAuthorizationCode(route.authCode)
                integrationRepository.registerDevice(any())
                serverManager.convertTemporaryServer(tempServerId)
                authRepository.revokeSession()
                serverManager.removeServer(tempServerId)
            }
        }
    }

    @Test
    fun `Given registerAuthCode throws HttpException 404 when onSaveClick then emits error with error_with_registration message`() = runTest {
        val tempServerId = 0
        coEvery { serverManager.addServer(any()) } returns tempServerId
        coEvery { authRepository.registerAuthorizationCode(route.authCode) } throws HttpException(Response.error<Any>(404, mockk(relaxed = true)))
        coEvery { authRepository.revokeSession() } just Runs
        coEvery { serverManager.removeServer(tempServerId) } just Runs

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)
            viewModel.onSaveClick()
            advanceUntilIdle()
            assertError(navEvents.awaitItem(), commonR.string.error_with_registration)
        }
    }

    @Test
    fun `Given registerAuthCode throws SSLHandshakeException when onSaveClick then emits error with webview_error_FAILED_SSL_HANDSHAKE message`() = runTest {
        val tempServerId = 0
        val exception = SSLHandshakeException("SSL handshake failed")
        coEvery { serverManager.addServer(any()) } returns tempServerId
        coEvery { authRepository.registerAuthorizationCode(route.authCode) } throws exception
        coEvery { authRepository.revokeSession() } just Runs
        coEvery { serverManager.removeServer(tempServerId) } just Runs

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)
            viewModel.onSaveClick()
            advanceUntilIdle()
            assertError(navEvents.awaitItem(), commonR.string.webview_error_FAILED_SSL_HANDSHAKE)
        }
    }

    @Test
    fun `Given registerAuthCode throws SSLException when onSaveClick then emits error with webview_error_SSL_INVALID message`() = runTest {
        val tempServerId = 0
        coEvery { serverManager.addServer(any()) } returns tempServerId
        coEvery { authRepository.registerAuthorizationCode(route.authCode) } throws SSLException("SSL invalid")
        coEvery { authRepository.revokeSession() } just Runs
        coEvery { serverManager.removeServer(tempServerId) } just Runs

        turbineScope {
            val navEvents = viewModel.navigationEventsFlow.testIn(backgroundScope)
            viewModel.onSaveClick()
            advanceUntilIdle()
            assertError(navEvents.awaitItem(), commonR.string.webview_error_SSL_INVALID)
        }
    }

    @Test
    fun `Given adding server when onSaveClick then isSavingFlow is properly updated`() = runTest {
        viewModel.isSavingFlow.test {
            assertFalse(awaitItem())
            coEvery { serverManager.addServer(any()) } coAnswers {
                assertTrue(awaitItem())
                throw RuntimeException("Failed to add server")
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
            coEvery { serverManager.addServer(any()) } coAnswers {
                assertFalse(awaitItem())
                throw RuntimeException("Failed to add server")
            }
            viewModel.onSaveClick()
            advanceUntilIdle()
            assertTrue(awaitItem())
            coVerify { serverManager.addServer(any()) }
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
