package io.homeassistant.companion.android.changelog

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.di.qualifiers.IsAutomotive
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RELEASE_TAG_URL_PREFIX = "https://github.com/home-assistant/android/releases/tag/"

sealed interface ChangelogUiState {
    /** The actions of the entries are being resolved for this device. */
    data object Loading : ChangelogUiState

    /**
     * @property versionName The version of the app currently running, without the flavor suffix.
     * @property releaseUrl URL of the GitHub release page of [versionName].
     * @property currentPlatform The platform the app is running on, emphasized on the entries' tags.
     * @property sections The changelog content, in display order.
     */
    data class Content(
        val versionName: String,
        val releaseUrl: String,
        val currentPlatform: ChangelogPlatform,
        val sections: List<ChangelogSection>,
    ) : ChangelogUiState
}

/**
 * ViewModel of the changelog screen.
 *
 * Marks the changelog as seen as soon as the screen is shown, so it is not shown again for the
 * current app version regardless of how the user leaves the screen.
 */
@HiltViewModel
class ChangelogViewModel @VisibleForTesting constructor(
    prefsRepository: PrefsRepository,
    isAutomotive: Boolean,
    appVersion: AppVersion,
    private val sensorManagers: Set<SensorManager>,
    changelog: Changelog,
    private val backgroundDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    constructor(
        prefsRepository: PrefsRepository,
        @IsAutomotive isAutomotive: Boolean,
        appVersion: AppVersion,
        sensorManagers: Set<@JvmSuppressWildcards SensorManager>,
    ) : this(prefsRepository, isAutomotive, appVersion, sensorManagers, currentChangelog, Dispatchers.Default)

    private val _uiState = MutableStateFlow<ChangelogUiState>(ChangelogUiState.Loading)
    val uiState: StateFlow<ChangelogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val versionWithoutFlavor = appVersion.name.removeSuffix("-${BuildConfig.FLAVOR}")
            _uiState.value = ChangelogUiState.Content(
                versionName = versionWithoutFlavor,
                releaseUrl = RELEASE_TAG_URL_PREFIX + versionWithoutFlavor,
                currentPlatform = if (isAutomotive) ChangelogPlatform.AUTOMOTIVE else ChangelogPlatform.APP,
                sections = changelog.toSections().withSensorActionsFor(availableSensorIds()),
            )
            prefsRepository.markChangelogSeen(appVersion.code)
        }
    }

    /**
     * Ids of the sensors the device has, the only ones a [ChangelogAction.OpenSensor] may target.
     * Off the main thread since some managers query the system to know whether they have a sensor.
     */
    private suspend fun availableSensorIds(): Set<String> = withContext(backgroundDispatcher) {
        sensorManagers
            .filter { it.hasSensor() }
            .flatMap { manager -> manager.getAvailableSensors().map { it.id } }
            .toSet()
    }
}

/** Drops every [ChangelogAction.OpenSensor] whose sensor is not in [availableSensorIds]. */
private fun List<ChangelogSection>.withSensorActionsFor(availableSensorIds: Set<String>): List<ChangelogSection> =
    map { section ->
        section.copy(
            entries = section.entries.map { entry ->
                val action = entry.action
                if (action is ChangelogAction.OpenSensor && action.sensorId !in availableSensorIds) {
                    entry.copy(action = null)
                } else {
                    entry
                }
            },
        )
    }
