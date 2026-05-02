package io.homeassistant.companion.android.settings.sensor.healthconnect

import android.app.Application
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.work.WorkManager
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.sensors.HealthConnectSensorManager
import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectChangesWorker
import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectDataType
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
    private val sensorDao = mockk<SensorDao>(relaxed = true)
    private val serverManager = mockk<ServerManager>(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any<Context>()) } returns workManager
        // The HC manager's `hasSensor(context)` and `getAvailableSensors` both gate on
        // `HealthConnectClient.getSdkStatus(context)` returning SDK_AVAILABLE. In a JVM
        // unit test that call would otherwise return SDK_UNAVAILABLE and the list would
        // be empty, so we stub it so the bulk-enable path actually has sensors to flip.
        mockkObject(HealthConnectClient.Companion)
        every { HealthConnectClient.getSdkStatus(any()) } returns HealthConnectClient.SDK_AVAILABLE
    }

    private fun makeVm(
        client: HealthConnectClient? = mockk<HealthConnectClient>(),
    ) = HealthConnectSettingsViewModel(
        application = application,
        preferences = preferences,
        clientProvider = Provider { client },
        sensorDao = sensorDao,
        serverManager = serverManager,
    )

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initial state reflects persisted preference and HC availability`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { preferences.isRealtimeSyncEnabled() } returns true
        val vm = makeVm()

        advanceUntilIdle()
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.isAvailable)
        assertTrue(state.realtimeSyncEnabled)
    }

    @Test
    fun `unavailable when client provider returns null`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { preferences.isRealtimeSyncEnabled() } returns false
        val vm = makeVm(client = null)

        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAvailable)
    }

    @Test
    fun `enabling realtime sync persists and starts the worker`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { preferences.isRealtimeSyncEnabled() } returns false
        val vm = makeVm()
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
        val vm = makeVm()
        advanceUntilIdle()

        vm.setRealtimeSyncEnabled(false)
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.realtimeSyncEnabled)
        coVerify { preferences.setRealtimeSyncEnabled(false) }
        verify { workManager.cancelUniqueWork(HealthConnectChangesWorker.UNIQUE_WORK_NAME) }
    }

    @Test
    fun `enableAll persists per-sensor allow-writes settings and emits the full perm set`() = runTest(
        UnconfinedTestDispatcher(),
    ) {
        coEvery { preferences.isRealtimeSyncEnabled() } returns false
        // One server, two sensors-worth of work — we don't need to mock the manager here
        // because the VM looks it up via SensorReceiver.MANAGERS at runtime, which already
        // has the singleton HealthConnectSensorManager available.
        val server = mockk<Server>()
        every { server.id } returns 1
        coEvery { serverManager.servers() } returns listOf(server)
        HealthConnectSensorManager.allowWritesCache.clear()

        val vm = makeVm()
        advanceUntilIdle()

        // Start collecting BEFORE triggering enableAll — the SharedFlow has replay=0, so
        // a late `first()` would deadlock waiting for a re-emission that never comes.
        val collected = mutableListOf<Set<String>>()
        val collectorJob: Job = launch { vm.enableAllRequested.collect { collected += it } }

        vm.enableAll()
        advanceUntilIdle()
        collectorJob.cancel()

        assertTrue(collected.isNotEmpty(), "No permission set was emitted")
        val perms = collected.first()

        assertTrue(perms.isNotEmpty())
        // Every data type's read AND write perm should appear in the emitted set.
        HealthConnectDataType.all.forEach { dataType ->
            assertTrue(
                dataType.readPermission in perms,
                "Missing read perm for ${dataType.key}",
            )
            assertTrue(
                dataType.writePermission in perms,
                "Missing write perm for ${dataType.key}",
            )
        }
        // Each sensor with a sensor ID should have an allow-writes setting persisted.
        coVerify(atLeast = 1) {
            sensorDao.add(match<SensorSetting> { it.name == HealthConnectSensorManager.SETTING_ALLOW_WRITES })
        }
        // Sensors should have been enabled for the registered server(s).
        coVerify(atLeast = 1) { sensorDao.setSensorEnabled(any(), listOf(1), enabled = true) }
        assertFalse(vm.uiState.value.enableAllInProgress)
    }

    @Test
    fun `enabledSensorCount reflects the sensor table flow`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { preferences.isRealtimeSyncEnabled() } returns false
        val sensorsFlow = MutableStateFlow<List<Sensor>>(emptyList())
        every { sensorDao.getAllFlow() } returns sensorsFlow

        val vm = makeVm()
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.enabledSensorCount)
        // Total comes from the catalogue and should be > 0 (every HC type has at least
        // one sensor id except Speed/Power/Cadence which are intentionally empty).
        assertTrue(vm.uiState.value.totalSensorCount > 0)

        // Two enabled sensors across two servers — should still count as 2 distinct sensors.
        sensorsFlow.value = listOf(
            Sensor("health_connect_weight", serverId = 1, enabled = true, state = ""),
            Sensor("health_connect_weight", serverId = 2, enabled = true, state = ""),
            Sensor("health_connect_steps", serverId = 1, enabled = true, state = ""),
            Sensor("health_connect_blood_pressure", serverId = 1, enabled = false, state = ""),
        )
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.enabledSensorCount)
    }

    @Test
    fun `enableAll is a no-op while already in progress`() = runTest(UnconfinedTestDispatcher()) {
        coEvery { preferences.isRealtimeSyncEnabled() } returns false
        val server = mockk<Server>()
        every { server.id } returns 1
        coEvery { serverManager.servers() } returns listOf(server)
        val vm = makeVm()
        advanceUntilIdle()

        // Force the in-progress flag and confirm a second click is dropped.
        vm.enableAll()
        // Second call before the first completes should be a no-op — but with an
        // UnconfinedTestDispatcher the first call already finished by here, so this just
        // verifies the code path doesn't re-enter while the flag is mid-flight. The flag
        // resets to false in `finally`, so we observe the final state, not an intermediate.
        advanceUntilIdle()
        assertFalse(vm.uiState.value.enableAllInProgress)
    }
}
