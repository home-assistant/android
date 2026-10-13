package io.homeassistant.companion.android.applock

import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
     * Return whether the user turned app lock on for the given server (or the active server when
     * [serverId] is `null`), regardless of whether the app is currently locked.
     *
     * Returns `false` if the server cannot be resolved or its authentication repository is unavailable.
     */
    suspend fun isAppLockEnabled(serverId: Int? = null): Boolean {
        val resolvedId = serverId ?: ServerManager.SERVER_ID_ACTIVE
        return serverManager.getServer(resolvedId)?.let {
            try {
                serverManager.authenticationRepository(it.id).isLockEnabledRaw()
            } catch (e: IllegalStateException) {
                Timber.w(e, "Cannot determine app lock setting for server $resolvedId")
                false
            }
        } ?: false
    }

    /**
     * Emits whether app lock is enabled for the active server, re-emitting whenever that setting or
     * the registered servers change, and emitting the current value immediately on collection.
     *
     * Emits `false` while no server can be resolved. Switching the active server is reflected once the
     * server list changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun isAppLockEnabledFlow(): Flow<Boolean> = serverManager.serversFlow
        .map { serverManager.getServer()?.id }
        .distinctUntilChanged()
        .flatMapLatest { serverId ->
            if (serverId == null) {
                flowOf(false)
            } else {
                serverManager.authenticationRepository(serverId).lockEnabledRawFlow()
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
            } catch (e: IllegalStateException) {
                Timber.w(e, "Cannot determine app locked state for server $resolvedId")
                false
            }
        } ?: false
    }
}
