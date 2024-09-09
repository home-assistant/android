package io.homeassistant.companion.android.common.util

import android.content.Context
import io.homeassistant.companion.android.common.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

object DateFormatter {

    fun formatDateFromMillis(milliseconds: Long): String {
        val date = Date(milliseconds)

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }

    fun formatTimeAndDateCompat(millis: Long): String {
        val calendar = GregorianCalendar.getInstance().apply {
            timeInMillis = millis
        }

        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormatter = SimpleDateFormat("MMM d", Locale.getDefault())

        return if (calendar.get(GregorianCalendar.HOUR_OF_DAY) == 0 && calendar.get(GregorianCalendar.MINUTE) == 0) {
            dateFormatter.format(calendar.time)
        } else {
            timeFormatter.format(calendar.time)
        }
    }

    fun formatHours(context: Context, hours: Int): String {
        return context.resources.getQuantityString(R.plurals.hours_format, hours, hours)
    }
}
