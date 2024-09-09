package io.homeassistant.companion.android.common.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateFormatter {

    fun formatDateFromMillis(milliseconds: Long): String {
        val date = Date(milliseconds)

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }
}
