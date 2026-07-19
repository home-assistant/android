package io.homeassistant.companion.android.util

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import timber.log.Timber

private const val SCREEN_OFF_WAKE_LOCK_TAG = "HomeAssistant::NotificationScreenOffWakeLock"
private const val SCREEN_ON_WAKE_LOCK_TAG = "HomeAssistant::NotificationScreenOnWakeLock"
private val SCREEN_ON_WAKE_LOCK_TIMEOUT = 30.seconds

/**
 * Turns the display off and back on for the screen off and screen on notification commands,
 * keeping all wake lock handling in one place. The display power is cut with
 * [DevicePolicyManager.lockNow] while a partial wake lock keeps the device awake, which requires
 * [ScreenOffAdminReceiver] active as device admin and no secure keyguard. All functions must be
 * called from the main thread, the wake lock state is not synchronized.
 */
@Singleton
class ScreenOffHelper @Inject constructor(@ApplicationContext private val context: Context) {

    private val devicePolicyManager by lazy { context.getSystemService<DevicePolicyManager>() }
    private val keyguardManager by lazy { context.getSystemService<KeyguardManager>() }
    private val powerManager by lazy { context.getSystemService<PowerManager>() }
    private val adminComponent by lazy { ComponentName(context, ScreenOffAdminReceiver::class.java) }

    private var screenOffWakeLock: PowerManager.WakeLock? = null

    /** Whether the screen is currently turned off by [turnScreenOff]. */
    @VisibleForTesting
    internal val isScreenOff: Boolean
        get() = screenOffWakeLock != null

    /** Whether [ScreenOffAdminReceiver] is active as device admin so [turnScreenOff] can be used. */
    fun canTurnScreenOff(): Boolean = devicePolicyManager?.isAdminActive(adminComponent) == true

    /**
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
            releaseScreenOffWakeLock()
            false
        }
    }

    /**
     * Wakes the screen up, releasing the wake lock held by [turnScreenOff] when the screen was
     * turned off before.
     *
     * @return `true` if the screen was turned off before, `false` if it was not
     */
    fun turnScreenOn(): Boolean {
        val screenOnWakeLock = powerManager?.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            SCREEN_ON_WAKE_LOCK_TAG,
        )
        screenOnWakeLock?.acquire(SCREEN_ON_WAKE_LOCK_TIMEOUT.inWholeMilliseconds)
        // Released while the screen on wake lock is held so the device cannot suspend in between
        val wasScreenOff = releaseScreenOffWakeLock()
        screenOnWakeLock?.release()
        return wasScreenOff
    }

    /** @return `true` if the wake lock of [turnScreenOff] was released, `false` if none was held */
    private fun releaseScreenOffWakeLock(): Boolean {
        val wakeLock = screenOffWakeLock ?: return false
        screenOffWakeLock = null
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        return true
    }
}
