package io.homeassistant.companion.android.common.sensors

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.Condition
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.DNDSensorManager.Companion.dndSensor
import io.homeassistant.companion.android.common.sensors.DNDSensorManager.Companion.homeAssistantModeSensor
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.sensor.Sensor
import io.homeassistant.companion.android.database.server.Server
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val MODE_ID = "mode-id"
private const val VANILLA_ICE_CREAM_SDK = 35
private const val UPSIDE_DOWN_CAKE_SDK = 34
private const val SERVER_ID = 1
private const val OTHER_SERVER_ID = 2

/** Tests how [DNDSensorManager] drives the Home Assistant mode; the mode itself is tested in [HomeAssistantModeTest]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [VANILLA_ICE_CREAM_SDK])
class DNDSensorManagerTest {

    private val notificationManager: NotificationManager = mockk(relaxed = true)
    private val packageManager: PackageManager = mockk(relaxed = true)
    private val sensorRepository: SensorRepository = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val addedMode = slot<AutomaticZenRule>()
    private val storedValues = mutableMapOf<String, String?>()
    private val storage: LocalStorage = mockk {
        coEvery { getString(any()) } answers { storedValues[firstArg()] }
        coEvery { putString(any(), any()) } answers { storedValues[firstArg()] = secondArg() }
    }
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var homeAssistantMode: HomeAssistantMode
    private lateinit var manager: DNDSensorManager

    @Before
    fun setUp() {
        val context = spyk(ApplicationProvider.getApplicationContext<Context>())
        every { context.packageManager } returns packageManager
        every { packageManager.getLaunchIntentForPackage(any()) } returns
            Intent().setComponent(ComponentName(context.packageName, "LaunchActivity"))

        every { notificationManager.areAutomaticZenRulesUserManaged() } returns true
        every { notificationManager.isNotificationPolicyAccessGranted } returns true
        every { notificationManager.automaticZenRules } returns emptyMap()
        every { notificationManager.addAutomaticZenRule(capture(addedMode)) } answers {
            every { notificationManager.getAutomaticZenRule(MODE_ID) } returns addedMode.captured
            MODE_ID
        }
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_FALSE
        coEvery { sensorRepository.get(homeAssistantModeSensor.id) } returns
            listOf(Sensor(id = homeAssistantModeSensor.id, serverId = SERVER_ID, enabled = true, state = ""))

        homeAssistantMode = HomeAssistantMode(context, notificationManager, storage, testDispatcher)
        manager = DNDSensorManager(context, sensorRepository, serverManager, homeAssistantMode)
    }

    @After
    fun tearDown() {
        SdkVersion.resetSdkInt()
    }

    @Test
    fun `Given device where modes are managed by the user when getting sensors then mode sensor is available`() = runTest(testDispatcher) {
        assertEquals(listOf(dndSensor, homeAssistantModeSensor), manager.getAvailableSensors())
    }

    @Test
    fun `Given device where modes are not managed by the user when getting sensors then mode sensor is missing`() = runTest(testDispatcher) {
        every { notificationManager.areAutomaticZenRulesUserManaged() } returns false

        assertEquals(listOf(dndSensor), manager.getAvailableSensors())
    }

    @Test
    fun `Given Android older than 15 when getting sensors then mode sensor is missing`() = runTest(testDispatcher) {
        SdkVersion.sdkInt = UPSIDE_DOWN_CAKE_SDK
        homeAssistantMode = HomeAssistantMode(
            ApplicationProvider.getApplicationContext(),
            notificationManager,
            storage,
            testDispatcher,
        )
        manager = DNDSensorManager(
            ApplicationProvider.getApplicationContext(),
            sensorRepository,
            serverManager,
            homeAssistantMode,
        )

        assertEquals(listOf(dndSensor), manager.getAvailableSensors())
        assertFalse(manager.isHomeAssistantModeSupported())
    }

    @Test
    fun `Given no Do Not Disturb access when checking permissions then only the mode sensor misses it`() = runTest(testDispatcher) {
        every { notificationManager.isNotificationPolicyAccessGranted } returns false

        assertFalse(manager.checkPermission(homeAssistantModeSensor.id))
        assertTrue(manager.checkPermission(dndSensor.id))
    }

    @Test
    fun `Given mode turned on when updating then the sensor stores a boolean on state`() = runTest(testDispatcher) {
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_TRUE

        manager.requestSensorUpdate()

        coVerify {
            sensorRepository.update(
                match { it.id == homeAssistantModeSensor.id && it.state == "true" && it.stateType == "boolean" },
            )
        }
    }

    @Test
    fun `Given no Do Not Disturb access when updating then nothing is stored for the mode sensor`() = runTest(testDispatcher) {
        every { notificationManager.isNotificationPolicyAccessGranted } returns false

        manager.requestSensorUpdate()

        coVerify(exactly = 0) { sensorRepository.update(match { it.id == homeAssistantModeSensor.id }) }
    }

    @Test
    fun `Given user removed the mode when updating then the sensor is disabled on every server`() = runTest(testDispatcher) {
        coEvery { serverManager.servers() } returns listOf(
            mockk<Server> { every { id } returns SERVER_ID },
            mockk<Server> { every { id } returns OTHER_SERVER_ID },
        )
        manager.requestSensorUpdate()
        every { notificationManager.getAutomaticZenRule(MODE_ID) } returns null

        manager.requestSensorUpdate()

        coVerify(exactly = 1) {
            sensorRepository.setSensorEnabled(homeAssistantModeSensor.id, listOf(SERVER_ID, OTHER_SERVER_ID), false)
        }
    }

    @Test
    fun `Given a mode command when applying it then the mode changes`() = runTest(testDispatcher) {
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_TRUE

        assertEquals(HomeAssistantMode.ChangeResult.Applied, manager.setHomeAssistantModeActive(true))

        verify { notificationManager.setAutomaticZenRuleState(MODE_ID, match { it.state == Condition.STATE_TRUE }) }
    }

    @Test
    fun `Given a refresh paused before storing the mode state when a later refresh reads a new state then the new state is stored last`() = runTest(testDispatcher) {
        manager.requestSensorUpdate()
        val storedStates = mutableListOf<String>()
        val firstStoreStarted = CompletableDeferred<Unit>()
        val releaseFirstStore = CompletableDeferred<Unit>()
        coEvery { sensorRepository.update(match { it.id == homeAssistantModeSensor.id }) } coAnswers {
            if (storedStates.isEmpty()) {
                firstStoreStarted.complete(Unit)
                releaseFirstStore.await()
            }
            storedStates += firstArg<Sensor>().state
        }

        val olderRefresh = launch { manager.requestSensorUpdate() }
        firstStoreStarted.await()
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_TRUE
        val newerRefresh = launch { manager.requestSensorUpdate() }
        testScheduler.advanceUntilIdle()

        // Once when the mode was added, once by the older refresh: the newer one waits for the older one to store
        verify(exactly = 2) { notificationManager.getAutomaticZenRuleState(MODE_ID) }
        releaseFirstStore.complete(Unit)
        olderRefresh.join()
        newerRefresh.join()

        assertEquals(listOf("false", "true"), storedStates)
    }
}
