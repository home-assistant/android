package io.homeassistant.companion.android.settings

import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.applock.AppLockStateManager
import io.homeassistant.companion.android.testing.unit.seedFakeAndroidId
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertFalse
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

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class SettingsActivityTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private var appLocked = true

    @BindValue
    @JvmField
    val appLockStateManager: AppLockStateManager = mockk(relaxUnitFun = true) {
        coEvery { isAppLocked(any()) } answers { appLocked }
        coEvery { isAppLockEnabled(any()) } answers { appLocked }
    }

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

    /** Opens the settings, without a settings screen in them: a saved state opens none, which these tests do not need. */
    private fun launch(): ActivityController<SettingsActivity> = Robolectric.buildActivity(
        SettingsActivity::class.java,
        SettingsActivity.newInstance(ApplicationProvider.getApplicationContext()),
    ).setup(Bundle()).also { activities += it }

    /** @return whether a touch reaches a view over the whole settings */
    private fun touchReachesSettings(activity: SettingsActivity): Boolean {
        var touched = false
        activity.findViewById<ViewGroup>(R.id.content_full_screen).addView(
            View(activity).apply {
                setOnTouchListener { _, _ ->
                    touched = true
                    true
                }
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        shadowOf(Looper.getMainLooper()).idle()

        val now = SystemClock.uptimeMillis()
        val touch = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 300f, 0)
        activity.dispatchTouchEvent(touch)
        touch.recycle()
        return touched
    }

    /** @return whether accessibility services, which act without touches, see a view of the settings */
    private fun accessibilityReachesSettings(activity: SettingsActivity): Boolean {
        val setting = View(activity).apply { contentDescription = "A setting" }
        activity.findViewById<ViewGroup>(R.id.content_full_screen).addView(setting)
        shadowOf(Looper.getMainLooper()).idle()
        return setting.isImportantForAccessibility
    }

    @Test
    fun `Given the app locked or not when accessibility services look at the settings then they only see them unlocked`() {
        appLocked = true
        val locked = launch().get()
        assertFalse(accessibilityReachesSettings(locked))
        assertFalse(locked.findViewById<View>(R.id.blurView).isImportantForAccessibility)

        appLocked = false
        assertTrue(accessibilityReachesSettings(launch().get()))
    }

    @Test
    fun `Given the app locked when touching the settings then the touch does not reach them`() {
        appLocked = true

        assertFalse(touchReachesSettings(launch().get()))
    }

    @Test
    fun `Given the app not locked when touching the settings then the touch reaches them`() {
        appLocked = false

        assertTrue(touchReachesSettings(launch().get()))
    }

    @Test
    fun `Given the lock not read yet when the settings open then a touch does not reach them`() {
        coEvery { appLockStateManager.isAppLocked(any()) } coAnswers { CompletableDeferred<Boolean>().await() }

        assertFalse(touchReachesSettings(launch().get()))
    }

    @Test
    fun `Given unlocked settings left when they come back before the lock is read then a touch does not reach them`() {
        appLocked = false
        val settings = launch()
        coEvery { appLockStateManager.isAppLocked(any()) } coAnswers { CompletableDeferred<Boolean>().await() }

        settings.pause().resume()

        assertFalse(touchReachesSettings(settings.get()))
    }

    @Test
    fun `Given a lock check ending after the settings were left when they come back then a touch waits for the next check`() {
        appLocked = false
        val firstCheck = CompletableDeferred<Boolean>()
        coEvery { appLockStateManager.isAppLocked(any()) } coAnswers { firstCheck.await() }
        val settings = launch()

        settings.pause()
        firstCheck.complete(false)
        shadowOf(Looper.getMainLooper()).idle()
        coEvery { appLockStateManager.isAppLocked(any()) } coAnswers { CompletableDeferred<Boolean>().await() }
        settings.resume()

        assertFalse(touchReachesSettings(settings.get()))
    }

    /** Presses Enter, which acts on the view that has the focus, like a keyboard or a D-pad does. */
    private fun pressEnter(activity: SettingsActivity) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `Given a focused setting when the settings come back locked then a key reaches it only once unlocked`() {
        appLocked = false
        val settings = launch()
        val setting = Switch(settings.get()).apply { isFocusableInTouchMode = true }
        settings.get().findViewById<ViewGroup>(R.id.content_full_screen).addView(setting)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(setting.requestFocus())

        appLocked = true
        settings.pause().resume()
        pressEnter(settings.get())
        assertFalse(setting.isChecked)

        appLocked = false
        settings.windowFocusChanged(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(setting.requestFocus())
        pressEnter(settings.get())
        assertTrue(setting.isChecked)
    }

    @Test
    fun `Given the settings left while the focus check runs when it ends then the app is not marked active`() {
        appLocked = false
        val settings = launch()
        clearMocks(appLockStateManager, answers = false)
        val lockChecked = CompletableDeferred<Boolean>()
        coEvery { appLockStateManager.isAppLocked(any()) } coAnswers { lockChecked.await() }

        settings.windowFocusChanged(true)
        shadowOf(Looper.getMainLooper()).idle()
        settings.pause()
        lockChecked.complete(false)
        shadowOf(Looper.getMainLooper()).idle()

        coVerify(exactly = 0) { appLockStateManager.setAppActive(any(), true) }
    }

    @Test
    fun `Given a focused setting when the settings come back unlocked then it has the focus again`() {
        appLocked = false
        val settings = launch()
        val setting = Switch(settings.get()).apply { isFocusableInTouchMode = true }
        settings.get().findViewById<ViewGroup>(R.id.content_full_screen).addView(setting)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(setting.requestFocus())

        settings.pause().resume()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(setting.isFocused)
    }
}
