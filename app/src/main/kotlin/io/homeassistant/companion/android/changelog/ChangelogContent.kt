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
            contentRes = commonR.string.changelog_entry_assistant_volume_sensor,
            platforms = setOf(ChangelogPlatform.APP, ChangelogPlatform.AUTOMOTIVE, ChangelogPlatform.WEAR),
        ),
        ChangelogEntry(
            contentRes = commonR.string.changelog_entry_assistant_volume_command,
            platforms = setOf(ChangelogPlatform.APP, ChangelogPlatform.AUTOMOTIVE),
        ),
    ),
    improved = listOf(
        ChangelogEntry(
            contentRes = commonR.string.changelog_entry_health_connect,
            platforms = setOf(ChangelogPlatform.APP, ChangelogPlatform.AUTOMOTIVE),
        ),
        ChangelogEntry(
            contentRes = commonR.string.changelog_entry_entity_widgets,
            platforms = setOf(ChangelogPlatform.APP),
            action = ChangelogAction.OpenWidgetConfig(WidgetType.Entity),
        ),
        ChangelogEntry(
            contentRes = commonR.string.changelog_entry_media_player_widgets,
            platforms = setOf(ChangelogPlatform.APP),
            action = ChangelogAction.OpenWidgetConfig(WidgetType.MediaPlayer),
        ),
        ChangelogEntry(
            contentRes = commonR.string.changelog_entry_tiles,
            platforms = setOf(ChangelogPlatform.APP),
            action = ChangelogAction.OpenSettings(SettingsActivity.Deeplink.QSTile()),
        ),
        ChangelogEntry(
            contentRes = commonR.string.changelog_entry_matter_commissioning,
            platforms = setOf(ChangelogPlatform.APP),
        ),
        ChangelogEntry(
            contentRes = commonR.string.changelog_entry_assist,
            platforms = setOf(ChangelogPlatform.APP, ChangelogPlatform.AUTOMOTIVE),
            action = ChangelogAction.OpenAssist,
        ),
    ),
    fixed = listOf(
        ChangelogEntry(
            contentRes = commonR.string.changelog_entry_bug_fixes,
            platforms = setOf(ChangelogPlatform.APP, ChangelogPlatform.AUTOMOTIVE, ChangelogPlatform.WEAR),
        ),
    ),
)
