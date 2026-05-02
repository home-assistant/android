package io.homeassistant.companion.android.settings.sensor.healthconnect

import android.app.Application
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.sensor.SensorDao
import io.homeassistant.companion.android.database.sensor.SensorSetting
import io.homeassistant.companion.android.database.sensor.SensorSettingType
import io.homeassistant.companion.android.sensors.HealthConnectSensorManager
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectChangesWorker
import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectDataType
import io.homeassistant.companion.android.sensors.healthconnect.HealthConnectSyncPreferences
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * UI state for the Health Connect settings screen.
 *
 * @property isLoading whether the initial preference read is still in flight.
 * @property isAvailable whether Health Connect itself is installed/usable on the device.
 *  When false the screen shows an explanation and disables the toggle so users don't
 *  flip a flag whose worker would never run.
 * @property realtimeSyncEnabled the current state of the real-time-sync opt-in flag.
 * @property enableAllInProgress whether the "enable all sensors" job is mid-flight.
 *  The button stays disabled while this is true so a double-tap can't fire two perm
 *  request flows at once.
 */
data class HealthConnectSettingsUiState(
    val isLoading: Boolean = true,
    val isAvailable: Boolean = false,
    val realtimeSyncEnabled: Boolean = false,
    val enableAllInProgress: Boolean = false,
    /** Number of Health Connect sensors with at least one enabled row in the DB. */
    val enabledSensorCount: Int = 0,
    /** Total number of HC sensors the catalogue exposes (sum of sensorIds across data types). */
    val totalSensorCount: Int = 0,
)

/**
 * Backs the [HealthConnectSettingsFragment] Compose screen.
 *
 * Owns three side effects:
 *  - persisting the real-time-sync flag through [HealthConnectSyncPreferences],
 *  - starting/stopping [HealthConnectChangesWorker] to match it, and
 *  - the "enable everything" bulk action that flips on every HC sensor (across every
 *    server), enables write permissions per sensor, and emits the union of read+write
 *    permission strings on [enableAllRequested] so the fragment can launch the HC
 *    permission contract.
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
    private val sensorDao: SensorDao,
    private val serverManager: ServerManager,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HealthConnectSettingsUiState())
    val uiState: StateFlow<HealthConnectSettingsUiState> = _uiState.asStateFlow()

    /**
     * Emits the full set of read + write permission strings the fragment should hand to
     * [HealthConnectSensorManager.getPermissionResultContract]. Replay = 0 because we
     * don't want a stale request firing again on rotation; buffer = 1 with DROP_OLDEST so
     * a rapid second click never queues a second launch.
     */
    private val _enableAllRequested = MutableSharedFlow<Set<String>>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val enableAllRequested: SharedFlow<Set<String>> = _enableAllRequested.asSharedFlow()

    init {
        val totalSensors = HealthConnectDataType.all.flatMap { it.sensorIds }.toSet()
        viewModelScope.launch {
            val available = clientProvider.get() != null
            val enabled = preferences.isRealtimeSyncEnabled()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isAvailable = available,
                    realtimeSyncEnabled = enabled,
                    totalSensorCount = totalSensors.size,
                )
            }
        }
        // Live count of HC sensors that have an enabled row anywhere (any server). Drives
        // the "X/Y enabled" indicator on the bulk-enable row so the user gets immediate
        // confirmation after granting permissions / running the bulk action.
        viewModelScope.launch {
            sensorDao.getAllFlow()
                .map { all ->
                    all.asSequence()
                        .filter { it.enabled && it.id in totalSensors }
                        .map { it.id }
                        .distinct()
                        .count()
                }
                .distinctUntilChanged()
                .collect { count ->
                    _uiState.update { it.copy(enabledSensorCount = count) }
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

    /**
     * Bulk-enable every Health Connect sensor for every registered server, flip the
     * per-sensor "Allow writes from HA" toggle on, and surface a single permission
     * request that asks for both read and write access to all known data types at once.
     *
     * The intended audience is power users who already know they want the full surface;
     * the screen pairs the button with a confirmation dialog so a casual tap doesn't
     * silently dump 50+ permission requests into Health Connect.
     */
    fun enableAll() {
        if (_uiState.value.enableAllInProgress) return
        _uiState.update { it.copy(enableAllInProgress = true) }
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val hcManager = SensorReceiver.MANAGERS.firstOrNull { it is HealthConnectSensorManager }
                    ?: return@launch
                val serverIds = serverManager.servers().map { it.id }
                val sensors = hcManager.getAvailableSensors(context)
                sensors.forEach { sensor ->
                    if (serverIds.isNotEmpty()) {
                        sensorDao.setSensorEnabled(sensor.id, serverIds, enabled = true)
                    }
                    sensorDao.add(
                        SensorSetting(
                            sensorId = sensor.id,
                            name = HealthConnectSensorManager.SETTING_ALLOW_WRITES,
                            value = "true",
                            valueType = SensorSettingType.TOGGLE,
                            enabled = true,
                        ),
                    )
                }
                // The write-permission cache (HealthConnectSensorManager.allowWritesCache)
                // refreshes from the persisted SensorSetting rows on the next
                // requestSensorUpdate. We don't poke it directly here — the fragment
                // calls SensorReceiver.updateAllSensors() after the permission contract
                // returns, which exercises that refresh automatically.
                val perms = buildSet {
                    HealthConnectDataType.all.forEach { dataType ->
                        add(dataType.readPermission)
                        add(dataType.writePermission)
                    }
                }
                _enableAllRequested.tryEmit(perms)
            } catch (e: Exception) {
                Timber.w(e, "enableAll failed")
            } finally {
                _uiState.update { it.copy(enableAllInProgress = false) }
            }
        }
    }
}
