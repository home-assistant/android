package io.homeassistant.companion.android.settings

import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.preference.SwitchPreference
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.applock.AppLockStateManager
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.modes.HomeAssistantModeManager
import io.homeassistant.companion.android.di.ServerManagerModule
import io.homeassistant.companion.android.testing.unit.seedFakeAndroidId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/** Tests how the settings drive the switch of the mode through the activity's lifecycle and app lock. */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@UninstallModules(ServerManagerModule::class)
@HiltAndroidTest
class SettingsFragmentModeTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private var hasAccess = false
    private var added = false

    @BindValue
    @JvmField
    val appLockStateManager: AppLockStateManager = mockk(relaxUnitFun = true) {
        coEvery { isAppLocked(any()) } returns false
        coEvery { isAppLockEnabled(any()) } returns false
    }

    // Without a server, so nothing opens the database
    @BindValue
    @JvmField
    val serverManager: ServerManager = mockk(relaxed = true) {
        every { serversFlow } returns flowOf(emptyList())
        coEvery { servers() } returns emptyList()
    }

    @BindValue
    @JvmField
    val homeAssistantModeManager: HomeAssistantModeManager = mockk {
        every { isSupported() } returns true
        coEvery { hasAccess() } answers { hasAccess }
        coEvery { getState() } answers { state() }
        coEvery { add() } answers {
            added = hasAccess
            added
        }
    }

    private fun state() = if (hasAccess) HomeAssistantModeManager.State(added = added, enabled = added, active = false) else null

    /** Activities started by the test, closed after it so their work ends before the test environment does. */
    private val activities = mutableListOf<ActivityController<*>>()

    @After
    fun tearDown() {
        activities.forEach { it.close() }
    }

    @Before
    fun setUp() {
        ApplicationProvider.getApplicationContext<Context>().seedFakeAndroidId()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun launch(): ActivityController<SettingsActivity> = Robolectric.buildActivity(
        SettingsActivity::class.java,
        SettingsActivity.newInstance(ApplicationProvider.getApplicationContext(), SettingsActivity.Deeplink.HomeAssistantMode),
    ).setup().also { activities += it }.also {
        idle()
        it.windowFocusChanged(true)
        idle()
    }

    private fun ActivityController<SettingsActivity>.modeSwitch(): SwitchPreference {
        val fragment = get().supportFragmentManager.findFragmentById(R.id.content) as SettingsFragment
        return checkNotNull(fragment.findPreference(SettingsFragment.PREF_HOME_ASSISTANT_MODE))
    }

    @Test
    fun `Given the state of the mode not read yet when the settings open then the switch waits for it`() {
        hasAccess = true
        added = true
        val read = CompletableDeferred<HomeAssistantModeManager.State?>()
        coEvery { homeAssistantModeManager.getState() } coAnswers { read.await() }

        val settings = launch()

        assertFalse(settings.modeSwitch().isEnabled)
        read.complete(state())
        idle()
        assertTrue(settings.modeSwitch().isEnabled)
        assertTrue(settings.modeSwitch().isChecked)
    }

    @Test
    fun `Given no access when the switch is turned on then access is requested and the mode added once unlocked after returning`() {
        val settings = launch()

        settings.modeSwitch().callChangeListener(true)
        idle()
        assertEquals(
            Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
            shadowOf(settings.get()).nextStartedActivity?.action,
        )

        settings.pause().stop()
        hasAccess = true
        settings.restart().resume()
        idle()
        coVerify(exactly = 0) { homeAssistantModeManager.add() }
        settings.windowFocusChanged(true)
        idle()

        coVerify(exactly = 1) { homeAssistantModeManager.add() }
        assertTrue(settings.modeSwitch().isChecked)
    }

    @Test
    fun `Given the settings left while access is checked when the check ends then the access screen does not open`() {
        val settings = launch()
        val accessChecked = CompletableDeferred<Boolean>()
        coEvery { homeAssistantModeManager.hasAccess() } coAnswers { accessChecked.await() }

        settings.modeSwitch().callChangeListener(true)
        idle()
        settings.pause()
        accessChecked.complete(false)
        idle()

        assertNull(shadowOf(settings.get()).nextStartedActivity)
        coEvery { homeAssistantModeManager.hasAccess() } answers { hasAccess }
        hasAccess = true
        settings.resume()
        settings.windowFocusChanged(true)
        idle()
        coVerify(exactly = 0) { homeAssistantModeManager.add() }
    }

    @Test
    fun `Given a change waiting for access when the settings are recreated then it is made once unlocked`() {
        val settings = launch()
        settings.modeSwitch().callChangeListener(true)
        idle()

        hasAccess = true
        settings.recreate()
        idle()
        settings.windowFocusChanged(true)
        idle()

        coVerify(exactly = 1) { homeAssistantModeManager.add() }
        assertTrue(settings.modeSwitch().isChecked)
    }

    @Test
    fun `Given the app locked when the switch is changed under the blur then nothing changes`() {
        val settings = launch()
        coEvery { appLockStateManager.isAppLocked(any()) } returns true

        settings.modeSwitch().callChangeListener(true)
        idle()

        assertNull(shadowOf(settings.get()).nextStartedActivity)
    }
}
