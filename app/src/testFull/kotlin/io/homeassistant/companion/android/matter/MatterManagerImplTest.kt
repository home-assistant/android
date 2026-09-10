package io.homeassistant.companion.android.matter

import android.content.ComponentName
import android.content.IntentSender
import android.os.Build
import com.google.android.gms.home.matter.commissioning.CommissioningClient
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.data.websocket.impl.entities.MatterCommissionResponse
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.server.Server
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatterManagerImplTest {

    private val serverManager: ServerManager = mockk(relaxed = true)
    private val commissioningClient: CommissioningClient = mockk(relaxed = true)
    private val commissioningServiceComponent: ComponentName = mockk(relaxed = true)
    private val webSocketRepository: WebSocketRepository = mockk(relaxed = true)

    @AfterEach
    fun tearDown() {
        SdkVersion.resetSdkInt()
    }

    private fun createManager(
        isAutomotive: Boolean = false,
        sdkInt: Int = Build.VERSION_CODES.O_MR1,
    ): MatterManagerImpl {
        SdkVersion.sdkInt = sdkInt
        return MatterManagerImpl(
            serverManager = serverManager,
            isAutomotive = isAutomotive,
            commissioningClient = commissioningClient,
            commissioningServiceComponent = commissioningServiceComponent,
        )
    }

    @Test
    fun `Given SDK before O_MR1 when appSupportsCommissioning then returns false`() {
        val manager = createManager(sdkInt = Build.VERSION_CODES.O)

        assertFalse(manager.appSupportsCommissioning())
    }

    @Test
    fun `Given automotive device when appSupportsCommissioning then returns false`() {
        val manager = createManager(isAutomotive = true, sdkInt = Build.VERSION_CODES.O_MR1)

        assertFalse(manager.appSupportsCommissioning())
    }

    @Test
    fun `Given SDK O_MR1 plus and not automotive when appSupportsCommissioning then returns true`() {
        val manager = createManager(isAutomotive = false, sdkInt = Build.VERSION_CODES.O_MR1)

        assertTrue(manager.appSupportsCommissioning())
    }

    @Test
    fun `Given not registered when coreSupportsCommissioning then returns false`() = runTest {
        coEvery { serverManager.isRegistered() } returns false
        val manager = createManager()

        assertFalse(manager.coreSupportsCommissioning(serverId = 1))
    }

    @Test
    fun `Given non-admin user when coreSupportsCommissioning then returns false`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer(1) } returns mockServer(isAdmin = false)
        val manager = createManager()

        assertFalse(manager.coreSupportsCommissioning(serverId = 1))
    }

    @Test
    fun `Given config without Matter component when coreSupportsCommissioning then returns false`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer(1) } returns mockServer(isAdmin = true)
        coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
        coEvery { webSocketRepository.getConfig() } returns mockk(relaxed = true) {
            every { components } returns listOf("light", "switch")
        }
        val manager = createManager()

        assertFalse(manager.coreSupportsCommissioning(serverId = 1))
    }

    @Test
    fun `Given config with Matter component when coreSupportsCommissioning then returns true`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer(1) } returns mockServer(isAdmin = true)
        coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
        coEvery { webSocketRepository.getConfig() } returns mockk(relaxed = true) {
            every { components } returns listOf("matter", "light")
        }
        val manager = createManager()

        assertTrue(manager.coreSupportsCommissioning(serverId = 1))
    }

    @Test
    fun `Given getConfig throws when coreSupportsCommissioning then returns false`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer(1) } returns mockServer(isAdmin = true)
        coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
        coEvery { webSocketRepository.getConfig() } throws IllegalStateException("network")
        val manager = createManager()

        assertFalse(manager.coreSupportsCommissioning(serverId = 1))
    }

    @Test
    fun `Given unsupported SDK when commissionMatterDevice then emits Error`() = runTest {
        val manager = createManager(sdkInt = Build.VERSION_CODES.O)

        val event = manager.prepareMatterDeviceCommissioning()

        assertInstanceOf(MatterManager.CommissioningResult.Error::class.java, event)
    }

    @Test
    fun `Given Play Services succeeds when commissionMatterDevice then emits Ready with intent sender`() = runTest {
        val intentSender: IntentSender = mockk()
        every { commissioningClient.commissionDevice(any<CommissioningRequest>()) } returns successTask(intentSender)
        val manager = createManager()

        val event = assertInstanceOf(
            MatterManager.CommissioningResult.Ready::class.java,
            manager.prepareMatterDeviceCommissioning(),
        )
        assertEquals(intentSender, event.intentSender)
    }

    @Test
    fun `Given Play Services fails when commissionMatterDevice then emits Error with cause`() = runTest {
        val cause = IllegalStateException("play services unavailable")
        every { commissioningClient.commissionDevice(any<CommissioningRequest>()) } returns failureTask(cause)
        val manager = createManager()

        val event = assertInstanceOf(
            MatterManager.CommissioningResult.Error::class.java,
            manager.prepareMatterDeviceCommissioning(),
        )
        assertEquals(cause, event.cause)
    }

    @Test
    fun `Given supported when suppressDiscoveryBottomSheet then calls suppressHalfSheetNotification`() {
        val manager = createManager()

        manager.suppressDiscoveryBottomSheet()

        verify { commissioningClient.suppressHalfSheetNotification() }
    }

    @Test
    fun `Given unsupported when suppressDiscoveryBottomSheet then no-op`() {
        val manager = createManager(sdkInt = Build.VERSION_CODES.O)

        manager.suppressDiscoveryBottomSheet()

        verify(exactly = 0) { commissioningClient.suppressHalfSheetNotification() }
    }

    @Test
    fun `Given server accepts code when commissionDevice then returns response`() = runTest {
        val response: MatterCommissionResponse = mockk()
        coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
        coEvery { webSocketRepository.commissionMatterDevice("abc-123") } returns response
        val manager = createManager()

        assertEquals(response, manager.commissionDevice(code = "abc-123", serverId = 1))
    }

    @Test
    fun `Given server throws when commissionDevice then returns null and swallows exception`() = runTest {
        coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
        coEvery { webSocketRepository.commissionMatterDevice(any()) } throws IllegalStateException("boom")
        val manager = createManager()

        assertNull(manager.commissionDevice(code = "abc-123", serverId = 1))
    }

    @Test
    fun `Given server accepts pin and ip when commissionOnNetworkDevice then returns response`() = runTest {
        val response: MatterCommissionResponse = mockk()
        coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
        coEvery { webSocketRepository.commissionMatterDeviceOnNetwork(1234L, "2001:db8:0:85a3::ac1f:8001") } returns
            response
        val manager = createManager()

        assertEquals(
            response,
            manager.commissionOnNetworkDevice(pin = 1234L, ip = "2001:db8:0:85a3::ac1f:8001", serverId = 1),
        )
    }

    @Test
    fun `Given server throws when commissionOnNetworkDevice then returns null and swallows exception`() = runTest {
        coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
        coEvery { webSocketRepository.commissionMatterDeviceOnNetwork(any(), any()) } throws
            IllegalStateException("boom")
        val manager = createManager()

        assertNull(manager.commissionOnNetworkDevice(pin = 1234L, ip = "2001:db8:0:85a3::ac1f:8001", serverId = 1))
    }

    private fun mockServer(isAdmin: Boolean): Server = mockk(relaxed = true) {
        every { user } returns mockk(relaxed = true) {
            every { this@mockk.isAdmin } returns isAdmin
        }
    }

    /**
     * Builds a Play Services [Task] mock that invokes its success listener synchronously when
     * registered, mirroring how the real Task fires when the result is already available.
     */
    private fun <T> successTask(result: T): Task<T> = mockk {
        every { addOnSuccessListener(any()) } answers {
            firstArg<OnSuccessListener<T>>().onSuccess(result)
            this@mockk
        }
        every { addOnFailureListener(any()) } returns this@mockk
    }

    private fun <T> failureTask(cause: Exception): Task<T> = mockk {
        every { addOnSuccessListener(any()) } returns this@mockk
        every { addOnFailureListener(any()) } answers {
            firstArg<OnFailureListener>().onFailure(cause)
            this@mockk
        }
    }
}
