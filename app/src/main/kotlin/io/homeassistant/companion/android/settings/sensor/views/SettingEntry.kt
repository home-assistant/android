package io.homeassistant.companion.android.settings.sensor.views

/**
 * Identifier-and-label pair displayed in the sensor allow-list sheet and the legacy setting dialog.
 *
 * Sensor allow-list labels arrive formatted as `"<primary>\n(<secondary>)"` (for example
 * `"Chrome\n(com.google.chrome)"`); older single-line labels have no secondary value. Only the
 * first newline separates the two parts; any additional newlines remain inside the secondary
 * value. Surrounding parentheses are stripped from the secondary value when both the leading `(`
 * and trailing `)` are present, matching [String.removeSurrounding] semantics.
 */
data class SettingEntry(val id: String, val label: String) {
    /** First line of [label]. */
    val primary: String

    /** Remainder of [label] after the first newline without surrounding parentheses, or `null` for single-line labels. */
    val secondary: String?

    init {
        val parts = label.split("\n", limit = 2)
        primary = parts[0]
        secondary = if (parts.size == 2) parts[1].removeSurrounding("(", ")") else null
    }
}
