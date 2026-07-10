package io.homeassistant.companion.android.util

import android.app.Application
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class ScreenOffHelperTest {

    private lateinit var application: Application
    private lateinit var helper: ScreenOffHelper

    @Before
    fun setUp() {
        ShadowPowerManager.clearWakeLocks()
        application = ApplicationProvider.getApplicationContext()
        helper = ScreenOffHelper(application)
    }

    @Test
    fun `Given device admin not active when checking then the screen cannot be turned off`() {
        assertFalse(helper.canTurnScreenOff())
    }

    @Test
    fun `Given device admin active when checking then the screen can be turned off`() {
        activateDeviceAdmin()

        assertTrue(helper.canTurnScreenOff())
    }

    @Test
    fun `Given device admin active when turning screen off then the device stays awake`() {
        activateDeviceAdmin()

        assertTrue(helper.turnScreenOff())

        assertTrue(helper.isScreenOff)
        val wakeLock = ShadowPowerManager.getLatestWakeLock()
        assertNotNull(wakeLock)
        assertTrue(wakeLock.isHeld)
    }

    @Test
    fun `Given screen already off when turning screen off again then it is turned off again`() {
        activateDeviceAdmin()
        assertTrue(helper.turnScreenOff())

        assertTrue(helper.turnScreenOff())

        assertTrue(helper.isScreenOff)
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test
    fun `Given a secure keyguard when turning screen off then the screen stays on`() {
        activateDeviceAdmin()
        shadowOf(application.getSystemService(KeyguardManager::class.java)).setIsDeviceSecure(true)

        assertFalse(helper.turnScreenOff())

        assertFalse(helper.isScreenOff)
        assertNull(ShadowPowerManager.getLatestWakeLock())
    }

    @Test
    fun `Given screen turned off when turning screen on then the wake lock is released`() {
        activateDeviceAdmin()
        assertTrue(helper.turnScreenOff())

        assertTrue(helper.turnScreenOn())

        assertFalse(helper.isScreenOff)
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test
    fun `Given screen not turned off when turning screen on then there is nothing to do`() {
        assertFalse(helper.turnScreenOn())

        assertFalse(helper.isScreenOff)
    }

    @Test
    fun `Given device admin deactivated in between when turning screen off then the wake lock is released`() {
        val devicePolicyManager = mockk<DevicePolicyManager> {
            every { lockNow() } throws SecurityException("Device admin was deactivated")
        }
        val context = object : ContextWrapper(application) {
            override fun getSystemService(name: String): Any? = if (name == Context.DEVICE_POLICY_SERVICE) devicePolicyManager else super.getSystemService(name)
        }
        val helper = ScreenOffHelper(context)

        assertFalse(helper.turnScreenOff())

        assertFalse(helper.isScreenOff)
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test
    fun `Given screen was turned back on when turning screen off again then the device stays awake again`() {
        activateDeviceAdmin()
        assertTrue(helper.turnScreenOff())
        assertTrue(helper.turnScreenOn())

        assertTrue(helper.turnScreenOff())

        assertTrue(helper.isScreenOff)
        assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    private fun activateDeviceAdmin() {
        shadowOf(application.getSystemService(DevicePolicyManager::class.java))
            .setActiveAdmin(ComponentName(application, ScreenOffAdminReceiver::class.java))
    }
}
