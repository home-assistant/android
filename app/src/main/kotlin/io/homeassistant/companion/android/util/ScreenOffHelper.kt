package io.homeassistant.companion.android.util

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import timber.log.Timber

private const val SCREEN_ON_WAKE_LOCK_TAG = "HomeAssistant::NotificationScreenOnWakeLock"
private val SCREEN_ON_WAKE_LOCK_TIMEOUT = 30.seconds

/**
 * Turns the display off and back on for the screen off and screen on notification commands. The
 * display power is cut with [DevicePolicyManager.lockNow], which requires [ScreenOffAdminReceiver]
 * active as device admin and no secure keyguard. Only the display is affected: whether the app
 * stays reachable while the screen is off follows its connection settings, like the persistent
 * connection.
 */
@Singleton
class ScreenOffHelper @Inject constructor(@ApplicationContext private val context: Context) {

    private val devicePolicyManager by lazy { context.getSystemService<DevicePolicyManager>() }
    private val keyguardManager by lazy { context.getSystemService<KeyguardManager>() }
    private val powerManager by lazy { context.getSystemService<PowerManager>() }
    private val adminComponent by lazy { ComponentName(context, ScreenOffAdminReceiver::class.java) }

    /** Whether [ScreenOffAdminReceiver] is active as device admin so [turnScreenOff] can be used. */
    fun canTurnScreenOff(): Boolean = devicePolicyManager?.isAdminActive(adminComponent) == true

    /**
     * @return `true` if the screen was turned off, `false` when the device admin is not active or
     * a secure keyguard is set
     */
    fun turnScreenOff(): Boolean {
        val devicePolicyManager = devicePolicyManager ?: return false
        if (keyguardManager?.isDeviceSecure != false) {
            Timber.w("Not turning the screen off, the secure keyguard would lock the device")
            return false
        }
        return try {
            devicePolicyManager.lockNow()
            true
        } catch (e: SecurityException) {
            Timber.e(e, "Device admin is not active, cannot turn the screen off")
            false
        }
    }

    /** Wakes the screen up. */
    fun turnScreenOn() {
        val screenOnWakeLock = powerManager?.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            SCREEN_ON_WAKE_LOCK_TAG,
        )
        screenOnWakeLock?.acquire(SCREEN_ON_WAKE_LOCK_TIMEOUT.inWholeMilliseconds)
        screenOnWakeLock?.release()
    }
}
