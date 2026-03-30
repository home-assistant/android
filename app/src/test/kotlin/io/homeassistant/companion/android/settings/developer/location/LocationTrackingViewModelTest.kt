package io.homeassistant.companion.android.settings.developer.location

import android.app.Application
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.location.LocationHistoryDao
import io.homeassistant.companion.android.database.location.LocationHistoryItemResult
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(ConsoleLogExtension::class, MainDispatcherJUnit5Extension::class)
class LocationTrackingViewModelTest {

    private val locationHistoryDao: LocationHistoryDao = mockk(relaxed = true)
    private val prefsRepository: PrefsRepository = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val application: Application = mockk(relaxed = true)

    @BeforeEach
    fun setUp() {
        coEvery { prefsRepository.isLocationHistoryEnabled() } returns false
        coEvery { serverManager.servers() } returns emptyList()
        every { locationHistoryDao.getAll() } returns mockk(relaxed = true)
        every { locationHistoryDao.getAll(any()) } returns mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun createViewModel(): LocationTrackingViewModel = LocationTrackingViewModel(
        locationHistoryDao = locationHistoryDao,
        prefsRepository = prefsRepository,
        serverManager = serverManager,
        application = application,
    )

    @Test
    fun `Given history disabled when created then historyEnabled is false`() = runTest {
        coEvery { prefsRepository.isLocationHistoryEnabled() } returns false
        val viewModel = createViewModel()
        runCurrent()

        assertFalse(viewModel.historyEnabled)
    }

    @Test
    fun `Given history enabled when created then historyEnabled is true`() = runTest {
        coEvery { prefsRepository.isLocationHistoryEnabled() } returns true
        val viewModel = createViewModel()
        runCurrent()

        assertTrue(viewModel.historyEnabled)
    }

    @Test
    fun `Given history disabled when enabling then prefs are updated`() = runTest {
        coEvery { prefsRepository.isLocationHistoryEnabled() } returns false
        val viewModel = createViewModel()
        runCurrent()

        viewModel.enableHistory(enabled = true)
        runCurrent()

        coVerify { prefsRepository.setLocationHistoryEnabled(true) }
    }

    @Test
    fun `Given history enabled when disabling then history is deleted`() = runTest {
        coEvery { prefsRepository.isLocationHistoryEnabled() } returns true
        val viewModel = createViewModel()
        runCurrent()

        viewModel.enableHistory(enabled = false)
        runCurrent()

        coVerify { prefsRepository.setLocationHistoryEnabled(false) }
        coVerify { locationHistoryDao.deleteAll() }
    }

    @Test
    fun `Given history already enabled when enabling again then no action taken`() = runTest {
        coEvery { prefsRepository.isLocationHistoryEnabled() } returns true
        val viewModel = createViewModel()
        runCurrent()

        viewModel.enableHistory(enabled = true)
        runCurrent()

        coVerify(exactly = 0) { prefsRepository.setLocationHistoryEnabled(any()) }
    }

    @Test
    fun `Given sent filter when collecting history then dao queries sent results`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.historyPagerFlow.collect {} }
        runCurrent()

        viewModel.setHistoryFilter(LocationTrackingViewModel.HistoryFilter.SENT.menuItemId)
        runCurrent()

        verify { locationHistoryDao.getAll(listOf(LocationHistoryItemResult.SENT.name)) }
    }

    @Test
    fun `Given failed filter when collecting history then dao queries failed results`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.historyPagerFlow.collect {} }
        runCurrent()

        viewModel.setHistoryFilter(LocationTrackingViewModel.HistoryFilter.FAILED.menuItemId)
        runCurrent()

        verify { locationHistoryDao.getAll(listOf(LocationHistoryItemResult.FAILED_SEND.name)) }
    }

    @Test
    fun `Given skipped filter when collecting history then dao queries skipped results`() = runTest {
        val expectedResults = (
            LocationHistoryItemResult.entries.toMutableList() -
                LocationHistoryItemResult.SENT -
                LocationHistoryItemResult.FAILED_SEND
            ).map { it.name }

        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.historyPagerFlow.collect {} }
        runCurrent()

        viewModel.setHistoryFilter(LocationTrackingViewModel.HistoryFilter.SKIPPED.menuItemId)
        runCurrent()

        verify { locationHistoryDao.getAll(expectedResults) }
    }

    @Test
    fun `Given default filter when collecting history then dao queries all results`() = runTest {
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.historyPagerFlow.collect {} }
        runCurrent()

        verify { locationHistoryDao.getAll() }
    }
}
