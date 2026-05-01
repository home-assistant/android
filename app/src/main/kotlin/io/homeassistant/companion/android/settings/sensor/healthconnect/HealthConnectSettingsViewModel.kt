package io.homeassistant.companion.android.settings.sensor.healthconnect

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectChangesWorker
import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectSyncPreferences
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Health Connect settings screen.
 *
 * @property isLoading whether the initial preference read is still in flight.
 * @property isAvailable whether Health Connect itself is installed/usable on the device.
 *  When false the screen shows an explanation and disables the toggle so users don't
 *  flip a flag whose worker would never run.
 * @property realtimeSyncEnabled the current state of the real-time-sync opt-in flag.
 */
data class HealthConnectSettingsUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = false,
    val realtimeSyncEnabled: Boolean = false,
)

/**
 * Backs the [HealthConnectSettingsFragment] Compose screen.
 *
 * This view-model owns two side effects: persisting the real-time-sync flag through
 * [HealthConnectSyncPreferences], and starting/stopping [HealthConnectChangesWorker]
 * to match it. Both effects fire on the same flag flip — keeping them in one place
 * means the worker can never drift out of sync with the persisted preference (which
 * was the bug pattern in earlier WorkManager features in this repo).
 *
 * The HC client provider is injected as a [Provider] so the view-model can poll
 * availability without keeping a reference to a possibly-null client across the
 * lifecycle.
 */
@HiltViewModel
class HealthConnectSettingsViewModel @Inject constructor(
    application: Application,
    private val preferences: HealthConnectSyncPreferences,
    private val clientProvider: Provider<HealthConnectClient?>,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HealthConnectSettingsUiState())
    val uiState: StateFlow<HealthConnectSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val available = clientProvider.get() != null
            val enabled = preferences.isRealtimeSyncEnabled()
            _uiState.update {
                it.copy(isLoading = false, isAvailable = available, realtimeSyncEnabled = enabled)
            }
        }
    }

    fun setRealtimeSyncEnabled(enabled: Boolean) {
        // Update UI optimistically so the switch flips with no perceptible delay; the
        // suspending writes / WorkManager calls finish on the IO dispatcher in the
        // background. If WorkManager throws (it shouldn't on a healthy device) the worst
        // case is the persisted flag and the scheduled work disagree until next launch —
        // worth the snappier UX.
        _uiState.update { it.copy(realtimeSyncEnabled = enabled) }
        viewModelScope.launch {
            preferences.setRealtimeSyncEnabled(enabled)
            val context = getApplication<Application>()
            if (enabled) {
                HealthConnectChangesWorker.start(context)
            } else {
                HealthConnectChangesWorker.stop(context)
            }
        }
    }
}
