package io.homeassistant.companion.android.util

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.os.PowerManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
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
    private val screenOnReceiver = slot<BroadcastReceiver>()
    private val context = mockk<Context> {
        every { getSystemService(DevicePolicyManager::class.java) } returns devicePolicyManager
        every { getSystemService(KeyguardManager::class.java) } returns keyguardManager
        every { getSystemService(PowerManager::class.java) } returns powerManager
        every { registerReceiver(capture(screenOnReceiver), any(), anyNullable(), anyNullable()) } returns null
        every { unregisterReceiver(any()) } just Runs
    }
    private val screenOffLock = fakeWakeLock()
    private val screenOnLock = fakeWakeLock()
    private val helper = ScreenOffHelper(context)

    @BeforeEach
    fun setUp() {
        every { devicePolicyManager.isAdminActive(any()) } returns false
        every { devicePolicyManager.lockNow() } just Runs
        every { keyguardManager.isDeviceSecure } returns false
        every { powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, any()) } returns screenOffLock
        every { powerManager.newWakeLock(neq(PowerManager.PARTIAL_WAKE_LOCK), any()) } returns screenOnLock
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
        assertTrue(screenOffLock.isHeld)
        verify(exactly = 1) { devicePolicyManager.lockNow() }
    }

    @Test
    fun `Given screen already off when turning screen off again then it is turned off again`() {
        activateDeviceAdmin()
        assertTrue(helper.turnScreenOff())

        assertTrue(helper.turnScreenOff())

        assertTrue(helper.isScreenOff)
        assertTrue(screenOffLock.isHeld)
        verify(exactly = 1) { screenOffLock.acquire() }
        verify(exactly = 2) { devicePolicyManager.lockNow() }
    }

    @Test
    fun `Given a secure keyguard when turning screen off then the screen stays on`() {
        activateDeviceAdmin()
        every { keyguardManager.isDeviceSecure } returns true

        assertFalse(helper.turnScreenOff())

        assertFalse(helper.isScreenOff)
        verify(exactly = 0) { powerManager.newWakeLock(any(), any()) }
    }

    @Test
    fun `Given screen turned off when turning screen on then the wake lock is released while the screen wakes up`() {
        activateDeviceAdmin()
        assertTrue(helper.turnScreenOff())

        assertTrue(helper.turnScreenOn())

        assertFalse(helper.isScreenOff)
        assertFalse(screenOffLock.isHeld)
        // The wake lock is released while the screen on wake lock is held, so the device
        // cannot suspend in between
        verifyOrder {
            screenOnLock.acquire(any<Long>())
            screenOffLock.release()
            screenOnLock.release()
        }
    }

    @Test
    fun `Given screen not turned off when turning screen on then the screen is still woken up`() {
        assertFalse(helper.turnScreenOn())

        assertFalse(helper.isScreenOff)
        verify(exactly = 1) { screenOnLock.acquire(any<Long>()) }
        verify(exactly = 1) { screenOnLock.release() }
    }

    @Test
    fun `Given device admin deactivated in between when turning screen off then the wake lock is released`() {
        activateDeviceAdmin()
        every { devicePolicyManager.lockNow() } throws SecurityException("Device admin was deactivated")

        assertFalse(helper.turnScreenOff())

        assertFalse(helper.isScreenOff)
        assertFalse(screenOffLock.isHeld)
        // Cleaning up after the failure must not wake the screen
        verify(exactly = 0) { screenOnLock.acquire(any<Long>()) }
    }

    @Test
    fun `Given screen was turned back on when turning screen off again then the device stays awake again`() {
        activateDeviceAdmin()
        assertTrue(helper.turnScreenOff())
        assertTrue(helper.turnScreenOn())

        assertTrue(helper.turnScreenOff())

        assertTrue(helper.isScreenOff)
        assertTrue(screenOffLock.isHeld)
    }

    @Test
    fun `Given screen turned off when the screen turns on without the command then the wake lock is released`() {
        activateDeviceAdmin()
        assertTrue(helper.turnScreenOff())

        screenOnReceiver.captured.onReceive(context, mockk())

        assertFalse(helper.isScreenOff)
        assertFalse(screenOffLock.isHeld)
        verify(exactly = 1) { context.unregisterReceiver(screenOnReceiver.captured) }
    }

    @Test
    fun `Given screen turned off when turning screen on by command then the receiver is unregistered`() {
        activateDeviceAdmin()
        assertTrue(helper.turnScreenOff())

        assertTrue(helper.turnScreenOn())

        verify(exactly = 1) { context.unregisterReceiver(any()) }
    }

    @Test
    fun `Given screen already off when turning screen off again then the receiver is not registered twice`() {
        activateDeviceAdmin()
        assertTrue(helper.turnScreenOff())

        assertTrue(helper.turnScreenOff())

        verify(exactly = 1) { context.registerReceiver(any(), any(), anyNullable(), anyNullable()) }
    }

    @Test
    fun `Given device admin deactivated in between when turning screen off then the receiver is cleaned up`() {
        activateDeviceAdmin()
        every { devicePolicyManager.lockNow() } throws SecurityException("Device admin was deactivated")

        assertFalse(helper.turnScreenOff())

        verify(exactly = 1) { context.unregisterReceiver(any()) }
    }

    @Test
    fun `Given screen not turned off when turning screen on then no receiver is unregistered`() {
        assertFalse(helper.turnScreenOn())

        verify(exactly = 0) { context.unregisterReceiver(any()) }
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
