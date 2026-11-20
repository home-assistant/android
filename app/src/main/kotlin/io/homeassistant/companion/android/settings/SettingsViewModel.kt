package io.homeassistant.companion.android.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.applock.AppLockStateManager
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _unlockedInForeground = MutableStateFlow(false)

    /**
     * Whether the settings are in the foreground and unlocked: `true` once the user unlocked them or no unlock was
     * needed, `false` again when they leave the foreground. A change the user started before leaving for another
     * screen waits for it.
     */
    val unlockedInForeground: StateFlow<Boolean> = _unlockedInForeground.asStateFlow()

    /** Records whether the settings are in the foreground and unlocked, see [unlockedInForeground]. */
    fun setUnlockedInForeground(unlocked: Boolean) {
        _unlockedInForeground.value = unlocked
    }

    fun setAppActive(serverId: Int?, active: Boolean) {
        viewModelScope.launch {
            appLockStateManager.setAppActive(serverId, active)
        }
    }

    suspend fun isAppLocked(serverId: Int?): Boolean = appLockStateManager.isAppLocked(serverId)

    suspend fun isAppLockEnabled(serverId: Int?): Boolean = appLockStateManager.isAppLockEnabled(serverId)
}
