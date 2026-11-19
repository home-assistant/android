package io.homeassistant.companion.android.common.modes

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.service.notification.Condition
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.util.SdkVersion
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
private const val OTHER_MODE_ID = "other-mode-id"
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
    private val testDispatcher = StandardTestDispatcher()

    /** The app's modes in Android, by id. */
    private val modesInAndroid = mutableMapOf<String, AutomaticZenRule>()

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
        every { notificationManager.automaticZenRules } answers { modesInAndroid.toMap() }
        every { notificationManager.addAutomaticZenRule(capture(addedMode)) } answers {
            modesInAndroid[MODE_ID] = addedMode.captured
            MODE_ID
        }
        every { notificationManager.removeAutomaticZenRule(any()) } answers {
            modesInAndroid.remove(firstArg<String>()) != null
        }
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_FALSE

        createMode()
    }

    @After
    fun tearDown() {
        SdkVersion.resetSdkInt()
    }

    /** Creates a new instance, like after the app process is restarted. */
    private fun createMode() {
        homeAssistantMode = HomeAssistantMode(context, notificationManager, testDispatcher)
    }

    /** @return the condition id the app gives its mode, read from a mode it added */
    private suspend fun modeConditionId(): Uri {
        homeAssistantMode.add()
        return addedMode.captured.conditionId
    }

    private fun mode(conditionId: Uri, enabled: Boolean = true): AutomaticZenRule = mockk {
        every { this@mockk.conditionId } returns conditionId
        every { isEnabled } returns enabled
    }

    private suspend fun isAdded() = homeAssistantMode.getState()?.added == true

    private suspend fun isActive() = homeAssistantMode.getState()?.active

    private fun assertModeChangeIsUserAction(active: Boolean, expectedState: Int) = runTest(testDispatcher) {
        homeAssistantMode.add()
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
        assertFalse(homeAssistantMode.add())
        assertFalse(isAdded())
        assertTrue(homeAssistantMode.remove())
        assertNull(isActive())
        assertEquals(HomeAssistantMode.ChangeResult.Failed, homeAssistantMode.setActive(true))

        verify(exactly = 0) { notificationManager.areAutomaticZenRulesUserManaged() }
        verify(exactly = 0) { notificationManager.automaticZenRules }
    }

    @Test
    fun `Given device where modes are not managed by the user when adding then mode is not supported or added`() = runTest(testDispatcher) {
        every { notificationManager.areAutomaticZenRulesUserManaged() } returns false

        assertFalse(homeAssistantMode.isSupported())
        assertFalse(homeAssistantMode.add())
        verify(exactly = 0) { notificationManager.addAutomaticZenRule(any()) }
    }

    @Test
    fun `Given mode not added when adding twice then it is added once as a mode the user can turn on`() = runTest(testDispatcher) {
        assertFalse(isAdded())

        assertTrue(homeAssistantMode.add())
        assertTrue(homeAssistantMode.add())

        assertTrue(isAdded())
        verify(exactly = 1) { notificationManager.addAutomaticZenRule(any()) }
        assertTrue(addedMode.captured.isManualInvocationAllowed)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, addedMode.captured.interruptionFilter)
    }

    @Test
    fun `Given mode added before the app restarted when checking then it is found by its condition id`() = runTest(testDispatcher) {
        val conditionId = modeConditionId()
        createMode()

        assertTrue(isAdded())
        assertTrue(homeAssistantMode.add())

        verify(exactly = 1) { notificationManager.addAutomaticZenRule(any()) }
        assertEquals(conditionId, modesInAndroid.getValue(MODE_ID).conditionId)
    }

    @Test
    fun `Given another mode of the app when checking then it is not taken for the Home Assistant mode`() = runTest(testDispatcher) {
        // For example the one Android creates for setInterruptionFilter
        modesInAndroid[OTHER_MODE_ID] = mode(Uri.parse("condition://other"))

        assertFalse(isAdded())
        assertEquals(HomeAssistantMode.ChangeResult.NotAdded, homeAssistantMode.setActive(true))
        assertTrue(homeAssistantMode.remove())

        verify(exactly = 0) { notificationManager.removeAutomaticZenRule(any()) }
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

        homeAssistantMode.add()

        assertEquals(
            ComponentName(context.packageName, MODE_SETTINGS_ACTIVITY),
            addedMode.captured.configurationActivity,
        )
    }

    @Test
    fun `Given no mode settings screen when adding mode then Android links to the launcher activity`() = runTest(testDispatcher) {
        homeAssistantMode.add()

        assertEquals(ComponentName(context.packageName, LAUNCH_ACTIVITY), addedMode.captured.configurationActivity)
    }

    @Test
    fun `Given Android refuses the mode when adding then it is reported as not added`() = runTest(testDispatcher) {
        every { notificationManager.addAutomaticZenRule(any()) } throws IllegalArgumentException("Rule limit")

        assertFalse(homeAssistantMode.add())
        assertFalse(isAdded())
    }

    @Test
    fun `Given no Do Not Disturb access when using the mode then failure is handled`() = runTest(testDispatcher) {
        every { notificationManager.automaticZenRules } throws SecurityException("No access")
        every { notificationManager.isNotificationPolicyAccessGranted } returns false

        assertFalse(homeAssistantMode.hasAccess())
        assertFalse(homeAssistantMode.add())
        assertFalse(isAdded())
        assertFalse(homeAssistantMode.remove())
        assertNull(isActive())
        assertEquals(HomeAssistantMode.ChangeResult.NoAccess, homeAssistantMode.setActive(true))
        verify(exactly = 0) { notificationManager.setAutomaticZenRuleState(any(), any()) }
    }

    @Test
    fun `Given mode added when removing then it is gone from Android`() = runTest(testDispatcher) {
        homeAssistantMode.add()

        assertTrue(homeAssistantMode.remove())

        assertFalse(isAdded())
        verify(exactly = 1) { notificationManager.removeAutomaticZenRule(MODE_ID) }
    }

    @Test
    fun `Given the mode was added twice when removing then both are removed`() = runTest(testDispatcher) {
        val conditionId = modeConditionId()
        modesInAndroid[OTHER_MODE_ID] = mode(conditionId)

        assertTrue(homeAssistantMode.remove())

        assertTrue(modesInAndroid.isEmpty())
    }

    @Test
    fun `Given the mode was added twice and Android keeps one when removing then the other is still removed`() = runTest(testDispatcher) {
        val conditionId = modeConditionId()
        modesInAndroid[OTHER_MODE_ID] = mode(conditionId)
        every { notificationManager.removeAutomaticZenRule(MODE_ID) } returns false

        assertFalse(homeAssistantMode.remove())

        verify(exactly = 1) { notificationManager.removeAutomaticZenRule(OTHER_MODE_ID) }
        assertEquals(setOf(MODE_ID), modesInAndroid.keys)
    }

    @Test
    fun `Given mode not added when removing then nothing is removed`() = runTest(testDispatcher) {
        assertTrue(homeAssistantMode.remove())

        verify(exactly = 0) { notificationManager.removeAutomaticZenRule(any()) }
    }

    @Test
    fun `Given Android keeps the mode when removing then it is reported as still added`() = runTest(testDispatcher) {
        homeAssistantMode.add()
        every { notificationManager.removeAutomaticZenRule(any()) } returns false

        assertFalse(homeAssistantMode.remove())
    }

    @Test
    fun `Given mode not added when reading its state then it is off and not added`() = runTest(testDispatcher) {
        assertEquals(
            HomeAssistantMode.State(added = false, enabled = false, active = false),
            homeAssistantMode.getState(),
        )
    }

    @Test
    fun `Given mode disabled in Android when reading its state then it is added but not enabled`() = runTest(testDispatcher) {
        modesInAndroid[MODE_ID] = mode(modeConditionId(), enabled = false)

        assertEquals(
            HomeAssistantMode.State(added = true, enabled = false, active = false),
            homeAssistantMode.getState(),
        )
    }

    @Test
    fun `Given mode added when reading its state then Android's state is reported`() = runTest(testDispatcher) {
        homeAssistantMode.add()

        assertEquals(false, isActive())
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_TRUE
        assertEquals(true, isActive())
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_UNKNOWN
        assertNull(isActive())
    }

    @Test
    fun `Given mode not added when turning it on or off then it is not added`() = runTest(testDispatcher) {
        assertEquals(HomeAssistantMode.ChangeResult.NotAdded, homeAssistantMode.setActive(true))
        assertEquals(HomeAssistantMode.ChangeResult.NotAdded, homeAssistantMode.setActive(false))

        verify(exactly = 0) { notificationManager.addAutomaticZenRule(any()) }
        verify(exactly = 0) { notificationManager.setAutomaticZenRuleState(any(), any()) }
    }

    @Test
    fun `Given mode added when turning it on then Android receives it as a user action so a manual change cannot block it`() = assertModeChangeIsUserAction(active = true, expectedState = Condition.STATE_TRUE)

    @Test
    fun `Given mode added when turning it off then Android receives it as a user action so a manual change cannot block it`() = assertModeChangeIsUserAction(active = false, expectedState = Condition.STATE_FALSE)

    @Test
    fun `Given Android leaves the mode on when turning it off then the result says it was not applied`() = runTest(testDispatcher) {
        homeAssistantMode.add()
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_TRUE

        assertEquals(HomeAssistantMode.ChangeResult.NotApplied, homeAssistantMode.setActive(false))
    }

    @Test
    fun `Given Android cannot tell the mode state after turning it off then the result says it was not applied`() = runTest(testDispatcher) {
        homeAssistantMode.add()
        every { notificationManager.getAutomaticZenRuleState(MODE_ID) } returns Condition.STATE_UNKNOWN

        assertEquals(HomeAssistantMode.ChangeResult.NotApplied, homeAssistantMode.setActive(false))
    }

    @Test
    fun `Given user disabled the mode in Android when turning it on then nothing is changed`() = runTest(testDispatcher) {
        modesInAndroid[MODE_ID] = mode(modeConditionId(), enabled = false)

        assertEquals(HomeAssistantMode.ChangeResult.DisabledInAndroid, homeAssistantMode.setActive(true))

        verify(exactly = 0) { notificationManager.setAutomaticZenRuleState(any(), any()) }
    }
}
