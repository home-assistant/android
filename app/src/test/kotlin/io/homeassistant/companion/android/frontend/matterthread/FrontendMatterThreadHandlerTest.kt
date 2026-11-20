package io.homeassistant.companion.android.frontend.matterthread

import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import androidx.activity.result.ActivityResult
import app.cash.turbine.test
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.frontend.dialog.ConfirmButtons
import io.homeassistant.companion.android.frontend.dialog.FrontendDialogManager
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.incoming.MatterShareDevicePayload
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ErrorResultMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.MatterCommissionFinishMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.OutgoingExternalBusMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.SuccessResultMessage
import io.homeassistant.companion.android.matter.MatterManager
import io.homeassistant.companion.android.thread.ThreadManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

private const val SHARE_MESSAGE_ID = 42
private val SHARE_PAYLOAD = MatterShareDevicePayload(
    passcode = 20202021,
    discriminator = 3840,
    remainingSeconds = 250,
    vendorId = 0xFFF1,
    productId = 0x8000,
    deviceName = "Kitchen light",
)

@OptIn(ExperimentalCoroutinesApi::class)
class FrontendMatterThreadHandlerTest {

    private val matterManager: MatterManager = mockk(relaxed = true)
    private val threadManager: ThreadManager = mockk(relaxed = true)
    private val dialogManager: FrontendDialogManager = mockk(relaxed = true) {
        // Default: progress dialog suspends forever (caller must cancel to dismiss).
        coEvery { showMatterThreadProgress() } coAnswers { awaitCancellation() }
    }
    private val externalBusRepository: FrontendExternalBusRepository = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk(relaxed = true)

    private fun createHandler() = FrontendMatterThreadHandler(
        matterManager = matterManager,
        threadManager = threadManager,
        dialogManager = dialogManager,
        externalBusRepository = externalBusRepository,
        serverManager = serverManager,
    )

