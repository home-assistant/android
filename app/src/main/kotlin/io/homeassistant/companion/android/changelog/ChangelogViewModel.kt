package io.homeassistant.companion.android.changelog

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.util.AppVersion
import io.homeassistant.companion.android.common.util.AppVersionProvider
import io.homeassistant.companion.android.di.qualifiers.IsAutomotive
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
 * @property sections The changelog content, in display order.
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
class ChangelogViewModel @VisibleForTesting constructor(
    prefsRepository: PrefsRepository,
    isAutomotive: Boolean,
    appVersion: AppVersion,
) : ViewModel() {

    @Inject
    constructor(
        prefsRepository: PrefsRepository,
        @IsAutomotive isAutomotive: Boolean,
        appVersionProvider: AppVersionProvider,
    ) : this(prefsRepository, isAutomotive, appVersionProvider.invoke())

    val uiState: StateFlow<ChangelogUiState> = MutableStateFlow(
        run {
            val versionWithoutFlavor = appVersion.name.removeSuffix("-${BuildConfig.FLAVOR}")
            ChangelogUiState(
                versionName = versionWithoutFlavor,
                releaseUrl = RELEASE_TAG_URL_PREFIX + versionWithoutFlavor,
                currentPlatform = if (isAutomotive) ChangelogPlatform.AUTOMOTIVE else ChangelogPlatform.APP,
                sections = currentChangelog.toSections(),
            )
        },
    ).asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepository.markChangelogSeen(appVersion.code)
        }
    }
}
