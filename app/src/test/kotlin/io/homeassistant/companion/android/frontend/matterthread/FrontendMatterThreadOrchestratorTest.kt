package io.homeassistant.companion.android.frontend.matterthread

import android.app.Activity
import android.content.IntentSender
import androidx.activity.result.ActivityResult
import app.cash.turbine.test
import io.homeassistant.companion.android.frontend.dialog.FrontendDialogManager
import io.homeassistant.companion.android.matter.MatterCommissioningResult
import io.homeassistant.companion.android.matter.MatterManager
import io.homeassistant.companion.android.thread.ThreadManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FrontendMatterThreadOrchestratorTest {

    private val matterManager: MatterManager = mockk(relaxed = true)
    private val threadManager: ThreadManager = mockk(relaxed = true)
    private val dialogManager: FrontendDialogManager = mockk(relaxed = true) {
        // Default: progress dialog suspends forever (caller must cancel to dismiss).
        coEvery { showMatterThreadProgress() } coAnswers { awaitCancellation() }
    }

    private fun createOrchestrator() = FrontendMatterThreadOrchestrator(
        matterManager = matterManager,
        threadManager = threadManager,
        dialogManager = dialogManager,
    )

    @Test
    fun `Given matter Ready result when onStartMatterCommissioning then emits LaunchIntent`() = runTest {
        val intent: IntentSender = mockk()
        coEvery { matterManager.commissionMatterDevice() } returns MatterCommissioningResult.Ready(intent)
        val orchestrator = createOrchestrator()

        orchestrator.events.test {
            launch { orchestrator.onStartMatterCommissioning() }
            val event = awaitItem()
            assertInstanceOf(FrontendMatterThreadOrchestrator.Event.LaunchIntent::class.java, event)
            assertEquals(intent, (event as FrontendMatterThreadOrchestrator.Event.LaunchIntent).intentSender)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given matter Error result when onStartMatterCommissioning then emits MatterError snackbar event`() = runTest {
        coEvery { matterManager.commissionMatterDevice() } returns
            MatterCommissioningResult.Error(IllegalStateException("nope"))
        val orchestrator = createOrchestrator()

        orchestrator.events.test {
            launch { orchestrator.onStartMatterCommissioning() }
            val event = awaitItem()
            assertInstanceOf(FrontendMatterThreadOrchestrator.Event.ShowSnackbar::class.java, event)
            assertEquals(
                MatterThreadTerminal.Snackbar.MatterError,
                (event as FrontendMatterThreadOrchestrator.Event.ShowSnackbar).snackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given commissionMatterDevice throws when onStartMatterCommissioning then emits MatterError snackbar event`() = runTest {
        coEvery { matterManager.commissionMatterDevice() } throws IllegalStateException("boom")
        val orchestrator = createOrchestrator()

        orchestrator.events.test {
            launch { orchestrator.onStartMatterCommissioning() }
            val event = awaitItem()
            assertEquals(
                MatterThreadTerminal.Snackbar.MatterError,
                (event as FrontendMatterThreadOrchestrator.Event.ShowSnackbar).snackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given Matter is in-flight and RESULT_OK when onMatterThreadIntentResult then no event is emitted`() = runTest {
        coEvery { matterManager.commissionMatterDevice() } returns MatterCommissioningResult.Ready(mockk())
        val orchestrator = createOrchestrator()
        // Drive the orchestrator to the awaiting-intent-result state.
        orchestrator.onStartMatterCommissioning()

        orchestrator.events.test {
            orchestrator.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_OK, null))
            expectNoEvents()
        }
        coVerify(exactly = 0) { dialogManager.showMatterThreadTerminal(any()) }
    }

    @Test
    fun `Given Matter is in-flight and RESULT_CANCELED when onMatterThreadIntentResult then emits MatterCancelled snackbar`() = runTest {
        coEvery { matterManager.commissionMatterDevice() } returns MatterCommissioningResult.Ready(mockk())
        val orchestrator = createOrchestrator()
        orchestrator.onStartMatterCommissioning()

        orchestrator.events.test {
            launch { orchestrator.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_CANCELED, null)) }
            val event = awaitItem()
            assertEquals(
                MatterThreadTerminal.Snackbar.MatterCancelled,
                (event as FrontendMatterThreadOrchestrator.Event.ShowSnackbar).snackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given nothing in-flight when onMatterThreadIntentResult then result is ignored`() = runTest {
        val orchestrator = createOrchestrator()

        orchestrator.events.test {
            orchestrator.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_CANCELED, null))
            expectNoEvents()
        }
        coVerify(exactly = 0) { dialogManager.showMatterThreadTerminal(any()) }
    }

    @Test
    fun `Given thread Ready result when onImportThreadCredentials then emits LaunchIntent`() = runTest {
        val intent: IntentSender = mockk()
        coEvery { threadManager.exportThreadCredentials(any()) } returns ThreadManager.SyncResult.OnlyOnDevice(exportIntent = intent)
        val orchestrator = createOrchestrator()

        orchestrator.events.test {
            launch { orchestrator.onImportThreadCredentials(serverId = 1) }
            val event = assertInstanceOf(FrontendMatterThreadOrchestrator.Event.LaunchIntent::class.java, awaitItem())
            assertEquals(intent, event.intentSender)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given thread NoDataset result when onImportThreadCredentials then shows ThreadNoDataset dialog`() = runTest {
        coEvery { threadManager.exportThreadCredentials(any()) } returns ThreadManager.SyncResult.NoneHaveCredentials
        val orchestrator = createOrchestrator()

        orchestrator.onImportThreadCredentials(serverId = 1)

        coVerify { dialogManager.showMatterThreadTerminal(MatterThreadTerminal.Dialog.ThreadNoDataset) }
    }

    @Test
    fun `Given thread NotConnected result when onImportThreadCredentials then shows ThreadNotConnected dialog`() = runTest {
        coEvery { threadManager.exportThreadCredentials(any()) } returns ThreadManager.SyncResult.NotConnected
        val orchestrator = createOrchestrator()

        orchestrator.onImportThreadCredentials(serverId = 1)

        coVerify { dialogManager.showMatterThreadTerminal(MatterThreadTerminal.Dialog.ThreadNotConnected) }
    }

    @Test
    fun `Given thread AppUnsupported result when onImportThreadCredentials then emits ThreadError snackbar event`() = runTest {
        coEvery { threadManager.exportThreadCredentials(any()) } returns ThreadManager.SyncResult.AppUnsupported
        val orchestrator = createOrchestrator()

        orchestrator.events.test {
            launch { orchestrator.onImportThreadCredentials(serverId = 1) }
            val event = awaitItem()
            assertEquals(
                MatterThreadTerminal.Snackbar.ThreadError,
                (event as FrontendMatterThreadOrchestrator.Event.ShowSnackbar).snackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given exportThreadCredentials throws when onImportThreadCredentials then emits ThreadError snackbar event`() = runTest {
        coEvery { threadManager.exportThreadCredentials(any()) } throws IllegalStateException("boom")
        val orchestrator = createOrchestrator()

        orchestrator.events.test {
            launch { orchestrator.onImportThreadCredentials(serverId = 1) }
            val event = awaitItem()
            assertEquals(
                MatterThreadTerminal.Snackbar.ThreadError,
                (event as FrontendMatterThreadOrchestrator.Event.ShowSnackbar).snackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given onImportThreadCredentials when started then shows progress dialog`() = runTest {
        // Hold the call open so we can verify the progress dialog is shown before the result arrives.
        coEvery { threadManager.exportThreadCredentials(any()) } coAnswers { awaitCancellation() }
        val orchestrator = createOrchestrator()

        val job = launch { orchestrator.onImportThreadCredentials(serverId = 1) }
        advanceUntilIdle()

        coVerify { dialogManager.showMatterThreadProgress() }
        job.cancel()
    }

    @Test
    fun `Given Thread is in-flight and sendThreadDatasetExportResult returns name when onMatterThreadIntentResult then emits ThreadSuccess snackbar`() = runTest {
        coEvery { threadManager.exportThreadCredentials(any()) } returns ThreadManager.SyncResult.OnlyOnDevice(exportIntent = mockk())
        coEvery { threadManager.sendThreadDatasetExportResult(any(), any()) } returns "My Thread Network"
        val orchestrator = createOrchestrator()
        orchestrator.onImportThreadCredentials(serverId = 42)

        orchestrator.events.test {
            launch { orchestrator.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_OK, null)) }
            val event = awaitItem()
            assertEquals(
                MatterThreadTerminal.Snackbar.ThreadSuccess,
                (event as FrontendMatterThreadOrchestrator.Event.ShowSnackbar).snackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
        // serverId from the in-flight ref is forwarded to the manager — no extra plumbing needed.
        coVerify { threadManager.sendThreadDatasetExportResult(any(), serverId = 42) }
    }

    @Test
    fun `Given Thread is in-flight and sendThreadDatasetExportResult returns null when onMatterThreadIntentResult then shows ThreadNoDataset dialog`() = runTest {
        coEvery { threadManager.exportThreadCredentials(any()) } returns ThreadManager.SyncResult.OnlyOnDevice(exportIntent = mockk())
        coEvery { threadManager.sendThreadDatasetExportResult(any(), any()) } returns null
        val orchestrator = createOrchestrator()
        orchestrator.onImportThreadCredentials(serverId = 1)

        orchestrator.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_OK, null))

        coVerify { dialogManager.showMatterThreadTerminal(MatterThreadTerminal.Dialog.ThreadNoDataset) }
    }

    @Test
    fun `Given Thread is in-flight and sendThreadDatasetExportResult throws when onMatterThreadIntentResult then emits ThreadError snackbar`() = runTest {
        coEvery { threadManager.exportThreadCredentials(any()) } returns ThreadManager.SyncResult.OnlyOnDevice(exportIntent = mockk())
        coEvery { threadManager.sendThreadDatasetExportResult(any(), any()) } throws IllegalStateException("boom")
        val orchestrator = createOrchestrator()
        orchestrator.onImportThreadCredentials(serverId = 1)

        orchestrator.events.test {
            launch { orchestrator.onMatterThreadIntentResult(ActivityResult(Activity.RESULT_OK, null)) }
            val event = awaitItem()
            assertEquals(
                MatterThreadTerminal.Snackbar.ThreadError,
                (event as FrontendMatterThreadOrchestrator.Event.ShowSnackbar).snackbar,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Given a flow already in-flight when another start request arrives then the second is dropped`() = runTest {
        coEvery { matterManager.commissionMatterDevice() } coAnswers { awaitCancellation() }
        val orchestrator = createOrchestrator()

        val firstJob = launch { orchestrator.onStartMatterCommissioning() }
        advanceUntilIdle()
        orchestrator.onImportThreadCredentials(serverId = 1)

        coVerify(exactly = 0) { threadManager.exportThreadCredentials(any()) }
        firstJob.cancel()
    }
}
