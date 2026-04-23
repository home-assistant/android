package io.homeassistant.companion.android.launch.applock

import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Manages the app lock state
 */
class AppLockManager @Inject constructor(private val serverManager: ServerManager) {

    /**
     * Checks whether the app is locked for the active server.
     *
     * @return `true` if app lock is enabled and the session has expired, `false` otherwise
     *   (including when no server is registered)
     */
    suspend fun isAppLocked(): Boolean {
        return try {
            if (!serverManager.isRegistered()) return false

            serverManager.integrationRepository().isAppLocked()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cannot determine app locked state")
            false
        }
    }

    /**
     * Marks the app as active or inactive for the active server.
     */
    suspend fun setAppActive(active: Boolean) {
        try {
            if (!serverManager.isRegistered()) return

            serverManager.integrationRepository().setAppActive(active)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cannot set app active state to $active")
        }
    }
}
