package io.homeassistant.companion.android.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.applock.AppLockStateManager
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * ViewModel shared between [SettingsActivity] and its fragments to drive app lock state.
 *
 * The fire-and-forget [setAppActive] uses [viewModelScope] so the write completes even
 * when the calling Activity/Fragment lifecycle scope is cancelled (e.g. on pause/stop),
 * which is critical to ensure the next launch sees the correct lock state.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val appLockStateManager: AppLockStateManager) : ViewModel() {

    fun setAppActive(serverId: Int?, active: Boolean) {
        viewModelScope.launch {
            appLockStateManager.setAppActive(serverId, active)
        }
    }

    suspend fun isAppLocked(serverId: Int?): Boolean = appLockStateManager.isAppLocked(serverId)
}
