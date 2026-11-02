package io.homeassistant.companion.android.changelog

import io.homeassistant.companion.android.common.R as commonR

/**
 * The changelog content of the current release, with the displayed strings in the dedicated
 * `strings_changelog.xml` file of `:common` so they get translated. Entries not yet translated
 * fall back to English.
 *
 * Update this together with the release notes; the changelog screenshot test pins the rendered
 * result.
 */
internal val currentChangelog = Changelog(
    new = listOf(),
    improved = listOf(),
    fixed = listOf(
        ChangelogEntry(
            contentRes = commonR.string.changelog_entry_bug_fixes,
            platforms = setOf(ChangelogPlatform.APP, ChangelogPlatform.AUTOMOTIVE, ChangelogPlatform.WEAR),
        ),
    ),
)
