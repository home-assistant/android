package io.homeassistant.companion.android.applock

import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Centralizes app lock state operations against a server's integration repository.
 *
 * All methods are suspend functions and resolve the active server when [serverId] is `null`.
 * Errors from a missing or invalid server are logged and never propagate so callers do not
 * have to handle them at every call site.
 */
@Singleton
class AppLockStateManager @Inject constructor(private val serverManager: ServerManager) {

    /**
     * Set the app active state for the given server (or the active server when [serverId] is `null`).
     *
     * No-op if the server cannot be resolved or its integration repository is unavailable.
     */
    suspend fun setAppActive(serverId: Int? = null, active: Boolean) {
        val resolvedId = serverId ?: ServerManager.SERVER_ID_ACTIVE
        serverManager.getServer(resolvedId)?.let {
            try {
                serverManager.integrationRepository(it.id).setAppActive(active)
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Cannot set app active $active for server $resolvedId")
            }
        }
    }

    /**
     * Return whether the given server (or the active server when [serverId] is `null`) is locked.
     *
     * Returns `false` if the server cannot be resolved or its integration repository is unavailable.
     */
    suspend fun isAppLocked(serverId: Int? = null): Boolean {
        val resolvedId = serverId ?: ServerManager.SERVER_ID_ACTIVE
        return serverManager.getServer(resolvedId)?.let {
            try {
                serverManager.integrationRepository(it.id).isAppLocked()
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Cannot determine app locked state for server $resolvedId")
                false
            }
        } ?: false
    }
}
