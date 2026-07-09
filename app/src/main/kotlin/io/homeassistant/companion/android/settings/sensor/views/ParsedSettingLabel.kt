package io.homeassistant.companion.android.settings.sensor.views

/**
 * Primary and optional secondary text parsed from a sensor setting label.
 *
 * Sensor allow-list entries are formatted as `"<primary>\n(<secondary>)"`; older single-line
 * entries have no secondary value. [secondary] is `null` whenever the source label has a single
 * line.
 */
internal data class ParsedSettingLabel(val primary: String, val secondary: String?)

/**
 * Splits a sensor setting label into a [ParsedSettingLabel].
 *
 * Sensor allow-list entries arrive formatted as `"<primary>\n(<secondary>)"` (for example
 * `"Chrome\n(com.google.chrome)"`). Only the first newline is used to separate the two parts;
 * any additional newlines remain inside the secondary value. Surrounding parentheses are stripped
 * from the secondary value when both the leading `(` and trailing `)` are present, matching
 * [String.removeSurrounding] semantics.
 *
 * Shared between the Material 3 bottom sheet ([SensorDetailSettingSheet]) and the legacy Material 2
 * dialog row (`SensorDetailSettingRow`) so both render identical primary/secondary text.
 */
internal fun parseSettingLabel(label: String): ParsedSettingLabel {
    val parts = label.split("\n", limit = 2)
    val primary = parts[0]
    val secondary = if (parts.size == 2) parts[1].removeSurrounding("(", ")") else null
    return ParsedSettingLabel(primary = primary, secondary = secondary)
}
