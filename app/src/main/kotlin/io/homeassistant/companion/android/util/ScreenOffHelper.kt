package io.homeassistant.companion.android.util

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

private const val SCREEN_OFF_WAKE_LOCK_TAG = "HomeAssistant::NotificationScreenOffWakeLock"

/**
 * Turns the screen off for the screen off server command by cutting the display power with
 * [DevicePolicyManager.lockNow] while a [PowerManager.PARTIAL_WAKE_LOCK] keeps the device awake
 * and reachable by the server. This requires the user to activate [ScreenOffAdminReceiver] as
 * device admin, and the command is ignored when the device has a secure keyguard (PIN, pattern or
 * password) since turning the screen off would then lock the device behind credentials.
 */
@Singleton
class ScreenOffHelper @Inject constructor(@ApplicationContext private val context: Context) {

    private val devicePolicyManager by lazy { context.getSystemService<DevicePolicyManager>() }
    private val keyguardManager by lazy { context.getSystemService<KeyguardManager>() }
    private val powerManager by lazy { context.getSystemService<PowerManager>() }
    private val adminComponent by lazy { ComponentName(context, ScreenOffAdminReceiver::class.java) }

    private var screenOffWakeLock: PowerManager.WakeLock? = null

    /** Whether the screen is currently turned off by [turnScreenOff]. */
    internal val isScreenOff: Boolean
        get() = screenOffWakeLock != null

    /** Whether [ScreenOffAdminReceiver] is active as device admin so [turnScreenOff] can be used. */
    fun canTurnScreenOff(): Boolean = devicePolicyManager?.isAdminActive(adminComponent) == true

    /**
     * Turns the screen off while the device stays awake, unlocked and connected. Calling it again
     * while the screen is already off turns the display off again in case it was woken manually.
     *
     * @return `true` if the screen was turned off, `false` when the device admin is not active or
     * a secure keyguard is set
     */
    // The wake lock is held until the screen is turned back on, no timeout would be safe
    @SuppressLint("WakelockTimeout")
    fun turnScreenOff(): Boolean {
        val devicePolicyManager = devicePolicyManager ?: return false
        if (keyguardManager?.isDeviceSecure != false) {
            Timber.w("Not turning the screen off, the secure keyguard would lock the device")
            return false
        }
        return try {
            if (screenOffWakeLock == null) {
                // Keep the CPU and network awake so the device stays reachable by the server
                screenOffWakeLock = powerManager
                    ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, SCREEN_OFF_WAKE_LOCK_TAG)
                    ?.apply { acquire() }
            }
            devicePolicyManager.lockNow()
            true
        } catch (e: SecurityException) {
            Timber.e(e, "Device admin is not active, cannot turn the screen off")
            turnScreenOn()
            false
        }
    }

    /**
     * Releases the wake lock held while the screen was off so the device can sleep normally
     * again. Safe to call when the screen was not turned off.
     *
     * @return `true` if the screen was turned off before, `false` if there was nothing to do
     */
    fun turnScreenOn(): Boolean {
        val wakeLock = screenOffWakeLock ?: return false
        screenOffWakeLock = null
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        return true
    }
}
