package io.homeassistant.companion.android.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class AppLockViewModel @Inject constructor(private val serverManager: ServerManager) : ViewModel() {

    /**
     * Set the app active state for a specific server. Uses [viewModelScope] to ensure the
     * operation completes even if the calling Activity/Fragment lifecycle scope is cancelled,
     * which is critical for the app lock feature.
     */
    fun setAppActive(serverId: Int?, active: Boolean) {
        viewModelScope.launch(Dispatchers.Default) {
            val resolvedId = serverId ?: ServerManager.SERVER_ID_ACTIVE
            serverManager.getServer(resolvedId)?.let {
                try {
                    serverManager.integrationRepository(it.id).setAppActive(active)
                } catch (e: IllegalArgumentException) {
                    Timber.w(e, "Cannot set app active $active for server $resolvedId")
                }
            }
        }
    }

    suspend fun isAppLocked(serverId: Int?): Boolean {
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
