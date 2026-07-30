package io.homeassistant.companion.android.changelog

import androidx.annotation.StringRes
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.frontend.navigation.WidgetType
import io.homeassistant.companion.android.settings.SettingsActivity

/**
 * The platforms a changelog entry can apply to, in chip display order.
 */
enum class ChangelogPlatform(@field:StringRes val labelRes: Int) {
    APP(commonR.string.changelog_platform_app),
    AUTOMOTIVE(commonR.string.changelog_platform_automotive),
    WEAR(commonR.string.changelog_platform_wear),
}

/**
 * The kinds of changes a changelog groups its entries under.
 */
enum class ChangelogCategory(@field:StringRes val labelRes: Int) {
    NEW(commonR.string.changelog_category_new),
    IMPROVED(commonR.string.changelog_category_improved),
    FIXED(commonR.string.changelog_category_fixed),
}

/**
 * An action performed when the user taps a changelog entry. An entry with an action is rendered
 * as a clickable row with a chevron.
 */
sealed interface ChangelogAction {
    /** Opens [url] in the browser or the matching app. */
    data class OpenUrl(val url: String) : ChangelogAction

    /** Opens the settings on the screen targeted by [deeplink]. */
    data class OpenSettings(val deeplink: SettingsActivity.Deeplink) : ChangelogAction

    /** Opens the configuration screen of [widgetType] without a preselected entity. */
    data class OpenWidgetConfig(val widgetType: WidgetType) : ChangelogAction
}

/**
 * One change of a release.
 *
 * @property contentRes The change description, a string resource from `strings_changelog.xml`
 * in `:common`. Escaped inline HTML (like `&lt;b&gt;`) is rendered as styling.
 * @property platforms The platforms this change applies to.
 * @property action Optional action performed when the user taps the entry.
 */
data class ChangelogEntry(
    @param:StringRes val contentRes: Int,
    val platforms: Set<ChangelogPlatform>,
    val action: ChangelogAction? = null,
)

/**
 * The changes of one release, one field per category so a category cannot appear twice.
 * A category without entries is not displayed.
 */
data class Changelog(
    val new: List<ChangelogEntry> = emptyList(),
    val improved: List<ChangelogEntry> = emptyList(),
    val fixed: List<ChangelogEntry> = emptyList(),
) {
    /** The non-empty sections of this changelog, in display order. */
    fun toSections(): List<ChangelogSection> = listOf(
        ChangelogSection(ChangelogCategory.NEW, new),
        ChangelogSection(ChangelogCategory.IMPROVED, improved),
        ChangelogSection(ChangelogCategory.FIXED, fixed),
    ).filter { it.entries.isNotEmpty() }
}

/**
 * A group of [entries] of the same [category], displayed under one header. Derived from a
 * [Changelog] through [Changelog.toSections], not meant to be built directly.
 */
data class ChangelogSection(val category: ChangelogCategory, val entries: List<ChangelogEntry>)
