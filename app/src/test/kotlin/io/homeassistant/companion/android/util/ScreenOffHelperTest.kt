package io.homeassistant.companion.android.util

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.PowerManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ScreenOffHelperTest {

    private val devicePolicyManager = mockk<DevicePolicyManager>()
    private val keyguardManager = mockk<KeyguardManager>()
    private val powerManager = mockk<PowerManager>()
    private val context = mockk<Context> {
        every { getSystemService(DevicePolicyManager::class.java) } returns devicePolicyManager
        every { getSystemService(KeyguardManager::class.java) } returns keyguardManager
        every { getSystemService(PowerManager::class.java) } returns powerManager
    }
    private val screenOnLock = fakeWakeLock()
    private val helper = ScreenOffHelper(context)

    @BeforeEach
    fun setUp() {
        every { devicePolicyManager.isAdminActive(any()) } returns false
        every { devicePolicyManager.lockNow() } just Runs
        every { keyguardManager.isDeviceSecure } returns false
        every { powerManager.newWakeLock(any(), any()) } returns screenOnLock
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
    fun `Given device admin active when turning screen off then the display is locked`() {
        activateDeviceAdmin()

        assertTrue(helper.turnScreenOff())

        verify(exactly = 1) { devicePolicyManager.lockNow() }
    }

    @Test
    fun `Given a secure keyguard when turning screen off then the screen stays on`() {
        activateDeviceAdmin()
        every { keyguardManager.isDeviceSecure } returns true

        assertFalse(helper.turnScreenOff())

        verify(exactly = 0) { devicePolicyManager.lockNow() }
    }

    @Test
    fun `Given device admin deactivated in between when turning screen off then it reports failure`() {
        activateDeviceAdmin()
        every { devicePolicyManager.lockNow() } throws SecurityException("Device admin was deactivated")

        assertFalse(helper.turnScreenOff())
    }

    @Test
    fun `Given the screen off when turning screen on then the screen is woken up`() {
        helper.turnScreenOn()

        verifyOrder {
            screenOnLock.acquire(any<Long>())
            screenOnLock.release()
        }
    }

    private fun activateDeviceAdmin() {
        every { devicePolicyManager.isAdminActive(any()) } returns true
    }

    private fun fakeWakeLock(): PowerManager.WakeLock {
        var held = false
        return mockk {
            every { acquire() } answers { held = true }
            every { acquire(any()) } answers { held = true }
            every { release() } answers { held = false }
            every { isHeld } answers { held }
        }
    }
}
