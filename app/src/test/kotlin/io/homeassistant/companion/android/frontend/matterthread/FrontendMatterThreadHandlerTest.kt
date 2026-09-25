package io.homeassistant.companion.android.frontend.matterthread

import android.app.Activity
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import app.cash.turbine.test
import io.homeassistant.companion.android.common.data.HomeAssistantVersion
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.frontend.dialog.FrontendDialogManager
import io.homeassistant.companion.android.frontend.externalbus.FrontendExternalBusRepository
import io.homeassistant.companion.android.frontend.externalbus.outgoing.ErrorResultMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.MatterCommissionFinishMessage
import io.homeassistant.companion.android.frontend.externalbus.outgoing.SuccessResultMessage
import io.homeassistant.companion.android.matter.MatterManager
import io.homeassistant.companion.android.matter.MatterShareRequest
import io.homeassistant.companion.android.thread.ThreadManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

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

    private fun givenServerVersion(version: HomeAssistantVersion?) {
        val server: Server = mockk {
            every { this@mockk.version } returns version
        }
        coEvery { serverManager.getServer() } returns server
    }

    @Test
    fun `Given Matter Ready result when onStartMatterCommissioning then emits LaunchIntent`() = runTest {
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
    fun `Given Matter Error result when onStartMatterCommissioning then emits MatterError snackbar event`() = runTest {
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
    fun `Given commissionMatterDevice throws when onStartMatterCommissioning then emits MatterError snackbar event`() = runTest {
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
        // Drive the handler to the awaiting-intent-result state.
        handler.onStartMatterCommissioning()

        handler.events.test {
            handler.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_OK, null))
            expectNoEvents()
        }
        coVerify { externalBusRepository.send(MatterCommissionFinishMessage(name = "Kitchen light", success = true)) }
        coVerify(exactly = 0) { dialogManager.showMatterThreadTerminal(any()) }
    }

    @Test
    fun `Given Matter is in-flight and commissioning failed on old server when onMatterThreadIntentResult then reports failure and emits MatterCancelled snackbar`() = runTest {
        coEvery { matterManager.prepareMatterDeviceCommissioning() } returns MatterManager.CommissioningResult.Ready(mockk())
        every { matterManager.parseCommissioningIntentResult(any()) } returns MatterManager.CommissioningRequestResult.Failed
        givenServerVersion(HomeAssistantVersion(2026, 6, 0))
        val handler = createHandler()
        handler.onStartMatterCommissioning()

        handler.events.test {
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
        handler.onStartMatterCommissioning()

        handler.events.test {
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
    fun `Given Thread Ready result when onImportThreadCredentials then emits LaunchIntent`() = runTest {
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
    fun `Given Thread AppUnsupported result when onImportThreadCredentials then emits ThreadError snackbar event`() = runTest {
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
    fun `Given exportThreadCredentials throws when onImportThreadCredentials then emits ThreadError snackbar event`() = runTest {
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
    fun `Given Thread is in-flight and sendThreadDatasetExportResult returns name when onMatterThreadIntentResult then emits ThreadSuccess snackbar`() = runTest {
        coEvery { threadManager.exportPreferredDataset(any()) } returns ThreadManager.SyncResult.OnlyOnDevice(exportIntent = mockk())
        coEvery { threadManager.sendThreadDatasetExportResult(any(), any()) } returns "My Thread Network"
        val handler = createHandler()
        handler.onImportThreadCredentials(serverId = 42)

        handler.events.test {
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
    fun `Given Thread is in-flight and sendThreadDatasetExportResult throws when onMatterThreadIntentResult then emits ThreadError snackbar`() = runTest {
        coEvery { threadManager.exportPreferredDataset(any()) } returns ThreadManager.SyncResult.OnlyOnDevice(exportIntent = mockk())
        coEvery { threadManager.sendThreadDatasetExportResult(any(), any()) } throws IllegalStateException("boom")
        val handler = createHandler()
        handler.onImportThreadCredentials(serverId = 1)

        handler.events.test {
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

    @Test
    fun `Given sharing Ready result when onStartMatterSharing then emits LaunchIntent with parsed request`() = runTest {
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
        coVerify { matterManager.prepareDeviceSharing(MatterShareRequest.fromPayload(SHARE_PAYLOAD)!!) }
    }

    @Test
    fun `Given invalid payload when onStartMatterSharing then replies failed without calling Play Services`() = runTest {
        val handler = createHandler()

        handler.onStartMatterSharing(SHARE_MESSAGE_ID, JsonObject(mapOf("setup_pin_code" to JsonPrimitive(20202021))))

        coVerify(exactly = 0) { matterManager.prepareDeviceSharing(any()) }
        coVerify {
            externalBusRepository.send(
                ErrorResultMessage(id = SHARE_MESSAGE_ID, code = "failed", message = "Invalid matter/share_device payload"),
            )
        }
    }

    @Test
    fun `Given another flow in flight when onStartMatterSharing then replies failed`() = runTest {
        coEvery { matterManager.prepareMatterDeviceCommissioning() } returns MatterManager.CommissioningResult.Ready(mockk())
        val handler = createHandler()
        handler.onStartMatterCommissioning()

        handler.onStartMatterSharing(SHARE_MESSAGE_ID, SHARE_PAYLOAD)

        coVerify(exactly = 0) { matterManager.prepareDeviceSharing(any()) }
        coVerify {
            externalBusRepository.send(
                ErrorResultMessage(id = SHARE_MESSAGE_ID, code = "failed", message = "Another Matter flow is in progress"),
            )
        }
    }

    @Test
    fun `Given sharing Error result when onStartMatterSharing then replies failed`() = runTest {
        coEvery { matterManager.prepareDeviceSharing(any()) } returns
            MatterManager.CommissioningResult.Error(IllegalStateException("nope"))
        val handler = createHandler()

        handler.onStartMatterSharing(SHARE_MESSAGE_ID, SHARE_PAYLOAD)

        coVerify {
            externalBusRepository.send(
                ErrorResultMessage(id = SHARE_MESSAGE_ID, code = "failed", message = "Sharing could not be prepared"),
            )
        }
    }

    @Test
    fun `Given sharing is in-flight and shared when onMatterThreadIntentResult then replies success`() = runTest {
        givenSharingInFlight(MatterManager.SharingRequestResult.Shared)
        val handler = createHandler()
        handler.onStartMatterSharing(SHARE_MESSAGE_ID, SHARE_PAYLOAD)

        handler.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_OK, null))

        coVerify { externalBusRepository.send(SuccessResultMessage(SHARE_MESSAGE_ID)) }
    }

    @Test
    fun `Given sharing is in-flight and cancelled when onMatterThreadIntentResult then replies cancelled`() = runTest {
        givenSharingInFlight(MatterManager.SharingRequestResult.Cancelled)
        val handler = createHandler()
        handler.onStartMatterSharing(SHARE_MESSAGE_ID, SHARE_PAYLOAD)

        handler.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_CANCELED, null))

        coVerify {
            externalBusRepository.send(
                ErrorResultMessage(id = SHARE_MESSAGE_ID, code = "cancelled", message = "Cancelled by the user"),
            )
        }
    }

    @Test
    fun `Given sharing is in-flight and failed when onMatterThreadIntentResult then replies failed`() = runTest {
        givenSharingInFlight(MatterManager.SharingRequestResult.Failed)
        val handler = createHandler()
        handler.onStartMatterSharing(SHARE_MESSAGE_ID, SHARE_PAYLOAD)

        handler.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_FIRST_USER, null))

        coVerify {
            externalBusRepository.send(ErrorResultMessage(id = SHARE_MESSAGE_ID, code = "failed", message = "Sharing failed"))
        }
    }

    private fun givenSharingInFlight(outcome: MatterManager.SharingRequestResult) {
        coEvery { matterManager.prepareDeviceSharing(any()) } returns MatterManager.CommissioningResult.Ready(mockk())
        every { matterManager.parseSharingIntentResult(any()) } returns outcome
    }
}

private const val SHARE_MESSAGE_ID = 42
private val SHARE_PAYLOAD = JsonObject(
    mapOf(
        "setup_pin_code" to JsonPrimitive(20202021),
        "discriminator" to JsonPrimitive(3840),
        "device_name" to JsonPrimitive("Kitchen light"),
        "remaining_seconds" to JsonPrimitive(250),
    ),
)
