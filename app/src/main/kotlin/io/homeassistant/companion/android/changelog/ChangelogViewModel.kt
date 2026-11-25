package io.homeassistant.companion.android.changelog

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.modes.HomeAssistantModeManager
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.di.qualifiers.IsAutomotive
import io.homeassistant.companion.android.settings.SettingsActivity
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val RELEASE_TAG_URL_PREFIX = "https://github.com/home-assistant/android/releases/tag/"

/**
 * @property versionName The version of the app currently running, without the flavor suffix.
 * @property releaseUrl URL of the GitHub release page of [versionName].
 * @property currentPlatform The platform the app is running on, emphasized on the entries' tags.
 * @property sections The changelog content, in display order, without the actions this device cannot perform.
 */
data class ChangelogUiState(
    val versionName: String,
    val releaseUrl: String,
    val currentPlatform: ChangelogPlatform,
    val sections: List<ChangelogSection>,
)

/**
 * ViewModel of the changelog screen.
 *
 * Marks the changelog as seen as soon as the screen is shown, so it is not shown again for the
 * current app version regardless of how the user leaves the screen.
 */
@HiltViewModel
class ChangelogViewModel @VisibleForTesting internal constructor(
    prefsRepository: PrefsRepository,
    isAutomotive: Boolean,
    appVersion: AppVersion,
    homeAssistantModeManager: HomeAssistantModeManager,
    changelog: Changelog,
) : ViewModel() {

    @Inject
    constructor(
        prefsRepository: PrefsRepository,
        @IsAutomotive isAutomotive: Boolean,
        appVersion: AppVersion,
        homeAssistantModeManager: HomeAssistantModeManager,
    ) : this(prefsRepository, isAutomotive, appVersion, homeAssistantModeManager, currentChangelog)

    val uiState: StateFlow<ChangelogUiState> = MutableStateFlow(
        run {
            val versionWithoutFlavor = appVersion.name.removeSuffix("-${BuildConfig.FLAVOR}")
            ChangelogUiState(
                versionName = versionWithoutFlavor,
                releaseUrl = RELEASE_TAG_URL_PREFIX + versionWithoutFlavor,
                currentPlatform = if (isAutomotive) ChangelogPlatform.AUTOMOTIVE else ChangelogPlatform.APP,
                sections = changelog.toSections().withoutModeAction(homeAssistantModeManager.isSupported()),
            )
        },
    ).asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepository.markChangelogSeen(appVersion.code)
        }
    }
}

/**
 * @return these sections, without the link to the setting of the Home Assistant mode on a device without Modes
 * ([modeSupported] `false`), where the setting is hidden
 */
private fun List<ChangelogSection>.withoutModeAction(modeSupported: Boolean): List<ChangelogSection> {
    if (modeSupported) return this
    val modeAction = ChangelogAction.OpenSettings(SettingsActivity.Deeplink.HomeAssistantMode)
    return map { section ->
        section.copy(entries = section.entries.map { if (it.action == modeAction) it.copy(action = null) else it })
    }
}
