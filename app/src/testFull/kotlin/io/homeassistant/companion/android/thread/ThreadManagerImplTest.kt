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
import io.homeassistant.companion.android.common.data.websocket.impl.entities.ThreadDatasetResponse
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.server.Server
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.fail

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadManagerImplTest {

    private val serverManager: ServerManager = mockk(relaxed = true)
    private val threadNetworkClient: ThreadNetworkClient = mockk(relaxed = true)
    private val webSocketRepository: WebSocketRepository = mockk(relaxed = true)

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

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
        val result = createManager(isAutomotive = true).exportPreferredDataset(serverId = 1)

        assertInstanceOf(ThreadManager.SyncResult.AppUnsupported::class.java, result)
    }

    @Test
    fun `Given server unsupported when export then returns ServerUnsupported`() = runTest {
        stubServerSupport(coreSupports = false)

        val result = createManager().exportPreferredDataset(serverId = 1)

        assertInstanceOf(ThreadManager.SyncResult.ServerUnsupported::class.java, result)
    }

    @Test
    fun `Given device has no dataset when export then returns NoneHaveCredentials`() = runTest {
        stubServerSupport(coreSupports = true)
        mockPreferredCredentials(intentSender = null)

        val result = createManager().exportPreferredDataset(serverId = 1)

        assertInstanceOf(ThreadManager.SyncResult.NoneHaveCredentials::class.java, result)
    }

    @Test
    fun `Given device dataset is app-preferred when export then returns OnlyOnServer not imported`() = runTest {
        stubServerSupport(coreSupports = true)
        mockPreferredCredentials(intentSender = mockk())
        val appCredentials: ThreadNetworkCredentials = mockk(relaxed = true)
        every { threadNetworkClient.allCredentials } returns successTask(listOf(appCredentials))
        every { threadNetworkClient.isPreferredCredentials(appCredentials) } returns
            successTask(IsPreferredCredentialsResult.PREFERRED_CREDENTIALS_MATCHED)

        val result = assertInstanceOf(
            ThreadManager.SyncResult.OnlyOnServer::class.java,
            createManager().exportPreferredDataset(serverId = 1),
        )
        assertFalse(result.imported)
    }

    @Test
    fun `Given device has non-app-preferred dataset when export then returns OnlyOnDevice with intent`() = runTest {
        stubServerSupport(coreSupports = true)
        val deviceIntent: IntentSender = mockk()
        mockPreferredCredentials(intentSender = deviceIntent)
        every { threadNetworkClient.allCredentials } returns successTask(emptyList())

        val result = assertInstanceOf(
            ThreadManager.SyncResult.OnlyOnDevice::class.java,
            createManager().exportPreferredDataset(serverId = 1),
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

        val result = createManager().exportPreferredDataset(serverId = 1)

        assertInstanceOf(ThreadManager.SyncResult.NotConnected::class.java, result)
    }

    @Test
    fun `Given generic exception when export then exception propagates`() = runTest {
        stubServerSupport(coreSupports = true)
        every { threadNetworkClient.preferredCredentials } returns failureTask(IllegalStateException("boom"))

        try {
            createManager().exportPreferredDataset(serverId = 1)
            fail { "Should have failed" }
        } catch (_: IllegalStateException) {
        }
    }

    @Test
    fun `Given server has a preferred dataset when getPreferredDatasetFromServer then returns it`() = runTest {
        coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
        val preferred = dataset(datasetId = "preferred-id", preferred = true)
        coEvery { webSocketRepository.getThreadDatasets() } returns listOf(
            dataset(datasetId = "other-id", preferred = false),
            preferred,
        )

        assertEquals(preferred, createManager().getPreferredDatasetFromServer(serverId = 1))
    }

    @Test
    fun `Given server datasets but none preferred when getPreferredDatasetFromServer then returns null`() = runTest {
        coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
        coEvery { webSocketRepository.getThreadDatasets() } returns listOf(
            dataset(datasetId = "a", preferred = false),
            dataset(datasetId = "b", preferred = false),
        )

        assertNull(createManager().getPreferredDatasetFromServer(serverId = 1))
    }

    @Test
    fun `Given server returns no datasets when getPreferredDatasetFromServer then returns null`() = runTest {
        coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
        coEvery { webSocketRepository.getThreadDatasets() } returns null

        assertNull(createManager().getPreferredDatasetFromServer(serverId = 1))
    }

    @Nested
    inner class SyncPreferredDatasetTests {

        @Test
        fun `Given app unsupported when sync then returns AppUnsupported`() = runTest {
            val result = createManager(isAutomotive = true).syncPreferredDataset(serverId = 1, scope = this)

            assertInstanceOf(ThreadManager.SyncResult.AppUnsupported::class.java, result)
        }

        @Test
        fun `Given server unsupported when sync then returns ServerUnsupported`() = runTest {
            stubServerSupport(coreSupports = false)

            val result = createManager().syncPreferredDataset(serverId = 1, scope = this)

            assertInstanceOf(ThreadManager.SyncResult.ServerUnsupported::class.java, result)
        }

        @Test
        fun `Given neither device nor server has a dataset when sync then returns NoneHaveCredentials`() = runTest {
            stubServerSupport(coreSupports = true)
            mockOrphanCleanup()
            mockPreferredCredentials(intentSender = null)
            coEvery { webSocketRepository.getThreadDatasets() } returns emptyList()

            val result = createManager().syncPreferredDataset(serverId = 1, scope = this)

            assertInstanceOf(ThreadManager.SyncResult.NoneHaveCredentials::class.java, result)
        }

        @Test
        fun `Given only the server has a dataset and import succeeds when sync then returns OnlyOnServer imported`() =
            runTest {
                stubServerSupport(coreSupports = true)
                mockOrphanCleanup()
                mockPreferredCredentials(intentSender = null)
                coEvery { webSocketRepository.getThreadDatasets() } returns
                    listOf(dataset(datasetId = "server", preferred = true))
                // No TLV on the server, so the import is a no-op that completes successfully
                coEvery { webSocketRepository.getThreadDatasetTlv(any()) } returns null

                val result = assertInstanceOf(
                    ThreadManager.SyncResult.OnlyOnServer::class.java,
                    createManager().syncPreferredDataset(serverId = 1, scope = this),
                )
                assertTrue(result.imported)
            }

        @Test
        fun `Given only the server has a dataset and import fails when sync then returns OnlyOnServer not imported`() =
            runTest {
                stubServerSupport(coreSupports = true)
                mockOrphanCleanup()
                mockPreferredCredentials(intentSender = null)
                coEvery { webSocketRepository.getThreadDatasets() } returns
                    listOf(dataset(datasetId = "server", preferred = true))
                coEvery { webSocketRepository.getThreadDatasetTlv(any()) } throws RuntimeException("import boom")

                val result = assertInstanceOf(
                    ThreadManager.SyncResult.OnlyOnServer::class.java,
                    createManager().syncPreferredDataset(serverId = 1, scope = this),
                )
                assertFalse(result.imported)
            }

        @Test
        fun `Given only the device has a dataset when sync then returns OnlyOnDevice with intent`() = runTest {
            stubServerSupport(coreSupports = true)
            mockOrphanCleanup()
            val deviceIntent: IntentSender = mockk()
            mockPreferredCredentials(intentSender = deviceIntent)
            coEvery { webSocketRepository.getThreadDatasets() } returns emptyList()

            val result = assertInstanceOf(
                ThreadManager.SyncResult.OnlyOnDevice::class.java,
                createManager().syncPreferredDataset(serverId = 1, scope = this),
            )
            assertEquals(deviceIntent, result.exportIntent)
        }

        @Test
        fun `Given both have datasets and neither is device-preferred when sync then exports from device`() = runTest {
            stubServerSupport(coreSupports = true)
            mockOrphanCleanup()
            val deviceIntent: IntentSender = mockk()
            mockPreferredCredentials(intentSender = deviceIntent)
            coEvery { webSocketRepository.getThreadDatasets() } returns
                listOf(dataset(datasetId = "server", preferred = true))
            every { threadNetworkClient.allCredentials } returns successTask(emptyList())
            // No TLV on the server, so the device is found to prefer neither credential
            coEvery { webSocketRepository.getThreadDatasetTlv(any()) } returns null

            val result = assertInstanceOf(
                ThreadManager.SyncResult.AllHaveCredentials::class.java,
                createManager().syncPreferredDataset(serverId = 1, scope = this),
            )
            assertEquals(false, result.matches)
            assertEquals(false, result.fromApp)
            assertNull(result.updated)
            assertEquals(deviceIntent, result.exportIntent)
        }

        @Test
        fun `Given both have datasets and the comparison fails when sync then returns AllHaveCredentials with nulls`() =
            runTest {
                stubServerSupport(coreSupports = true)
                mockOrphanCleanup()
                mockPreferredCredentials(intentSender = mockk())
                coEvery { webSocketRepository.getThreadDatasets() } returns
                    listOf(dataset(datasetId = "server", preferred = true))
                coEvery { webSocketRepository.getThreadDatasetTlv(any()) } throws RuntimeException("compare boom")

                val result = assertInstanceOf(
                    ThreadManager.SyncResult.AllHaveCredentials::class.java,
                    createManager().syncPreferredDataset(serverId = 1, scope = this),
                )
                assertNull(result.matches)
                assertNull(result.fromApp)
                assertNull(result.updated)
                assertNull(result.exportIntent)
            }

        @Test
        fun `Given the device already prefers the server dataset when sync then matches without exporting`() = runTest {
            stubServerSupport(coreSupports = true)
            mockOrphanCleanup()
            mockPreferredCredentials(intentSender = mockk())
            coEvery { webSocketRepository.getThreadDatasets() } returns
                listOf(dataset(datasetId = "server", preferred = true))
            // The server TLV maps (statically) to credentials the device reports as its preferred ones
            coEvery { webSocketRepository.getThreadDatasetTlv(any()) } returns mockk {
                every { tlvAsByteArray } returns byteArrayOf(1, 2, 3)
            }
            val serverCredentials: ThreadNetworkCredentials = mockk(relaxed = true)
            mockkStatic(ThreadNetworkCredentials::class)
            every { ThreadNetworkCredentials.fromActiveOperationalDataset(any()) } returns serverCredentials
            every { threadNetworkClient.isPreferredCredentials(serverCredentials) } returns
                successTask(IsPreferredCredentialsResult.PREFERRED_CREDENTIALS_MATCHED)

            val result = assertInstanceOf(
                ThreadManager.SyncResult.AllHaveCredentials::class.java,
                createManager().syncPreferredDataset(serverId = 1, scope = this),
            )
            assertEquals(true, result.matches)
            assertEquals(true, result.fromApp)
            assertNull(result.updated)
            assertNull(result.exportIntent)
        }

        @Test
        fun `Given the device prefers an app-added dataset when sync then updates the device to the server dataset`() =
            runTest {
                stubServerSupport(coreSupports = true)
                mockOrphanCleanup()
                mockPreferredCredentials(intentSender = mockk())
                coEvery { webSocketRepository.getThreadDatasets() } returns
                    listOf(dataset(datasetId = "server", preferred = true))
                // No TLV, so the device does not prefer the server dataset directly...
                coEvery { webSocketRepository.getThreadDatasetTlv(any()) } returns null
                // ...but it does prefer a credential this app added previously
                mockDeviceCredentialPrefersApp()

                val result = assertInstanceOf(
                    ThreadManager.SyncResult.AllHaveCredentials::class.java,
                    createManager().syncPreferredDataset(serverId = 1, scope = this),
                )
                assertEquals(false, result.matches)
                assertEquals(true, result.fromApp)
                assertEquals(true, result.updated)
                assertNull(result.exportIntent)
            }

        @Test
        fun `Given a foreign-sourced server dataset preferred via an app credential when sync then removes it`() =
            runTest {
                stubServerSupport(coreSupports = true)
                mockOrphanCleanup()
                mockPreferredCredentials(intentSender = mockk())
                // The server's preferred dataset reports it originated from another app (not HA), so HA
                // should stop managing it: remove its own contributed credential without re-importing.
                coEvery { webSocketRepository.getThreadDatasets() } returns
                    listOf(dataset(datasetId = "server", preferred = true, source = "Google"))
                // No TLV, so the device does not prefer the server dataset directly...
                coEvery { webSocketRepository.getThreadDatasetTlv(any()) } returns null
                // ...but it does prefer a credential this app added previously, which is what lets HA
                // remove it. A credential added by another app could not be removed here (see the
                // "neither is device-preferred" test, which exports instead of removing).
                mockDeviceCredentialPrefersApp()

                val result = assertInstanceOf(
                    ThreadManager.SyncResult.AllHaveCredentials::class.java,
                    createManager().syncPreferredDataset(serverId = 1, scope = this),
                )
                assertEquals(false, result.matches)
                assertEquals(true, result.fromApp)
                assertEquals(false, result.updated)
                assertNull(result.exportIntent)
            }
    }

    private fun dataset(datasetId: String, preferred: Boolean, source: String = "HomeAssistant") =
        ThreadDatasetResponse(
            datasetId = datasetId,
            extendedPanId = "extended-pan-id",
            networkName = "network-$datasetId",
            panId = "pan-id",
            preferred = preferred,
            source = source,
        )

    private fun stubServerSupport(coreSupports: Boolean) {
        coEvery { serverManager.isRegistered() } returns true
        coEvery { serverManager.getServer(1) } returns mockServer(isAdmin = true)
        coEvery { serverManager.webSocketRepository(1) } returns webSocketRepository
        coEvery { webSocketRepository.getConfig() } returns mockk(relaxed = true) {
            every { components } returns if (coreSupports) listOf("thread") else listOf("light")
            every { version } returns "2024.1.0"
        }
    }

    private fun mockOrphanCleanup() {
        every { threadNetworkClient.removeCredentials(any()) } returns voidSuccessTask()
    }

    private fun mockDeviceCredentialPrefersApp() {
        val appCredentials: ThreadNetworkCredentials = mockk(relaxed = true)
        every { threadNetworkClient.allCredentials } returns successTask(listOf(appCredentials))
        every { threadNetworkClient.isPreferredCredentials(appCredentials) } returns
            successTask(IsPreferredCredentialsResult.PREFERRED_CREDENTIALS_MATCHED)
    }

    private fun mockPreferredCredentials(intentSender: IntentSender?) {
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

    private fun voidSuccessTask(): Task<Void> = mockk {
        every { addOnSuccessListener(any()) } answers {
            firstArg<OnSuccessListener<Void>>().onSuccess(null)
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
