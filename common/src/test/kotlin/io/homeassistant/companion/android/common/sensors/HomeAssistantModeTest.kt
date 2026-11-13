package io.homeassistant.companion.android.common.sensors

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.service.notification.Condition
import android.service.notification.ConditionProviderService
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.LocalStorage
import io.homeassistant.companion.android.common.util.SdkVersion
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val MODE_ID = "mode-id"
private const val NEW_MODE_ID = "new-mode-id"
private const val MODE_SETTINGS_ACTIVITY = "ModeSettingsActivity"
private const val LAUNCH_ACTIVITY = "LaunchActivity"
private const val VANILLA_ICE_CREAM_SDK = 35
private const val UPSIDE_DOWN_CAKE_SDK = 34

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [VANILLA_ICE_CREAM_SDK])
class HomeAssistantModeTest {

    private val notificationManager: NotificationManager = mockk(relaxed = true)
    private val packageManager: PackageManager = mockk(relaxed = true)
    private val addedMode = slot<AutomaticZenRule>()
    private val storedValues = mutableMapOf<String, String?>()
    private val storage: LocalStorage = mockk {
        coEvery { getString(any()) } answers { storedValues[firstArg()] }
        coEvery { putString(any(), any()) } answers { storedValues[firstArg()] = secondArg() }
    }
    private val testDispatcher = StandardTestDispatcher()

    /** Whether the mode sensor is enabled; disabled by the app when the user removes the mode. */
    private var sensorEnabled = true
    private var removalsReported = 0

    private lateinit var context: Context
    private lateinit var homeAssistantMode: HomeAssistantMode

    @Before
    fun setUp() {
        context = spyk(ApplicationProvider.getApplicationContext<Context>())
        every { context.packageManager } returns packageManager
        every { packageManager.getLaunchIntentForPackage(any()) } returns
            Intent().setComponent(ComponentName(context.packageName, LAUNCH_ACTIVITY))

        every { notificationManager.areAutomaticZenRulesUserManaged() } returns true
        every { notificationManager.isNotificationPolicyAccessGranted } returns true
        every { notificationManager.automaticZenRules } returns emptyMap()
        every { notificationManager.addAutomaticZenRule(capture(addedMode)) } answers {
            givenModeInAndroid(addedMode.captured)
            MODE_ID
        }
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_FALSE

        createMode()
    }

    @After
    fun tearDown() {
        SdkVersion.resetSdkInt()
    }

    /** Creates a new instance, like after the app process is restarted. Stored state is kept. */
    private fun createMode() {
        homeAssistantMode = HomeAssistantMode(context, notificationManager, storage, testDispatcher)
    }

    /** Updates the mode like its sensor manager does. */
    private suspend fun update(): Boolean? = homeAssistantMode.update(
        isWanted = { sensorEnabled },
        onRemovedByUser = {
            removalsReported++
            sensorEnabled = false
        },
    )

    private fun launch(extra: String, modeId: String) = Intent().putExtra(extra, modeId)

    private fun givenModeInAndroid(rule: AutomaticZenRule) {
        every { notificationManager.automaticZenRules } returns mapOf(MODE_ID to rule)
        every { notificationManager.getAutomaticZenRule(MODE_ID) } returns rule
    }

    /** Android no longer has the mode: the user removed it, or Android did when Do Not Disturb access was revoked. */
    private fun givenModeGoneFromAndroid() {
        every { notificationManager.automaticZenRules } returns emptyMap()
        every { notificationManager.getAutomaticZenRule(MODE_ID) } returns null
    }

    private fun givenAccess(granted: Boolean) {
        every { notificationManager.isNotificationPolicyAccessGranted } returns granted
    }

    private fun assertModeChangeIsUserAction(active: Boolean, expectedState: Int) = runTest(testDispatcher) {
        update()
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns expectedState

        assertEquals(HomeAssistantMode.ChangeResult.Applied, homeAssistantMode.setActive(active))

        verify {
            notificationManager.setAutomaticZenRuleState(
                MODE_ID,
                match { it.state == expectedState && it.source == Condition.SOURCE_USER_ACTION },
            )
        }
    }

    @Test
    fun `Given Android older than 15 when using the mode then Android is not asked about it`() = runTest(testDispatcher) {
        SdkVersion.sdkInt = UPSIDE_DOWN_CAKE_SDK
        createMode()

        assertFalse(homeAssistantMode.isSupported())
        assertNull(update())
        assertEquals(HomeAssistantMode.ChangeResult.Failed, homeAssistantMode.setActive(true))
        assertFalse(homeAssistantMode.isOwnModeLaunch(Intent()))

        verify(exactly = 0) { notificationManager.areAutomaticZenRulesUserManaged() }
        verify(exactly = 0) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given device where modes are not managed by the user when updating then mode is not supported or added`() = runTest(testDispatcher) {
        every { notificationManager.areAutomaticZenRulesUserManaged() } returns false

        assertFalse(homeAssistantMode.isSupported())
        assertNull(update())
        verify(exactly = 0) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given enabled sensor when updating twice then mode is added and looked up once and reported off`() = runTest(testDispatcher) {
        assertEquals(false, update())
        assertEquals(false, update())

        verify(exactly = 1) { notificationManager.addAutomaticZenRule(any()) }
        verify(exactly = 1) { notificationManager.automaticZenRules }
        assertTrue(addedMode.captured.isManualInvocationAllowed)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, addedMode.captured.interruptionFilter)
    }

