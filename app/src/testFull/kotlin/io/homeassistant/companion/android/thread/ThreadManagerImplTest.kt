package io.homeassistant.companion.android.thread

import android.content.IntentSender
import android.os.Build
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.threadnetwork.IntentSenderResult
import com.google.android.gms.threadnetwork.IsPreferredCredentialsResult
import com.google.android.gms.threadnetwork.ThreadNetworkClient
import com.google.android.gms.threadnetwork.ThreadNetworkCredentials
import com.google.android.gms.threadnetwork.ThreadNetworkStatusCodes
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.WebSocketRepository
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.server.Server
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadManagerImplTest {

    private val serverManager: ServerManager = mockk(relaxed = true)
    private val threadNetworkClient: ThreadNetworkClient = mockk(relaxed = true)
    private val webSocketRepository: WebSocketRepository = mockk(relaxed = true)

    private fun createManager(
        isAutomotive: Boolean = false,
        sdkInt: Int = Build.VERSION_CODES.O_MR1,
    ): ThreadManagerImpl {
        SdkVersion.sdkInt = sdkInt
        return ThreadManagerImpl(
            serverManager = serverManager,
            isAutomotive = isAutomotive,
            threadNetworkClient = threadNetworkClient,
        )
    }

    @Test
    fun `Given SDK before O_MR1 when appSupportsThread then returns false`() {
        assertFalse(createManager(sdkInt = Build.VERSION_CODES.O).appSupportsThread())
    }

    @Test
    fun `Given automotive device when appSupportsThread then returns false`() {
        assertFalse(createManager(isAutomotive = true).appSupportsThread())
    }

    @Test
    fun `Given SDK O_MR1 plus and not automotive when appSupportsThread then returns true`() {
        assertTrue(createManager().appSupportsThread())
    }

    @Test
    fun `Given not registered when coreSupportsThread then returns false`() = runTest {
        coEvery { serverManager.isRegistered() } returns false
        assertFalse(createManager().coreSupportsThread(serverId = 1))
    }

    @Test
    fun `Given non-admin user when coreSupportsThread then returns false`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer(1) } returns mockServer(isAdmin = false)
        assertFalse(createManager().coreSupportsThread(serverId = 1))
    }

    @Test
    fun `Given server before 2023_3 when coreSupportsThread then returns false`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer(1) } returns mockServer(isAdmin = true)
        coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
        coEvery { webSocketRepository.getConfig() } returns mockk(relaxed = true) {
            every { components } returns listOf("thread")
            every { version } returns "2023.2.5"
        }

        assertFalse(createManager().coreSupportsThread(serverId = 1))
    }

    @Test
    fun `Given server 2023_3 plus with thread component when coreSupportsThread then returns true`() = runTest {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer(1) } returns mockServer(isAdmin = true)
        coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
        coEvery { webSocketRepository.getConfig() } returns mockk(relaxed = true) {
            every { components } returns listOf("thread", "light")
            every { version } returns "2024.1.0"
        }

        assertTrue(createManager().coreSupportsThread(serverId = 1))
    }

    @Test
    fun `Given app unsupported when export then returns AppUnsupported`() = runTest {
        val result = createManager(isAutomotive = true).exportThreadCredentials(serverId = 1)

        assertInstanceOf(ThreadManager.SyncResult.AppUnsupported::class.java, result)
    }

    @Test
    fun `Given server unsupported when export then returns ServerUnsupported`() = runTest {
        stubServerSupport(coreSupports = false)

        val result = createManager().exportThreadCredentials(serverId = 1)

        assertInstanceOf(ThreadManager.SyncResult.ServerUnsupported::class.java, result)
    }

    @Test
    fun `Given device has no dataset when export then returns NoneHaveCredentials`() = runTest {
        stubServerSupport(coreSupports = true)
        stubPreferredCredentials(intentSender = null)

        val result = createManager().exportThreadCredentials(serverId = 1)

        assertInstanceOf(ThreadManager.SyncResult.NoneHaveCredentials::class.java, result)
    }

    @Test
    fun `Given device dataset is app-preferred when export then returns OnlyOnServer not imported`() = runTest {
        stubServerSupport(coreSupports = true)
        stubPreferredCredentials(intentSender = mockk())
        val appCredentials: ThreadNetworkCredentials = mockk(relaxed = true)
        every { threadNetworkClient.allCredentials } returns successTask(listOf(appCredentials))
        every { threadNetworkClient.isPreferredCredentials(appCredentials) } returns
            successTask(IsPreferredCredentialsResult.PREFERRED_CREDENTIALS_MATCHED)

        val result = assertInstanceOf(
            ThreadManager.SyncResult.OnlyOnServer::class.java,
            createManager().exportThreadCredentials(serverId = 1),
        )
        assertFalse(result.imported)
    }

    @Test
    fun `Given device has non-app-preferred dataset when export then returns OnlyOnDevice with intent`() = runTest {
        stubServerSupport(coreSupports = true)
        val deviceIntent: IntentSender = mockk()
        stubPreferredCredentials(intentSender = deviceIntent)
        every { threadNetworkClient.allCredentials } returns successTask(emptyList())

        val result = assertInstanceOf(
            ThreadManager.SyncResult.OnlyOnDevice::class.java,
            createManager().exportThreadCredentials(serverId = 1),
        )
        assertEquals(deviceIntent, result.exportIntent)
    }

    @Test
    fun `Given LOCAL_NETWORK_NOT_CONNECTED ApiException when export then returns NotConnected`() = runTest {
        stubServerSupport(coreSupports = true)
        val status = mockk<Status>(relaxed = true) {
            every { statusCode } returns ThreadNetworkStatusCodes.LOCAL_NETWORK_NOT_CONNECTED
        }
        every { threadNetworkClient.preferredCredentials } returns failureTask(ApiException(status))

        val result = createManager().exportThreadCredentials(serverId = 1)

        assertInstanceOf(ThreadManager.SyncResult.NotConnected::class.java, result)
    }

    @Test
    fun `Given generic exception when export then exception propagates`() = runTest {
        stubServerSupport(coreSupports = true)
        every { threadNetworkClient.preferredCredentials } returns failureTask(IllegalStateException("boom"))

        try {
            createManager().exportThreadCredentials(serverId = 1)
            fail { "Should have failed" }
        } catch (_: IllegalStateException) {
        }
    }

    /**
     * Common stub for the support gates used by both `exportThreadCredentials` and
     * `coreSupportsThread`. Configures a registered admin user on a server that runs HA 2024.1
     * and exposes the "thread" component.
     */
    private fun stubServerSupport(coreSupports: Boolean) {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer(1) } returns mockServer(isAdmin = true)
        coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
        coEvery { webSocketRepository.getConfig() } returns mockk(relaxed = true) {
            every { components } returns if (coreSupports) listOf("thread") else listOf("light")
            every { version } returns "2024.1.0"
        }
    }

    private fun stubPreferredCredentials(intentSender: IntentSender?) {
        val result: IntentSenderResult = mockk(relaxed = true) {
            every { this@mockk.intentSender } returns intentSender
        }
        every { threadNetworkClient.preferredCredentials } returns successTask(result)
    }

    private fun mockServer(isAdmin: Boolean): Server = mockk(relaxed = true) {
        every { user } returns mockk(relaxed = true) {
            every { this@mockk.isAdmin } returns isAdmin
        }
    }

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
