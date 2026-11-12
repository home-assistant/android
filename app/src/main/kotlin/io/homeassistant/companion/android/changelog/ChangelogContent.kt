package io.homeassistant.companion.android.changelog

import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.frontend.navigation.WidgetType
import io.homeassistant.companion.android.settings.SettingsActivity

/**
 * The changelog content of the current release, with the displayed strings in the dedicated
 * `strings_changelog.xml` file of `:common` so they get translated. Entries not yet translated
 * fall back to English.
 *
 * Update this together with the release notes; the changelog screenshot test pins the rendered
 * result.
 */
internal val currentChangelog = Changelog(
    new = listOf(
        ChangelogEntry(
            contentRes = commonR.string.changelog_entry_edge_gestures,
            platforms = setOf(ChangelogPlatform.APP, ChangelogPlatform.AUTOMOTIVE),
            action = ChangelogAction.OpenSettings(SettingsActivity.Deeplink.Gestures),
        ),
        ChangelogEntry(
            contentRes = commonR.string.changelog_entry_assist_listening_chime,
            platforms = setOf(ChangelogPlatform.APP),
            action = ChangelogAction.OpenSettings(SettingsActivity.Deeplink.AssistSettings),
            actionRequiresPlatform = true,
        ),
        ChangelogEntry(
            contentRes = commonR.string.changelog_entry_home_assistant_mode,
            platforms = setOf(ChangelogPlatform.APP),
        ),
    ),
    improved = listOf(),
    fixed = listOf(
        ChangelogEntry(
            contentRes = commonR.string.changelog_entry_fix_media_player_widget,
            platforms = setOf(ChangelogPlatform.APP),
            action = ChangelogAction.OpenWidgetConfig(WidgetType.MediaPlayer),
        ),
        ChangelogEntry(
            contentRes = commonR.string.changelog_entry_bug_fixes,
            platforms = setOf(ChangelogPlatform.APP, ChangelogPlatform.AUTOMOTIVE, ChangelogPlatform.WEAR),
        ),
    ),
)
