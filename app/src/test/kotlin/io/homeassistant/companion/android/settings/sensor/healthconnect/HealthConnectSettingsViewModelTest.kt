package io.homeassistant.companion.android.settings.sensor.healthconnect

import android.app.Application
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.work.WorkManager
import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectChangesWorker
import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectSyncPreferences
import io.homeassistant.companion.android.testing.unit.ConsoleLogExtension
import io.homeassistant.companion.android.testing.unit.MainDispatcherJUnit5Extension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import javax.inject.Provider
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Verifies the persistence + WorkManager scheduling side-effect that the
 * [HealthConnectSettingsViewModel] is supposed to keep coupled together.
 *
 * The view-model crosses the JVM/Android boundary by talking to [WorkManager.getInstance],
 * so we mock that static call. Using Robolectric for what is essentially an enqueue-vs-cancel
 * assertion would multiply test time without adding coverage.
 */
@ExtendWith(ConsoleLogExtension::class, MainDispatcherJUnit5Extension::class)
class HealthConnectSettingsViewModelTest {

    private val application = mockk<Application>(relaxed = true) {
        // WorkManager's Kotlin companion calls context.applicationContext before delegating
        // to the impl, so the mock has to return a real-ish Context for both `application`
        // and `applicationContext` for the static stub below to ever capture matchers.
        every { applicationContext } returns this
    }
    private val preferences = mockk<HealthConnectSyncPreferences>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any<Context>()) } returns workManager
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initial state reflects persisted preference and HC availability`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { preferences.isRealtimeSyncEnabled() } returns true
        val client = mockk<HealthConnectClient>()
        val vm = HealthConnectSettingsViewModel(application, preferences, Provider { client })

        advanceUntilIdle()
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.isAvailable)
        assertTrue(state.realtimeSyncEnabled)
    }

    @Test
    fun `unavailable when client provider returns null`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { preferences.isRealtimeSyncEnabled() } returns false
        val vm = HealthConnectSettingsViewModel(application, preferences, Provider { null })

        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAvailable)
    }

    @Test
    fun `enabling realtime sync persists and starts the worker`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { preferences.isRealtimeSyncEnabled() } returns false
        val vm = HealthConnectSettingsViewModel(
            application,
            preferences,
            Provider { mockk<HealthConnectClient>() },
        )
        advanceUntilIdle()

        vm.setRealtimeSyncEnabled(true)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.realtimeSyncEnabled)
        coVerify { preferences.setRealtimeSyncEnabled(true) }
        verify {
            workManager.enqueueUniquePeriodicWork(
                HealthConnectChangesWorker.UNIQUE_WORK_NAME,
                any(),
                any(),
            )
        }
    }

    @Test
    fun `disabling realtime sync persists and cancels the worker`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { preferences.isRealtimeSyncEnabled() } returns true
        val vm = HealthConnectSettingsViewModel(
            application,
            preferences,
            Provider { mockk<HealthConnectClient>() },
        )
        advanceUntilIdle()

        vm.setRealtimeSyncEnabled(false)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.realtimeSyncEnabled)
        coVerify { preferences.setRealtimeSyncEnabled(false) }
        verify { workManager.cancelUniqueWork(HealthConnectChangesWorker.UNIQUE_WORK_NAME) }
    }
}