    @Test
    fun `Given mode turned on when updating then it is reported on`() = runTest(testDispatcher) {
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_TRUE

        assertEquals(true, update())
    }

    @Test
    fun `Given app declares a mode settings screen when adding mode then Android links to it`() = runTest(testDispatcher) {
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = context.packageName
                name = MODE_SETTINGS_ACTIVITY
            }
        }
        every {
            packageManager.resolveActivity(
                match<Intent> { it.action == NotificationManager.ACTION_AUTOMATIC_ZEN_RULE },
                any<PackageManager.ResolveInfoFlags>(),
            )
        } returns resolveInfo

        update()

        assertEquals(
            ComponentName(context.packageName, MODE_SETTINGS_ACTIVITY),
            addedMode.captured.configurationActivity,
        )
    }

    @Test
    fun `Given no mode settings screen when adding mode then Android links to the launcher activity`() = runTest(testDispatcher) {
        update()

        assertEquals(ComponentName(context.packageName, LAUNCH_ACTIVITY), addedMode.captured.configurationActivity)
    }

    @Test
    fun `Given Android cannot tell the mode state when updating then nothing is reported`() = runTest(testDispatcher) {
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_UNKNOWN

        assertNull(update())
    }

    @Test
    fun `Given Android refuses the mode when updating then nothing is reported and it is tried again`() = runTest(testDispatcher) {
        every { notificationManager.addAutomaticZenRule(any()) } throws IllegalArgumentException("Rule limit")

        assertNull(update())
        assertNull(update())

        verify(exactly = 2) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given Do Not Disturb access revoked meanwhile when updating then failure is handled`() = runTest(testDispatcher) {
        every { notificationManager.automaticZenRules } throws SecurityException("Access revoked")

        assertNull(update())
    }

    @Test
    fun `Given no Do Not Disturb access when updating twice then Android is not asked about the mode`() = runTest(testDispatcher) {
        givenAccess(false)

        assertNull(update())
        assertNull(update())

        verify(exactly = 0) { notificationManager.automaticZenRules }
        verify(exactly = 0) { notificationManager.getAutomaticZenRule(any()) }
        verify(exactly = 0) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given sensor never enabled when updating then Android is not asked about the mode`() = runTest(testDispatcher) {
        sensorEnabled = false

        assertNull(update())
        assertNull(update())

        verify(exactly = 0) { notificationManager.isNotificationPolicyAccessGranted }
        verify(exactly = 0) { notificationManager.automaticZenRules }
        verify(exactly = 0) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given sensor disabled after mode was added when updating then mode stays`() = runTest(testDispatcher) {
        update()
        sensorEnabled = false

        assertNull(update())

        verify(exactly = 0) { notificationManager.removeAutomaticZenRule(any()) }
    }

    @Test
    fun `Given sensor disabled and enabled again while the mode stayed when updating then the same mode is reported`() = runTest(testDispatcher) {
        update()
        sensorEnabled = false
        update()
        sensorEnabled = true
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_TRUE

        assertEquals(true, update())
        verify(exactly = 1) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given user removed the mode while the app was not running when updating then removal is reported once and mode is not added back`() = runTest(testDispatcher) {
        update()
        createMode()
        givenModeGoneFromAndroid()

        assertNull(update())
        assertNull(update())

        assertEquals(1, removalsReported)
        verify(exactly = 1) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given user removed the mode when the sensor is enabled again right away then mode is added again`() = runTest(testDispatcher) {
        update()
        givenModeGoneFromAndroid()
        update()
        sensorEnabled = true

        update()

        assertEquals(1, removalsReported)
        verify(exactly = 2) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given user removed the mode while the sensor was disabled when the sensor is enabled again then mode is added again`() = runTest(testDispatcher) {
        update()
        sensorEnabled = false
        update()
        givenModeGoneFromAndroid()
        sensorEnabled = true

        update()

        assertEquals(0, removalsReported)
        verify(exactly = 2) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given a server disabled the sensor after its last update when the user removes the mode and enables the sensor then mode is added again`() = runTest(testDispatcher) {
        update()
        // Disabled by a server during a sync, after the mode was refreshed: no update saw the sensor disabled
        sensorEnabled = false
        givenModeGoneFromAndroid()
        // The update run for Android's status broadcast about the removal, which is not skipped for a disabled sensor
        update()
        sensorEnabled = true

        update()

        assertEquals(0, removalsReported)
        verify(exactly = 2) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given sensor disabled when Android opens the mode settings screen and the user then removes the mode and enables the sensor then mode is added again`() = runTest(testDispatcher) {
        update()
        sensorEnabled = false
        update()
        assertTrue(homeAssistantMode.isOwnModeLaunch(launch(NotificationManager.EXTRA_AUTOMATIC_RULE_ID, MODE_ID)))
        givenModeGoneFromAndroid()
        sensorEnabled = true

        update()

        assertEquals(0, removalsReported)
        verify(exactly = 2) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given Do Not Disturb access revoked and granted again when updating then mode is added instead of reporting a removal`() = runTest(testDispatcher) {
        update()
        givenAccess(false)
        update()
        givenAccess(true)
        givenModeGoneFromAndroid()

        update()

        assertEquals(0, removalsReported)
        verify(exactly = 2) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given user removed the mode when a command turns it on before the next update then the new mode is tracked`() = runTest(testDispatcher) {
        update()
        givenModeGoneFromAndroid()
        every { notificationManager.addAutomaticZenRule(any()) } answers {
            every { notificationManager.getAutomaticZenRule(NEW_MODE_ID) } returns firstArg()
            NEW_MODE_ID
        }

        homeAssistantMode.setActive(true)
        update()

        assertEquals(0, removalsReported)
        verify(exactly = 2) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given mode added when Android opens the mode settings screen then only a launch for its id matches`() = runTest(testDispatcher) {
        update()

        assertTrue(homeAssistantMode.isOwnModeLaunch(launch(NotificationManager.EXTRA_AUTOMATIC_RULE_ID, MODE_ID)))
        assertTrue(homeAssistantMode.isOwnModeLaunch(launch(ConditionProviderService.EXTRA_RULE_ID, MODE_ID)))
        assertFalse(homeAssistantMode.isOwnModeLaunch(launch(NotificationManager.EXTRA_AUTOMATIC_RULE_ID, "other")))
        // Some Android versions may not pass the id; the screen it opens is harmless
        assertTrue(homeAssistantMode.isOwnModeLaunch(Intent()))
    }

    @Test
    fun `Given mode id forgotten while the mode stayed when Android opens its settings screen then the launch matches`() = runTest(testDispatcher) {
        update()
        givenAccess(false)
        update()
        givenAccess(true)

        assertTrue(homeAssistantMode.isOwnModeLaunch(launch(NotificationManager.EXTRA_AUTOMATIC_RULE_ID, MODE_ID)))
    }

    @Test
    fun `Given mode not added when turning it off then nothing is changed`() = runTest(testDispatcher) {
        assertEquals(HomeAssistantMode.ChangeResult.NotAdded, homeAssistantMode.setActive(false))

        verify(exactly = 0) { notificationManager.setAutomaticZenRuleState(any(), any()) }
        verify(exactly = 0) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given mode not added and sensor disabled when turning it on then mode is added and turned on`() = runTest(testDispatcher) {
        sensorEnabled = false
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_TRUE

        assertEquals(HomeAssistantMode.ChangeResult.Applied, homeAssistantMode.setActive(true))

        verify(exactly = 1) { notificationManager.addAutomaticZenRule(any()) }
        verify { notificationManager.setAutomaticZenRuleState(MODE_ID, match { it.state == Condition.STATE_TRUE }) }
    }

    @Test
    fun `Given mode added when turning it on then Android receives it as a user action so a manual change cannot block it`() = assertModeChangeIsUserAction(active = true, expectedState = Condition.STATE_TRUE)

    @Test
    fun `Given mode added when turning it off then Android receives it as a user action so a manual change cannot block it`() = assertModeChangeIsUserAction(active = false, expectedState = Condition.STATE_FALSE)

    @Test
    fun `Given Android leaves the mode on when turning it off then the result says it was not applied`() = runTest(testDispatcher) {
        update()
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_TRUE

        assertEquals(HomeAssistantMode.ChangeResult.NotApplied, homeAssistantMode.setActive(false))
    }

    @Test
    fun `Given Android cannot tell the mode state after turning it off then the result says it was not applied`() = runTest(testDispatcher) {
        update()
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_UNKNOWN

        assertEquals(HomeAssistantMode.ChangeResult.NotApplied, homeAssistantMode.setActive(false))
    }

    @Test
    fun `Given user disabled the mode in Android when turning it on then nothing is changed`() = runTest(testDispatcher) {
        update()
        every { notificationManager.getAutomaticZenRule(MODE_ID) } returns
            mockk<AutomaticZenRule> { every { isEnabled } returns false }

        assertEquals(HomeAssistantMode.ChangeResult.DisabledInAndroid, homeAssistantMode.setActive(true))

        verify(exactly = 0) { notificationManager.setAutomaticZenRuleState(any(), any()) }
    }
}
