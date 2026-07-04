package io.homeassistant.companion.android.frontend.improv

import android.os.Build
import app.cash.turbine.test
import com.wifi.improv.DeviceState
import com.wifi.improv.ErrorState
import com.wifi.improv.ImprovDevice
import com.wifi.improv.ImprovManager
import io.homeassistant.companion.android.common.util.PermissionChecker
import io.homeassistant.companion.android.common.util.SdkVersion
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ImprovRepositoryImplTest {
    private val improvManager: ImprovManager = mockk(relaxed = true)
    private val improvManagerFactory: ImprovManagerFactory = ImprovManagerFactory { improvManager }
    private val permissionChecker: PermissionChecker = mockk()

    private fun TestScope.createRepository(
        sdkInt: Int = Build.VERSION_CODES.S,
        scope: CoroutineScope = backgroundScope,
        ioDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    ): ImprovRepositoryImpl {
        SdkVersion.sdkInt = sdkInt
        return ImprovRepositoryImpl(
            permissionChecker = permissionChecker,
            improvManagerFactory = improvManagerFactory,
            shareInScope = scope,
            backgroundDispatcher = ioDispatcher,
        )
    }

    @Nested
    inner class RequiredPermissions {

        @Test
        fun `Given SDK 31 plus when requiredPermissions then includes new BLUETOOTH permissions plus location`() = runTest {
            val repository = createRepository(sdkInt = Build.VERSION_CODES.S)

            assertEquals(
                listOf(
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.BLUETOOTH_SCAN",
                    "android.permission.BLUETOOTH_CONNECT",
                ),
                repository.requiredPermissions,
            )
        }

        @Test
        fun `Given SDK below 31 when requiredPermissions then includes legacy BLUETOOTH permissions plus location`() = runTest {
            val repository = createRepository(sdkInt = Build.VERSION_CODES.R)

            assertEquals(
                listOf(
                    "android.permission.ACCESS_FINE_LOCATION",
                    "android.permission.BLUETOOTH",
                    "android.permission.BLUETOOTH_ADMIN",
                ),
                repository.requiredPermissions,
            )
        }
    }

    @Nested
    inner class HasPermissions {

        @Test
        fun `Given all permissions granted when hasPermissions then returns true`() = runTest {
            every { permissionChecker.hasPermission(any()) } returns true
            val repository = createRepository()

            assertTrue(repository.hasPermissions())
        }

        @Test
        fun `Given one permission missing when hasPermissions then returns false`() = runTest {
            every { permissionChecker.hasPermission(any()) } returns true
            every { permissionChecker.hasPermission("android.permission.BLUETOOTH_SCAN") } returns false
            val repository = createRepository()

            assertFalse(repository.hasPermissions())
        }
    }

    @Nested
    inner class ScanDevices {

        @Test
        fun `Given permissions granted when scanDevices subscribed then findDevices is called`() = runTest {
            every { permissionChecker.hasPermission(any()) } returns true
            val repository = createRepository()

            repository.scanDevices().test {
                assertEquals(emptyList<ImprovDevice>(), awaitItem())
                verify { improvManager.findDevices() }
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given permissions missing when scanDevices subscribed then findDevices is not called`() = runTest {
            every { permissionChecker.hasPermission(any()) } returns false
            val repository = createRepository()

            repository.scanDevices().test {
                assertEquals(emptyList<ImprovDevice>(), awaitItem())
                verify(exactly = 0) { improvManager.findDevices() }
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given findDevices throws SecurityException when subscribed then exception is swallowed`() = runTest {
            every { permissionChecker.hasPermission(any()) } returns true
            every { improvManager.findDevices() } throws SecurityException("denied")
            val repository = createRepository()

            repository.scanDevices().test {
                assertEquals(emptyList<ImprovDevice>(), awaitItem())
                verify { improvManager.findDevices() }
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given findDevices throws generic Exception when subscribed then exception is swallowed`() = runTest {
            every { permissionChecker.hasPermission(any()) } returns true
            every { improvManager.findDevices() } throws IllegalStateException("bluetooth off")
            val repository = createRepository()

            repository.scanDevices().test {
                assertEquals(emptyList<ImprovDevice>(), awaitItem())
                verify { improvManager.findDevices() }
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given scanDevices subscription cancelled then stopScan is called after idle window`() = runTest {
            every { permissionChecker.hasPermission(any()) } returns true
            val repository = createRepository()

            repository.scanDevices().test {
                assertEquals(emptyList<ImprovDevice>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            advanceTimeBy(SCAN_IDLE_WINDOW_MS + 1)
            verify { improvManager.stopScan() }
        }
    }

    @Nested
    inner class OnDeviceFound {

        @Test
        fun `Given same device found twice when scanDevices collected then list contains it once`() = runTest {
            every { permissionChecker.hasPermission(any()) } returns true
            val repository = createRepository()
            val device = ImprovDevice("Smart Plug", "AA:BB")

            repository.scanDevices().test {
                assertEquals(emptyList<ImprovDevice>(), awaitItem())
                repository.onDeviceFound(device)
                assertEquals(listOf(device), awaitItem())
                repository.onDeviceFound(device)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given different devices found when scanDevices collected then list grows`() = runTest {
            every { permissionChecker.hasPermission(any()) } returns true
            val repository = createRepository()
            val first = ImprovDevice("Smart Plug", "AA:BB")
            val second = ImprovDevice("Lamp", "CC:DD")

            repository.scanDevices().test {
                assertEquals(emptyList<ImprovDevice>(), awaitItem())
                repository.onDeviceFound(first)
                assertEquals(listOf(first), awaitItem())
                repository.onDeviceFound(second)
                assertEquals(listOf(first, second), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class DeviceListReset {

        @Test
        fun `Given device discovered when onStateChange PROVISIONED then device list is cleared`() = runTest {
            every { permissionChecker.hasPermission(any()) } returns true
            val repository = createRepository()

            repository.scanDevices().test {
                assertEquals(emptyList<ImprovDevice>(), awaitItem())
                repository.onDeviceFound(ImprovDevice("Smart Plug", "AA:BB"))
                awaitItem()
                repository.onStateChange(DeviceState.PROVISIONED)
                assertEquals(emptyList<ImprovDevice>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class ProvisionDevice {

        @Test
        fun `Given provisionDevice when collected then connectToDevice is called`() = runTest {
            val repository = createRepository()
            val device = ImprovDevice("Smart Plug", "AA:BB")

            repository.provisionDevice(device, "wifi", "pwd").test {
                repository.onStateChange(DeviceState.AUTHORIZATION_REQUIRED)
                awaitItem()
                verify { improvManager.connectToDevice(device) }
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given AUTHORIZED state when provisioning then sendWifi is called exactly once`() = runTest {
            val repository = createRepository()

            repository.provisionDevice(ImprovDevice("d", "AA"), "wifi", "pwd").test {
                repository.onStateChange(DeviceState.AUTHORIZED)
                awaitItem() // StateChanged(AUTHORIZED) — sendWifi has been invoked by now
                repository.onStateChange(DeviceState.AUTHORIZED)
                awaitItem() // second StateChanged — guarded against re-send
                verify(exactly = 1) { improvManager.sendWifi("wifi", "pwd") }
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given PROVISIONED state with RPC result when provisioning then emits Provisioned with parsed domain and completes`() = runTest {
            val repository = createRepository()

            repository.provisionDevice(ImprovDevice("d", "AA"), "wifi", "pwd").test {
                repository.onRpcResult(listOf("https://example.com/?domain=esphome"))
                repository.onStateChange(DeviceState.PROVISIONED)

                assertInstanceOf(ProvisioningEvent.StateChanged::class.java, awaitItem())
                val provisioned = awaitItem() as ProvisioningEvent.Provisioned
                assertEquals("esphome", provisioned.domain)
                awaitComplete()
            }
        }

        @Test
        fun `Given PROVISIONED state without RPC result when provisioning then domain is null`() = runTest {
            val repository = createRepository()

            repository.provisionDevice(ImprovDevice("d", "AA"), "wifi", "pwd").test {
                repository.onStateChange(DeviceState.PROVISIONED)

                assertInstanceOf(ProvisioningEvent.StateChanged::class.java, awaitItem())
                val provisioned = awaitItem() as ProvisioningEvent.Provisioned
                assertNull(provisioned.domain)
                awaitComplete()
            }
        }

        @Test
        fun `Given onConnectionStateChange null then RPC result is cleared`() = runTest {
            val repository = createRepository()

            // Seed a result, then disconnect, then expect Provisioned's domain to be null.
            repository.onRpcResult(listOf("https://example.com/?domain=stale"))
            repository.onConnectionStateChange(null)

            repository.provisionDevice(ImprovDevice("d", "AA"), "wifi", "pwd").test {
                repository.onStateChange(DeviceState.PROVISIONED)
                awaitItem() // StateChanged
                val provisioned = awaitItem() as ProvisioningEvent.Provisioned
                assertNull(provisioned.domain)
                awaitComplete()
            }
        }

        @Test
        fun `Given error state when provisioning then emits ErrorOccurred`() = runTest {
            val repository = createRepository()

            repository.provisionDevice(ImprovDevice("d", "AA"), "wifi", "pwd").test {
                repository.onErrorStateChange(ErrorState.UNABLE_TO_CONNECT)
                val event = awaitItem() as ProvisioningEvent.ErrorOccurred
                assertEquals(ErrorState.UNABLE_TO_CONNECT, event.error)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given NO_ERROR state when provisioning then no ErrorOccurred emitted`() = runTest {
            val repository = createRepository()

            repository.provisionDevice(ImprovDevice("d", "AA"), "wifi", "pwd").test {
                repository.onErrorStateChange(ErrorState.NO_ERROR)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `Given connectToDevice throws SecurityException when provisioning then flow closes with exception`() = runTest {
            every { improvManager.connectToDevice(any()) } throws SecurityException("denied")
            val repository = createRepository()

            repository.provisionDevice(ImprovDevice("d", "AA"), "wifi", "pwd").test {
                val error = awaitError()
                assertInstanceOf(SecurityException::class.java, error)
                assertEquals("denied", error.message)
            }
        }

        @Test
        fun `Given sendWifi throws SecurityException when provisioning then flow closes with exception`() = runTest {
            every { improvManager.sendWifi(any(), any()) } throws SecurityException("wifi-denied")
            val repository = createRepository()

            repository.provisionDevice(ImprovDevice("d", "AA"), "wifi", "pwd").test {
                repository.onStateChange(DeviceState.AUTHORIZED)
                // StateChanged(AUTHORIZED) is emitted before sendWifi() runs — drain it first.
                awaitItem()
                val error = awaitError()
                assertInstanceOf(SecurityException::class.java, error)
                assertEquals("wifi-denied", error.message)
            }
        }
    }
}
