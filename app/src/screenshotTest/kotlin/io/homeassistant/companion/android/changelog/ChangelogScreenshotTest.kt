package io.homeassistant.companion.android.changelog

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.homeassistant.companion.android.changelog.ui.ChangelogScreenContent
import io.homeassistant.companion.android.common.compose.theme.HAThemeForPreview
import io.homeassistant.companion.android.util.compose.HAPreviews

/**
 * Renders the changelog with the real authored content of the current release, so a content
 * change shows up as a screenshot diff.
 */
class ChangelogScreenshotTest {

    @PreviewTest
    @HAPreviews
    @Composable
    fun `Changelog with current release content`() {
        HAThemeForPreview {
            ChangelogScreenContent(
                uiState = ChangelogUiState(
                    versionName = "20XX.X.X",
                    releaseUrl = "https://github.com/home-assistant/android/releases/tag/2026.7.6",
                    currentPlatform = ChangelogPlatform.APP,
                    sections = currentChangelog.toSections(),
                ),
                onCloseClick = {},
                onActionClick = {},
            )
        }
    }
}
