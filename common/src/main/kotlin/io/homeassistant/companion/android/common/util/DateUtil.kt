package io.homeassistant.companion.android.common.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun LocalDateTime.formatForLocal(dateTimeStyle: FormatStyle, locale: Locale = Locale.getDefault()): String {
    return format(DateTimeFormatter.ofLocalizedDateTime(dateTimeStyle).withLocale(locale))
}
