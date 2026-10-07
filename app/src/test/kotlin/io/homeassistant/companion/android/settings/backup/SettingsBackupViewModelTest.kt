package io.homeassistant.companion.android.settings.backup

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.backup.InvalidSettingsBackupException
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherJUnit5Extension::class)
class SettingsBackupViewModelTest {
    private val handler = mockk<SettingsBackupHandler>()
    private val documents = mockk<BackupDocumentRepository>()
    private val runtime = mockk<BackupRuntimeManager>()
    private val savedState = SavedStateHandle()
    private val backup = backupFixture()
    private val destinations = listOf(BackupDestination(42, "Home"))
    private val plan = prepareSettingsRestore(backup, mapOf("home" to 42), destinations, emptyMap())

    @BeforeEach
    fun setup() {
        coEvery { handler.destinations() } returns destinations
        coEvery { documents.read(any()) } returns backup
        coEvery { handler.prepare(any(), any()) } returns plan
        coEvery { handler.restore(any(), any(), any()) } returns Unit
        coEvery { runtime.refresh() } returns Unit
    }

    @Test
    fun `Given a picker result before initialization when importing then it waits for servers and never applies settings`() = runTest {
        val loaded = CompletableDeferred<List<BackupDestination>>()
        coEvery { handler.destinations() } coAnswers { loaded.await() }
        val viewModel = SettingsBackupViewModel(handler, documents, savedState, runtime)
        viewModel.events.test {
            viewModel.importBackup("document")
            runCurrent()
            coVerify(exactly = 0) { documents.read(any()) }
            loaded.complete(destinations)
            assertEquals(BackupEvent.ShowRestore, awaitItem())
            viewModel.restore()
            runCurrent()
            coVerify(exactly = 0) { handler.restore(any(), any(), any()) }
        }
    }

    @Test
    fun `Given a preview when changing selection or mapping then a new review is required`() = runTest {
        val viewModel = SettingsBackupViewModel(handler, documents, savedState, runtime)
        viewModel.events.test {
            viewModel.importBackup("document")
            assertEquals(BackupEvent.ShowRestore, awaitItem())
            viewModel.mapServer("home", 42)
            viewModel.review()
            runCurrent()
            assertNotNull((viewModel.uiState.value as SettingsBackupUiState.Content).restore?.plan)
            viewModel.select(BackupSection.Favorites, false)
            assertNull((viewModel.uiState.value as SettingsBackupUiState.Content).restore?.plan)
            viewModel.restore()
            runCurrent()
            coVerify(exactly = 0) { handler.restore(any(), any(), any()) }
            val recreated = SettingsBackupViewModel(handler, documents, savedState, runtime)
            runCurrent()
            assertFalse((recreated.uiState.value as SettingsBackupUiState.Content).sections.favorites)
        }
    }

    @Test
    fun `Given an invalid file when importing then an error is shown and no restore draft exists`() = runTest {
        coEvery { documents.read(any()) } throws InvalidSettingsBackupException("Synthetic invalid file")
        val viewModel = SettingsBackupViewModel(handler, documents, savedState, runtime)
        viewModel.events.test {
            viewModel.importBackup("document")
            assertEquals(BackupEvent.Message(R.string.backup_invalid_file), awaitItem())
            val state = viewModel.uiState.value as SettingsBackupUiState.Content
            assertFalse(state.busy)
            assertNull(state.restore)
        }
    }

    @Test
    fun `Given a restore in progress when applying twice or cancelling then only one commit is performed`() = runTest {
        val committed = CompletableDeferred<Unit>()
        coEvery { handler.restore(any(), any(), any()) } coAnswers { committed.await() }
        val viewModel = SettingsBackupViewModel(handler, documents, savedState, runtime)
        viewModel.events.test {
            viewModel.importBackup("document")
            assertEquals(BackupEvent.ShowRestore, awaitItem())
            viewModel.mapServer("home", 42)
            viewModel.review()
            runCurrent()
            viewModel.restore()
            runCurrent()
            viewModel.dismissRestore()
            assertNotNull((viewModel.uiState.value as SettingsBackupUiState.Content).restore)
            viewModel.restore()
            runCurrent()
            committed.complete(Unit)
            assertEquals(BackupEvent.Restored, awaitItem())
            assertEquals(BackupEvent.Message(R.string.backup_restore_success), awaitItem())
            coVerify(exactly = 1) { handler.restore(any(), any(), any()) }
            assertNull((viewModel.uiState.value as SettingsBackupUiState.Content).restore)
        }
    }

    @Test
    fun `Given a refresh failure after committing when reporting then settings remain restored and retry cannot apply again`() = runTest {
        coEvery { runtime.refresh() } throws SecurityException("Synthetic background restriction")
        val viewModel = SettingsBackupViewModel(handler, documents, savedState, runtime)
        viewModel.events.test {
            viewModel.importBackup("document")
            assertEquals(BackupEvent.ShowRestore, awaitItem())
            viewModel.mapServer("home", 42)
            viewModel.review()
            runCurrent()
            viewModel.restore()
            assertEquals(BackupEvent.Restored, awaitItem())
            assertEquals(BackupEvent.Message(R.string.backup_restore_restart), awaitItem())
            viewModel.restore()
            runCurrent()
            coVerify(exactly = 1) { handler.restore(any(), any(), any()) }
            assertNotNull((viewModel.uiState.value as SettingsBackupUiState.Content).lastRestore)
        }
    }

    @Test
    fun `Given destination changes after review when applying fails then a fresh review is available`() = runTest {
        coEvery { handler.restore(any(), any(), any()) } throws IllegalStateException("Synthetic capability change")
        val viewModel = SettingsBackupViewModel(handler, documents, savedState, runtime)
        viewModel.events.test {
            viewModel.importBackup("document")
            assertEquals(BackupEvent.ShowRestore, awaitItem())
            viewModel.mapServer("home", 42)
            viewModel.review()
            runCurrent()
            viewModel.restore()
            assertEquals(BackupEvent.Message(R.string.backup_operation_failed), awaitItem())
            val state = viewModel.uiState.value as SettingsBackupUiState.Content
            assertEquals(mapOf("home" to 42), state.restore?.mapping)
            assertNull(state.restore?.plan)
            assertTrue(state.canReview)
            coVerify(exactly = 0) { runtime.refresh() }
        }
    }

    @Test
    fun `Given no backup after process death when requesting restore then nothing is applied`() = runTest {
        val viewModel = SettingsBackupViewModel(handler, documents, savedState, runtime)
        viewModel.uiState.test {
            assertEquals(SettingsBackupUiState.Loading, awaitItem())
            assertTrue(awaitItem() is SettingsBackupUiState.Content)
            viewModel.restore()
            runCurrent()
            coVerify(exactly = 0) { handler.restore(any(), any(), any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
