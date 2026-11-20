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
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.modes.HomeAssistantMode
import io.homeassistant.companion.android.common.sensors.DNDSensorManager.Companion.dndSensor
import io.homeassistant.companion.android.common.sensors.DNDSensorManager.Companion.homeAssistantModeSensor
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.sensor.Attribute
import io.homeassistant.companion.android.database.sensor.Sensor
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
private const val ICON_ON = "mdi:minus-circle"
private const val ICON_OFF = "mdi:minus-circle-off"
private const val ICON_DISABLED = "mdi:minus-circle-off-outline"
private const val ICON_NOT_ADDED = "mdi:minus-circle-outline"

/** Tests how [DNDSensorManager] reports the Home Assistant mode; the mode itself is tested in `HomeAssistantModeTest`. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [VANILLA_ICE_CREAM_SDK])
class DNDSensorManagerTest {

    private val notificationManager: NotificationManager = mockk(relaxed = true)
    private val packageManager: PackageManager = mockk(relaxed = true)
    private val sensorRepository: SensorRepository = mockk(relaxed = true)
    private val serverManager: ServerManager = mockk(relaxed = true)
    private val addedMode = slot<AutomaticZenRule>()
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
            every { notificationManager.automaticZenRules } returns mapOf(MODE_ID to addedMode.captured)
            MODE_ID
        }
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_FALSE
        coEvery { sensorRepository.get(homeAssistantModeSensor.id) } returns
            listOf(Sensor(id = homeAssistantModeSensor.id, serverId = SERVER_ID, enabled = true, state = ""))

        homeAssistantMode = HomeAssistantMode(context, notificationManager, testDispatcher)
        manager = DNDSensorManager(context, sensorRepository, serverManager, homeAssistantMode)
    }

    /** Verifies the mode sensor was stored with [state] and [icon]: Home Assistant gets it when either changed. */
    private fun verifyModeSensorStored(state: Boolean, icon: String) = coVerify {
        sensorRepository.update(
            match { it.id == homeAssistantModeSensor.id && it.state == "$state" && it.icon == icon },
        )
    }

    private fun modeAttributes(added: Boolean, enabled: Boolean) = listOf(
        Attribute(homeAssistantModeSensor.id, DNDSensorManager.ATTRIBUTE_HOME_ASSISTANT_MODE_ADDED, "$added", "boolean"),
        Attribute(homeAssistantModeSensor.id, DNDSensorManager.ATTRIBUTE_HOME_ASSISTANT_MODE_ENABLED, "$enabled", "boolean"),
    )

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
            testDispatcher,
        )
        manager = DNDSensorManager(
            ApplicationProvider.getApplicationContext(),
            sensorRepository,
            serverManager,
            homeAssistantMode,
        )

        assertEquals(listOf(dndSensor), manager.getAvailableSensors())
    }

    @Test
    fun `Given no Do Not Disturb access when checking permissions then only the mode sensor misses it`() = runTest(testDispatcher) {
        every { notificationManager.isNotificationPolicyAccessGranted } returns false

        assertFalse(manager.checkPermission(homeAssistantModeSensor.id))
        assertTrue(manager.checkPermission(dndSensor.id))
    }

    @Test
    fun `Given mode turned on when updating then the sensor stores a boolean on state`() = runTest(testDispatcher) {
        homeAssistantMode.add()
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_TRUE

        manager.requestSensorUpdate()

        coVerify {
            sensorRepository.update(
                match { it.id == homeAssistantModeSensor.id && it.state == "true" && it.stateType == "boolean" },
            )
        }
    }

    @Test
    fun `Given mode not added when updating then the sensor reports off and the mode is not added`() = runTest(testDispatcher) {
        manager.requestSensorUpdate()

        coVerify { sensorRepository.update(match { it.id == homeAssistantModeSensor.id && it.state == "false" }) }
        verify(exactly = 0) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given mode not added when updating then the attributes say it is neither added nor enabled`() = runTest(testDispatcher) {
        manager.requestSensorUpdate()

        coVerify { sensorRepository.replaceAllAttributes(homeAssistantModeSensor.id, modeAttributes(false, false)) }
    }

    @Test
    fun `Given mode disabled in Android when updating then the attributes say it is added but not enabled`() = runTest(testDispatcher) {
        homeAssistantMode.add()
        val disabledMode = mockk<AutomaticZenRule> {
            every { conditionId } returns addedMode.captured.conditionId
            every { isEnabled } returns false
        }
        every { notificationManager.automaticZenRules } returns mapOf(MODE_ID to disabledMode)

        manager.requestSensorUpdate()

        coVerify { sensorRepository.replaceAllAttributes(homeAssistantModeSensor.id, modeAttributes(true, false)) }
    }

    @Test
    fun `Given the mode off in each way when updating then each has its own icon so a change to the attributes alone is sent`() = runTest(testDispatcher) {
        manager.requestSensorUpdate()
        verifyModeSensorStored(state = false, icon = ICON_NOT_ADDED)

        homeAssistantMode.add()
        manager.requestSensorUpdate()
        verifyModeSensorStored(state = false, icon = ICON_OFF)

        val disabledMode = mockk<AutomaticZenRule> {
            every { conditionId } returns addedMode.captured.conditionId
            every { isEnabled } returns false
        }
        every { notificationManager.automaticZenRules } returns mapOf(MODE_ID to disabledMode)
        manager.requestSensorUpdate()
        verifyModeSensorStored(state = false, icon = ICON_DISABLED)
    }

    @Test
    fun `Given mode turned on when updating then the sensor has the on icon`() = runTest(testDispatcher) {
        homeAssistantMode.add()
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_TRUE

        manager.requestSensorUpdate()

        verifyModeSensorStored(state = true, icon = ICON_ON)
    }

    @Test
    fun `Given device where modes are not managed by the user when updating then nothing is read for the mode sensor`() = runTest(testDispatcher) {
        every { notificationManager.areAutomaticZenRulesUserManaged() } returns false

        manager.requestSensorUpdate()

        verify(exactly = 0) { notificationManager.isNotificationPolicyAccessGranted }
        coVerify(exactly = 0) { sensorRepository.get(homeAssistantModeSensor.id) }
    }

    @Test
    fun `Given mode sensor disabled when updating then Android is not asked about the mode`() = runTest(testDispatcher) {
        coEvery { sensorRepository.get(homeAssistantModeSensor.id) } returns
            listOf(Sensor(id = homeAssistantModeSensor.id, serverId = SERVER_ID, enabled = false, state = ""))

        manager.requestSensorUpdate()

        verify(exactly = 0) { notificationManager.automaticZenRules }
        coVerify(exactly = 0) { sensorRepository.update(match { it.id == homeAssistantModeSensor.id }) }
    }

    @Test
    fun `Given no Do Not Disturb access when updating then nothing is stored for the mode sensor`() = runTest(testDispatcher) {
        every { notificationManager.isNotificationPolicyAccessGranted } returns false

        manager.requestSensorUpdate()

        coVerify(exactly = 0) { sensorRepository.update(match { it.id == homeAssistantModeSensor.id }) }
    }

    @Test
    fun `Given a refresh paused before storing the mode state when a later refresh reads a new state then the new state is stored last`() = runTest(testDispatcher) {
        homeAssistantMode.add()
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

        // Only by the older refresh: the newer one waits for the older one to store
        verify(exactly = 1) { notificationManager.getAutomaticZenRuleState(MODE_ID) }
        releaseFirstStore.complete(Unit)
        olderRefresh.join()
        newerRefresh.join()

        assertEquals(listOf("false", "true"), storedStates)
    }
}
