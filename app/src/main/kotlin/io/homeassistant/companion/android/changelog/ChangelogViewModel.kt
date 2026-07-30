package io.homeassistant.companion.android.changelog

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.di.qualifiers.IsAutomotive
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val RELEASE_TAG_URL_PREFIX = "https://github.com/home-assistant/android/releases/tag/"

/** Flavor suffixes appended to the version name that are meaningless to the user. */
private val FLAVOR_SUFFIXES = listOf("-full", "-minimal")

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
    changelogRepository: ChangelogRepository,
    isAutomotive: Boolean,
    rawVersionName: String,
) : ViewModel() {

    @Inject
    constructor(
        changelogRepository: ChangelogRepository,
        @IsAutomotive isAutomotive: Boolean,
    ) : this(changelogRepository, isAutomotive, BuildConfig.VERSION_NAME)

    val uiState: StateFlow<ChangelogUiState> = MutableStateFlow(
        run {
            val versionName = FLAVOR_SUFFIXES.fold(rawVersionName, String::removeSuffix)
            ChangelogUiState(
                versionName = versionName,
                releaseUrl = RELEASE_TAG_URL_PREFIX + versionName,
                currentPlatform = if (isAutomotive) ChangelogPlatform.AUTOMOTIVE else ChangelogPlatform.APP,
                sections = currentChangelog.toSections(),
            )
        },
    ).asStateFlow()

    init {
        viewModelScope.launch {
            changelogRepository.markChangelogSeen()
        }
    }
}