    @BeforeEach
    fun setUp() {
        // Uri.parse is a stub returning null in plain JVM tests; the handler uses it for the docs link.
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk()
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    private fun givenServerVersion(version: HomeAssistantVersion?) {
        val server: Server = mockk {
            every { this@mockk.version } returns version
        }
        coEvery { serverManager.getServer() } returns server
    }

    @Test
    fun `Given Matter Ready result when onStartMatterCommissioning then sends LaunchIntent`() = runTest {
        val intent: IntentSender = mockk()
        coEvery { matterManager.prepareMatterDeviceCommissioning() } returns MatterManager.CommissioningResult.Ready(intent)
        val handler = createHandler()

        handler.events.test {
            launch { handler.onStartMatterCommissioning() }
            val event =
                assertInstanceOf(FrontendMatterThreadHandler.Event.LaunchIntent::class.java, awaitItem())
            assertEquals(intent, event.intentSender)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given LaunchIntent sent before collecting when collecting then it is delivered once`() = runTest {
        val intent: IntentSender = mockk()
        coEvery { matterManager.prepareMatterDeviceCommissioning() } returns MatterManager.CommissioningResult.Ready(intent)
        val handler = createHandler()

        handler.onStartMatterCommissioning()

        handler.events.test {
            val event = assertInstanceOf(FrontendMatterThreadHandler.Event.LaunchIntent::class.java, awaitItem())
            assertEquals(intent, event.intentSender)
        }
        handler.events.test {
            expectNoEvents()
        }
    }

    @Test
    fun `Given Matter Error result when onStartMatterCommissioning then sends MatterError snackbar event`() = runTest {
        coEvery { matterManager.prepareMatterDeviceCommissioning() } returns
            MatterManager.CommissioningResult.Error(IllegalStateException("nope"))
        val handler = createHandler()

        handler.events.test {
            launch { handler.onStartMatterCommissioning() }
            val event = assertInstanceOf(FrontendMatterThreadHandler.Event.ShowSnackbar::class.java, awaitItem())
            assertEquals(
                MatterThreadTerminal.Snackbar.MatterError,
                event.snackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given commissionMatterDevice throws when onStartMatterCommissioning then sends MatterError snackbar event`() = runTest {
        coEvery { matterManager.prepareMatterDeviceCommissioning() } throws IllegalStateException("boom")
        val handler = createHandler()

        handler.events.test {
            launch { handler.onStartMatterCommissioning() }
            val event = assertInstanceOf(FrontendMatterThreadHandler.Event.ShowSnackbar::class.java, awaitItem())
            assertEquals(
                MatterThreadTerminal.Snackbar.MatterError,
                event.snackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given Matter is in-flight and commissioning succeeded when onMatterThreadIntentResult then reports success with device name`() = runTest {
        coEvery { matterManager.prepareMatterDeviceCommissioning() } returns MatterManager.CommissioningResult.Ready(mockk())
        every { matterManager.parseCommissioningIntentResult(any()) } returns
            MatterManager.CommissioningRequestResult.Success(deviceName = "Kitchen light")
        val handler = createHandler()

        handler.events.test {
            // Starting the flow asks the screen to launch the Play Services intent first
            handler.onStartMatterCommissioning()
            assertInstanceOf(FrontendMatterThreadHandler.Event.LaunchIntent::class.java, awaitItem())

            handler.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_OK, null))
            expectNoEvents()
        }
        coVerify { externalBusRepository.send(MatterCommissionFinishMessage(name = "Kitchen light", success = true)) }
        coVerify(exactly = 0) { dialogManager.showMatterThreadTerminal(any()) }
    }

    @Test
    fun `Given Matter is in-flight and commissioning failed on old server when onMatterThreadIntentResult then reports failure and sends MatterCancelled snackbar`() = runTest {
        coEvery { matterManager.prepareMatterDeviceCommissioning() } returns MatterManager.CommissioningResult.Ready(mockk())
        every { matterManager.parseCommissioningIntentResult(any()) } returns MatterManager.CommissioningRequestResult.Failed
        givenServerVersion(HomeAssistantVersion(2026, 6, 0))
        val handler = createHandler()

        handler.events.test {
            // Starting the flow asks the screen to launch the Play Services intent first
            handler.onStartMatterCommissioning()
            assertInstanceOf(FrontendMatterThreadHandler.Event.LaunchIntent::class.java, awaitItem())

            launch { handler.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_CANCELED, null)) }
            val event = assertInstanceOf(FrontendMatterThreadHandler.Event.ShowSnackbar::class.java, awaitItem())

            assertEquals(
                MatterThreadTerminal.Snackbar.MatterCancelled,
                event.snackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { externalBusRepository.send(MatterCommissionFinishMessage(name = null, success = false)) }
    }

    @Test
    fun `Given Matter is in-flight and commissioning failed on 2026_7 server when onMatterThreadIntentResult then reports failure without snackbar`() = runTest {
        coEvery { matterManager.prepareMatterDeviceCommissioning() } returns MatterManager.CommissioningResult.Ready(mockk())
        every { matterManager.parseCommissioningIntentResult(any()) } returns MatterManager.CommissioningRequestResult.Failed
        givenServerVersion(HomeAssistantVersion(2026, 7, 0))
        val handler = createHandler()

        handler.events.test {
            // Starting the flow asks the screen to launch the Play Services intent first
            handler.onStartMatterCommissioning()
            assertInstanceOf(FrontendMatterThreadHandler.Event.LaunchIntent::class.java, awaitItem())

            handler.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_CANCELED, null))
            expectNoEvents()
        }
        coVerify { externalBusRepository.send(MatterCommissionFinishMessage(name = null, success = false)) }
        coVerify(exactly = 0) { dialogManager.showMatterThreadTerminal(any()) }
    }

    @Test
    fun `Given nothing in-flight when onMatterThreadIntentResult then result is ignored`() = runTest {
        val handler = createHandler()

        handler.events.test {
            handler.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_CANCELED, null))
            expectNoEvents()
        }
        coVerify(exactly = 0) { dialogManager.showMatterThreadTerminal(any()) }
    }

    @Test
    fun `Given Thread Ready result when onImportThreadCredentials then sends LaunchIntent`() = runTest {
        val intent: IntentSender = mockk()
        coEvery { threadManager.exportPreferredDataset(any()) } returns ThreadManager.SyncResult.OnlyOnDevice(exportIntent = intent)
        val handler = createHandler()

        handler.events.test {
            launch { handler.onImportThreadCredentials(serverId = 1) }
            val event = assertInstanceOf(FrontendMatterThreadHandler.Event.LaunchIntent::class.java, awaitItem())
            assertEquals(intent, event.intentSender)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given Thread NoDataset result when onImportThreadCredentials then shows ThreadNoDataset dialog`() = runTest {
        coEvery { threadManager.exportPreferredDataset(any()) } returns ThreadManager.SyncResult.NoneHaveCredentials
        val handler = createHandler()

        handler.onImportThreadCredentials(serverId = 1)

        coVerify { dialogManager.showMatterThreadTerminal(MatterThreadTerminal.Dialog.ThreadNoDataset) }
    }

    @Test
    fun `Given Thread NotConnected result when onImportThreadCredentials then shows ThreadNotConnected dialog`() = runTest {
        coEvery { threadManager.exportPreferredDataset(any()) } returns ThreadManager.SyncResult.NotConnected
        val handler = createHandler()

        handler.onImportThreadCredentials(serverId = 1)

        coVerify { dialogManager.showMatterThreadTerminal(MatterThreadTerminal.Dialog.ThreadNotConnected) }
    }

    @Test
    fun `Given Thread AppUnsupported result when onImportThreadCredentials then sends ThreadError snackbar event`() = runTest {
        coEvery { threadManager.exportPreferredDataset(any()) } returns ThreadManager.SyncResult.AppUnsupported
        val handler = createHandler()

        handler.events.test {
            launch { handler.onImportThreadCredentials(serverId = 1) }
            val event = assertInstanceOf(FrontendMatterThreadHandler.Event.ShowSnackbar::class.java, awaitItem())

            assertEquals(
                MatterThreadTerminal.Snackbar.ThreadError,
                event.snackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given exportThreadCredentials throws when onImportThreadCredentials then sends ThreadError snackbar event`() = runTest {
        coEvery { threadManager.exportPreferredDataset(any()) } throws IllegalStateException("boom")
        val handler = createHandler()

        handler.events.test {
            launch { handler.onImportThreadCredentials(serverId = 1) }
            val event = assertInstanceOf(FrontendMatterThreadHandler.Event.ShowSnackbar::class.java, awaitItem())

            assertEquals(
                MatterThreadTerminal.Snackbar.ThreadError,
                event.snackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given onImportThreadCredentials when started then shows progress dialog`() = runTest {
        // Hold the call open so we can verify the progress dialog is shown before the result arrives.
        coEvery { threadManager.exportPreferredDataset(any()) } coAnswers { awaitCancellation() }
        val handler = createHandler()

        val job = launch { handler.onImportThreadCredentials(serverId = 1) }
        advanceUntilIdle()

        coVerify { dialogManager.showMatterThreadProgress() }
        job.cancel()
    }

    @Test
    fun `Given Thread is in-flight and sendThreadDatasetExportResult returns name when onMatterThreadIntentResult then sends ThreadSuccess snackbar`() = runTest {
        coEvery { threadManager.exportPreferredDataset(any()) } returns ThreadManager.SyncResult.OnlyOnDevice(exportIntent = mockk())
        coEvery { threadManager.sendThreadDatasetExportResult(any(), any()) } returns "My Thread Network"
        val handler = createHandler()

        handler.events.test {
            // Starting the flow asks the screen to launch the Play Services intent first
            handler.onImportThreadCredentials(serverId = 42)
            assertInstanceOf(FrontendMatterThreadHandler.Event.LaunchIntent::class.java, awaitItem())

            launch { handler.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_OK, null)) }
            val event = assertInstanceOf(FrontendMatterThreadHandler.Event.ShowSnackbar::class.java, awaitItem())

            assertEquals(
                MatterThreadTerminal.Snackbar.ThreadSuccess,
                event.snackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
        // serverId from the in-flight ref is forwarded to the manager — no extra plumbing needed.
        coVerify { threadManager.sendThreadDatasetExportResult(any(), serverId = 42) }
    }

    @Test
    fun `Given Thread is in-flight and sendThreadDatasetExportResult returns null when onMatterThreadIntentResult then shows ThreadNoDataset dialog`() = runTest {
        coEvery { threadManager.exportPreferredDataset(any()) } returns ThreadManager.SyncResult.OnlyOnDevice(exportIntent = mockk())
        coEvery { threadManager.sendThreadDatasetExportResult(any(), any()) } returns null
        val handler = createHandler()
        handler.onImportThreadCredentials(serverId = 1)

        handler.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_OK, null))

        coVerify { dialogManager.showMatterThreadTerminal(MatterThreadTerminal.Dialog.ThreadNoDataset) }
    }

    @Test
    fun `Given Thread is in-flight and sendThreadDatasetExportResult throws when onMatterThreadIntentResult then sends ThreadError snackbar`() = runTest {
        coEvery { threadManager.exportPreferredDataset(any()) } returns ThreadManager.SyncResult.OnlyOnDevice(exportIntent = mockk())
        coEvery { threadManager.sendThreadDatasetExportResult(any(), any()) } throws IllegalStateException("boom")
        val handler = createHandler()

        handler.events.test {
            // Starting the flow asks the screen to launch the Play Services intent first
            handler.onImportThreadCredentials(serverId = 1)
            assertInstanceOf(FrontendMatterThreadHandler.Event.LaunchIntent::class.java, awaitItem())

            launch { handler.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_OK, null)) }
            val event = assertInstanceOf(FrontendMatterThreadHandler.Event.ShowSnackbar::class.java, awaitItem())

            assertEquals(
                MatterThreadTerminal.Snackbar.ThreadError,
                event.snackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given a flow already in-flight when another start request arrives then the second is dropped`() = runTest {
        coEvery { matterManager.prepareMatterDeviceCommissioning() } coAnswers { awaitCancellation() }
        val handler = createHandler()

        val firstJob = launch { handler.onStartMatterCommissioning() }
        advanceUntilIdle()
        handler.onImportThreadCredentials(serverId = 1)

        coVerify(exactly = 0) { threadManager.exportPreferredDataset(any()) }
        firstJob.cancel()
    }

    @Nested
    inner class MatterSharingTest {

        @Test
        fun `Given sharing Ready result when onStartMatterSharing then emits LaunchIntent for the payload`() = runTest {
            val intent: IntentSender = mockk()
            coEvery { matterManager.prepareDeviceSharing(any()) } returns MatterManager.CommissioningResult.Ready(intent)
            val handler = createHandler()

            handler.events.test {
                launch { handler.onStartMatterSharing(SHARE_MESSAGE_ID, SHARE_PAYLOAD) }
                val event =
                    assertInstanceOf(FrontendMatterThreadHandler.Event.LaunchIntent::class.java, awaitItem())
                assertEquals(intent, event.intentSender)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { matterManager.prepareDeviceSharing(SHARE_PAYLOAD) }
        }

        @Test
        fun `Given no message id when onStartMatterSharing then nothing happens`() = runTest {
            val handler = createHandler()

            handler.onStartMatterSharing(null, SHARE_PAYLOAD)

            coVerify(exactly = 0) { matterManager.prepareDeviceSharing(any()) }
            coVerify(exactly = 0) { externalBusRepository.send(any()) }
            // Nothing was claimed, so the next request still gets through.
            handler.onStartMatterSharing(SHARE_MESSAGE_ID, SHARE_PAYLOAD)
            coVerify { matterManager.prepareDeviceSharing(any()) }
        }

        @Test
        fun `Given another flow in flight when onStartMatterSharing then replies failed and leaves that flow alone`() = runTest {
            coEvery { matterManager.prepareMatterDeviceCommissioning() } returns MatterManager.CommissioningResult.Ready(mockk())
            every { matterManager.parseCommissioningIntentResult(any()) } returns
                MatterManager.CommissioningRequestResult.Success(deviceName = "Kitchen light")
            val handler = createHandler()
            handler.onStartMatterCommissioning()

            handler.onStartMatterSharing(SHARE_MESSAGE_ID, SHARE_PAYLOAD)

            coVerify(exactly = 0) { matterManager.prepareDeviceSharing(any()) }
            coVerify {
                externalBusRepository.send(
                    ErrorResultMessage(id = SHARE_MESSAGE_ID, code = "failed", message = "Another flow is in progress"),
                )
            }
            handler.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_OK, null))
            coVerify { externalBusRepository.send(MatterCommissionFinishMessage(name = "Kitchen light", success = true)) }
        }

        @Test
        fun `Given sharing Error result when onStartMatterSharing then replies failed and releases`() = runTest {
            coEvery { matterManager.prepareDeviceSharing(any()) } returns
                MatterManager.CommissioningResult.Error(IllegalStateException("nope"))
            val handler = createHandler()

            handler.onStartMatterSharing(SHARE_MESSAGE_ID, SHARE_PAYLOAD)

            coVerify {
                externalBusRepository.send(
                    ErrorResultMessage(id = SHARE_MESSAGE_ID, code = "failed", message = "Sharing could not be prepared"),
                )
            }
            handler.onStartMatterSharing(SHARE_MESSAGE_ID, SHARE_PAYLOAD)
            coVerify(exactly = 2) { matterManager.prepareDeviceSharing(any()) }
        }

        @Test
        fun `Given prepareDeviceSharing throws when onStartMatterSharing then replies failed and releases`() = runTest {
            coEvery { matterManager.prepareDeviceSharing(any()) } throws IllegalStateException("nope")
            val handler = createHandler()

            handler.onStartMatterSharing(SHARE_MESSAGE_ID, SHARE_PAYLOAD)

            coVerify {
                externalBusRepository.send(
                    ErrorResultMessage(id = SHARE_MESSAGE_ID, code = "failed", message = "Sharing could not be prepared"),
                )
            }
            handler.onStartMatterSharing(SHARE_MESSAGE_ID, SHARE_PAYLOAD)
            coVerify(exactly = 2) { matterManager.prepareDeviceSharing(any()) }
        }

        @ParameterizedTest
        @MethodSource(
            "io.homeassistant.companion.android.frontend.matterthread.FrontendMatterThreadHandlerTest#shareOutcomes",
        )
        fun `Given sharing is in-flight when onMatterThreadIntentResult then replies with the outcome`(
            outcome: MatterManager.SharingRequestResult,
            reply: OutgoingExternalBusMessage,
        ) = runTest {
            givenSharingInFlight(outcome)
            val handler = createHandler()
            handler.onStartMatterSharing(SHARE_MESSAGE_ID, SHARE_PAYLOAD)

            handler.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_OK, null))

            coVerify { externalBusRepository.send(reply) }
        }

        private fun givenSharingInFlight(outcome: MatterManager.SharingRequestResult) {
            coEvery { matterManager.prepareDeviceSharing(any()) } returns MatterManager.CommissioningResult.Ready(mockk())
            every { matterManager.parseSharingIntentResult(any()) } returns outcome
        }
    }

    @Nested
    inner class StoreThreadCredentials {

        private val tlv = byteArrayOf(1, 2, 3)
        private val borderAgentId = "0123456789abcdef0123456789abcdef"

        private fun givenStoreReady(outcome: ThreadManager.PreflightOutcome) {
            every { threadManager.appSupportsThread() } returns true
            every { threadManager.networkNameFromTlv(tlv) } returns "MyNetwork"
            coEvery { threadManager.predictPreferredOutcome(tlv) } returns outcome
        }

        private suspend fun store(handler: FrontendMatterThreadHandler = createHandler()) {
            handler.onStoreThreadCredentialsInPlatformKeychain(serverId = 1, borderAgentId = borderAgentId, tlv = tlv)
        }

        @Test
        fun `Given app without Thread support when storing then shows unsupported and does not add`() = runTest {
            every { threadManager.appSupportsThread() } returns false

            store()

            coVerify {
                dialogManager.showInformation(commonR.string.thread_store_unsupported, moreInfoUrl = any())
            }
            coVerify(exactly = 0) { threadManager.addCredentialToDevice(any(), any(), any()) }
        }

        @Test
        fun `Given credential already preferred when storing then informs and does not add`() = runTest {
            givenStoreReady(ThreadManager.PreflightOutcome.AlreadyPreferred)

            store()

            coVerify {
                dialogManager.showInformation(
                    commonR.string.thread_store_already_preferred,
                    "MyNetwork",
                    moreInfoUrl = any(),
                )
            }
            coVerify(exactly = 0) { threadManager.addCredentialToDevice(any(), any(), any()) }
        }

        @Test
        fun `Given user declines the preflight confirmation when storing then does not add`() = runTest {
            givenStoreReady(ThreadManager.PreflightOutcome.LikelyToBecomePreferred)
            coEvery {
                dialogManager.showConfirm(commonR.string.thread_store_likely_preferred, "MyNetwork", moreInfoUrl = any(), buttons = ConfirmButtons.YesNo)
            } returns false

            store()

            coVerify(exactly = 0) { threadManager.addCredentialToDevice(any(), any(), any()) }
        }

        @Test
        fun `Given user confirms and credential becomes preferred when storing then reports preferred`() = runTest {
            givenStoreReady(ThreadManager.PreflightOutcome.LikelyToBecomePreferred)
            coEvery {
                dialogManager.showConfirm(commonR.string.thread_store_likely_preferred, "MyNetwork", moreInfoUrl = any(), buttons = ConfirmButtons.YesNo)
            } returns true
            coEvery { threadManager.addCredentialToDevice(1, tlv, borderAgentId) } returns
                ThreadManager.AddCredentialResult.Preferred

            store()

            coVerify {
                dialogManager.showInformation(
                    commonR.string.thread_store_added_preferred,
                    "MyNetwork",
                    moreInfoUrl = any(),
                )
            }
        }

        @Test
        fun `Given adding the credential fails when storing then reports the failure`() = runTest {
            givenStoreReady(ThreadManager.PreflightOutcome.Unknown)
            coEvery {
                dialogManager.showConfirm(commonR.string.thread_store_unknown_preferred, "MyNetwork", moreInfoUrl = any(), buttons = ConfirmButtons.YesNo)
            } returns true
            coEvery { threadManager.addCredentialToDevice(1, tlv, borderAgentId) } throws
                IllegalStateException("add boom")

            store()

            coVerify {
                dialogManager.showInformation(
                    commonR.string.thread_store_failed,
                    "MyNetwork",
                    "add boom",
                    moreInfoUrl = any(),
                )
            }
        }

        @Test
        fun `Given adding the credential fails without a message when storing then reports the exception type`() = runTest {
            givenStoreReady(ThreadManager.PreflightOutcome.Unknown)
            coEvery {
                dialogManager.showConfirm(commonR.string.thread_store_unknown_preferred, "MyNetwork", moreInfoUrl = any(), buttons = ConfirmButtons.YesNo)
            } returns true
            coEvery { threadManager.addCredentialToDevice(1, tlv, borderAgentId) } throws IllegalStateException()

            store()

            coVerify {
                dialogManager.showInformation(
                    commonR.string.thread_store_failed,
                    "MyNetwork",
                    "IllegalStateException",
                    moreInfoUrl = any(),
                )
            }
        }

        @Test
        fun `Given the preflight check throws when storing then reports the failure with the network name`() = runTest {
            every { threadManager.appSupportsThread() } returns true
            every { threadManager.networkNameFromTlv(tlv) } returns "MyNetwork"
            coEvery { threadManager.predictPreferredOutcome(tlv) } throws IllegalStateException("preflight boom")

            store()

            coVerify {
                dialogManager.showInformation(
                    commonR.string.thread_store_failed,
                    "MyNetwork",
                    "preflight boom",
                    moreInfoUrl = any(),
                )
            }
            coVerify(exactly = 0) { threadManager.addCredentialToDevice(any(), any(), any()) }
        }

        @Test
        fun `Given an unexpected error and an unreadable network name when storing then reports an invalid payload`() = runTest {
            every { threadManager.appSupportsThread() } throws IllegalStateException("boom")
            every { threadManager.networkNameFromTlv(tlv) } returns null

            store()

            coVerify {
                dialogManager.showInformation(commonR.string.thread_store_invalid_payload, moreInfoUrl = any())
            }
        }

        @Test
        fun `Given store flow awaiting confirmation when a stale intent result arrives then other flows stay blocked`() = runTest {
            givenStoreReady(ThreadManager.PreflightOutcome.Unknown)
            coEvery {
                dialogManager.showConfirm(
                    commonR.string.thread_store_unknown_preferred,
                    "MyNetwork",
                    moreInfoUrl = any(),
                    buttons = ConfirmButtons.YesNo,
                )
            } coAnswers { awaitCancellation() }
            val handler = createHandler()
            val storeJob = launch { store(handler) }
            advanceUntilIdle()

            handler.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_OK, null))
            handler.onStartMatterCommissioning()

            coVerify(exactly = 0) { matterManager.prepareMatterDeviceCommissioning() }
            storeJob.cancel()
        }
    }

    companion object {
        @JvmStatic
        fun shareOutcomes(): List<Arguments> = listOf(
            Arguments.of(MatterManager.SharingRequestResult.Shared, SuccessResultMessage(SHARE_MESSAGE_ID)),
            Arguments.of(
                MatterManager.SharingRequestResult.Cancelled,
                ErrorResultMessage(id = SHARE_MESSAGE_ID, code = "canceled", message = "Cancelled by the user"),
            ),
            Arguments.of(
                MatterManager.SharingRequestResult.Failed,
                ErrorResultMessage(id = SHARE_MESSAGE_ID, code = "failed", message = "Sharing failed"),
            ),
        )
    }
}
