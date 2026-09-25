package io.homeassistant.companion.android.matter

import android.app.Activity
import android.content.ComponentName
import android.content.IntentSender
import android.os.Build
import android.os.SystemClock
import androidx.activity.result.ActivityResult
import com.google.android.gms.common.moduleinstall.ModuleAvailabilityResponse
import com.google.android.gms.common.moduleinstall.ModuleInstallClient
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallResponse
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState
import com.google.android.gms.home.matter.commissioning.CommissioningClient
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.ShareDeviceRequest
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
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.concurrent.Executor
import java.util.concurrent.TimeoutException
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
    private val moduleInstallClient: ModuleInstallClient = mockk(relaxed = true) {
        every { areModulesAvailable(any()) } returns successTask(moduleAvailability(available = true))
    }
    private val commissioningServiceComponent: ComponentName = mockk(relaxed = true)
    private val webSocketRepository: WebSocketRepository = mockk(relaxed = true)

    @AfterEach
    fun tearDown() {
        SdkVersion.resetSdkInt()
        unmockkStatic(SystemClock::class)
    }

    private fun givenElapsedRealtime(millis: Long) {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns millis
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
            moduleInstallClient = moduleInstallClient,
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
    @Test
    fun `Given Play Services succeeds when prepareDeviceSharing then emits Ready with a request built from the payload`() = runTest {
        val intentSender: IntentSender = mockk()
        val request = slot<ShareDeviceRequest>()
        every { commissioningClient.shareDevice(capture(request)) } returns successTask(intentSender)
        givenElapsedRealtime(1_000L)
        val manager = createManager()

        val event = assertInstanceOf(
            MatterManager.CommissioningResult.Ready::class.java,
            manager.prepareDeviceSharing(SHARE_REQUEST),
        )

        assertEquals(intentSender, event.intentSender)
        assertEquals("Kitchen light", request.captured.deviceName)
        assertEquals(SHARE_REQUEST.passcode, request.captured.commissioningWindow.passcode)
        assertEquals(SHARE_REQUEST.discriminator, request.captured.commissioningWindow.discriminator.value)
        assertEquals(1_000L, request.captured.commissioningWindow.windowOpenMillis)
        assertEquals(250L, request.captured.commissioningWindow.durationSeconds)
        assertEquals(SHARE_REQUEST.vendorId, request.captured.deviceDescriptor.vendorId)
    }

    @Test
    fun `Given Play Services fails when prepareDeviceSharing then emits Error with cause`() = runTest {
        val cause = IllegalStateException("play services unavailable")
        every { commissioningClient.shareDevice(any()) } returns failureTask(cause)
        givenElapsedRealtime(1_000L)
        val manager = createManager()

        val event = assertInstanceOf(
            MatterManager.CommissioningResult.Error::class.java,
            manager.prepareDeviceSharing(SHARE_REQUEST.copy(deviceName = null)),
        )
        assertEquals(cause, event.cause)
    }

    @Test
    fun `Given Matter module installed when prepareDeviceSharing then does not request an install`() = runTest {
        every { commissioningClient.shareDevice(any()) } returns successTask(mockk())
        givenElapsedRealtime(1_000L)

        createManager().prepareDeviceSharing(SHARE_REQUEST)

        verify(exactly = 0) { moduleInstallClient.installModules(any()) }
    }

    @Test
    fun `Given Matter module missing when prepareDeviceSharing then installs it before sharing`() = runTest {
        val intentSender: IntentSender = mockk()
        every { moduleInstallClient.areModulesAvailable(any()) } returns
            successTask(moduleAvailability(available = false))
        val installRequest = slot<ModuleInstallRequest>()
        every { moduleInstallClient.installModules(capture(installRequest)) } answers {
            installRequest.captured.listener!!.onInstallStatusUpdated(installUpdate(InstallState.STATE_COMPLETED))
            successTask(ModuleInstallResponse(1, false))
        }
        every { commissioningClient.shareDevice(any()) } returns successTask(intentSender)
        givenElapsedRealtime(1_000L)

        val event = createManager().prepareDeviceSharing(SHARE_REQUEST)

        assertEquals(listOf(commissioningClient), installRequest.captured.apis)
        assertEquals(intentSender, assertInstanceOf(MatterManager.CommissioningResult.Ready::class.java, event).intentSender)
        verify { moduleInstallClient.unregisterListener(installRequest.captured.listener!!) }
    }

    @Test
    fun `Given Matter module install fails when prepareDeviceSharing then still asks Play Services`() = runTest {
        val cause = IllegalStateException("API unavailable")
        every { moduleInstallClient.areModulesAvailable(any()) } returns
            successTask(moduleAvailability(available = false))
        every { moduleInstallClient.installModules(any()) } returns failureTask(IllegalStateException("no network"))
        every { commissioningClient.shareDevice(any()) } returns failureTask(cause)
        givenElapsedRealtime(1_000L)

        val event = createManager().prepareDeviceSharing(SHARE_REQUEST)

        assertEquals(cause, assertInstanceOf(MatterManager.CommissioningResult.Error::class.java, event).cause)
    }

    @Test
    fun `Given Play Services never answers when prepareDeviceSharing then emits Error after the timeout`() = runTest {
        every { commissioningClient.shareDevice(any()) } returns pendingTask()
        givenElapsedRealtime(1_000L)

        val event = createManager().prepareDeviceSharing(SHARE_REQUEST)

        assertInstanceOf(
            TimeoutException::class.java,
            assertInstanceOf(MatterManager.CommissioningResult.Error::class.java, event).cause,
        )
    }

    @Test
    fun `Given automotive device when prepareDeviceSharing then emits Error without calling Play Services`() = runTest {
        val manager = createManager(isAutomotive = true)

        assertFalse(manager.appSupportsSharing())
        assertInstanceOf(
            MatterManager.CommissioningResult.Error::class.java,
            manager.prepareDeviceSharing(SHARE_REQUEST.copy(deviceName = null)),
        )
        verify(exactly = 0) { commissioningClient.shareDevice(any()) }
    }

    @Test
    fun `Given share activity results when parseSharingIntentResult then maps OK, cancel and other codes`() {
        val manager = createManager()

        assertEquals(
            MatterManager.SharingRequestResult.Shared,
            manager.parseSharingIntentResult(ActivityResult(Activity.RESULT_OK, null)),
        )
        assertEquals(
            MatterManager.SharingRequestResult.Cancelled,
            manager.parseSharingIntentResult(ActivityResult(Activity.RESULT_CANCELED, null)),
        )
        assertEquals(
            MatterManager.SharingRequestResult.Failed,
            manager.parseSharingIntentResult(ActivityResult(Activity.RESULT_FIRST_USER, null)),
        )
    }

    @Test
    fun `Given no remaining time when prepareDeviceSharing then assumes the default window`() = runTest {
        val request = slot<ShareDeviceRequest>()
        every { commissioningClient.shareDevice(capture(request)) } returns successTask(mockk())
        givenElapsedRealtime(1_000L)
        val manager = createManager()

        manager.prepareDeviceSharing(SHARE_REQUEST.copy(remainingSeconds = null))

        assertEquals(300L, request.captured.commissioningWindow.durationSeconds)
    }

    private fun moduleAvailability(available: Boolean): ModuleAvailabilityResponse = mockk {
        every { areModulesAvailable() } returns available
    }

    private fun installUpdate(state: Int): ModuleInstallStatusUpdate = mockk {
        every { installState } returns state
    }

    // Completed tasks answer both the listener style and kotlinx `await()`, which reads the state directly.
    private fun <T> successTask(result: T): Task<T> = mockk {
        every { isComplete } returns true
        every { isCanceled } returns false
        every { exception } returns null
        every { this@mockk.result } returns result
        every { addOnSuccessListener(any()) } answers {
            firstArg<OnSuccessListener<T>>().onSuccess(result)
            this@mockk
        }
        every { addOnFailureListener(any()) } returns this@mockk
    }

    private fun <T> pendingTask(): Task<T> = mockk {
        every { isComplete } returns false
        every { addOnCompleteListener(any<Executor>(), any()) } returns this@mockk
        every { addOnSuccessListener(any()) } returns this@mockk
        every { addOnFailureListener(any()) } returns this@mockk
    }

    private fun <T> failureTask(cause: Exception): Task<T> = mockk {
        every { isComplete } returns true
        every { isCanceled } returns false
        every { exception } returns cause
        every { addOnSuccessListener(any()) } returns this@mockk
        every { addOnFailureListener(any()) } answers {
            firstArg<OnFailureListener>().onFailure(cause)
            this@mockk
        }
    }
}

private val SHARE_REQUEST = MatterShareRequest(
    passcode = 20202021,
    discriminator = 3840,
    vendorId = 0xFFF1,
    productId = 0x8001,
    deviceName = "Kitchen light",
    remainingSeconds = 250,
)
