package io.homeassistant.companion.android.common.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.text.Spanned
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.text.HtmlCompat
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.colorFilter
import com.mikepenz.iconics.utils.toAndroidIconCompat
import com.vdurmont.emoji.EmojiParser
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.util.CHANNEL_GENERAL
import io.homeassistant.companion.android.common.util.cancel
import java.util.Locale
import timber.log.Timber

object NotificationData {
    const val TAG = "MessagingService"
    const val TITLE = "title"
    const val MESSAGE = "message"
    const val WEBHOOK_ID = "webhook_id"
    const val GROUP_PREFIX = "group_"
    const val CHANNEL = "channel"
    const val IMPORTANCE = "importance"
    const val LED_COLOR = "ledColor"
    const val VIBRATION_PATTERN = "vibrationPattern"
    const val NOTIFICATION_ICON = "notification_icon"
    const val ALERT_ONCE = "alert_once"
    const val COMMAND = "command"

    // Channel streams
    const val ALARM_STREAM = "alarm_stream"
    const val ALARM_STREAM_MAX = "alarm_stream_max"
    const val MUSIC_STREAM = "music_stream"
    const val NOTIFICATION_STREAM = "notification_stream"
    const val RING_STREAM = "ring_stream"
    const val SYSTEM_STREAM = "system_stream"
    const val CALL_STREAM = "call_stream"
    const val DTMF_STREAM = "dtmf_stream"

    const val MEDIA_STREAM = "media_stream"
    val ALARM_STREAMS = listOf(ALARM_STREAM, ALARM_STREAM_MAX)

    // special action constants
    const val CLEAR_NOTIFICATION = "clear_notification"
}

fun createChannelID(channelName: String): String {
    return channelName
        .trim()
        .lowercase(Locale.ROOT)
        .replace(" ", "_")
}

fun handleChannel(
    context: Context,
    notificationManagerCompat: NotificationManagerCompat,
    data: Map<String, String>,
): String {
    // Define some values for a default channel
    var channelID = CHANNEL_GENERAL
    var channelName = context.getString(R.string.general)

    if (!data[NotificationData.CHANNEL].isNullOrEmpty()) {
        channelID = createChannelID(data[NotificationData.CHANNEL].toString())
        channelName = data[NotificationData.CHANNEL].toString().trim()
    }

    // Since android Oreo notification channel is needed.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelID,
            channelName,
            handleImportance(data),
        )

        if (channelName == NotificationData.ALARM_STREAM) {
            handleChannelSound(context, channel)
        }

        setChannelLedColor(context, data, channel)
        setChannelVibrationPattern(data, channel)
        notificationManagerCompat.createNotificationChannel(channel)
    }
    return channelID
}

@RequiresApi(Build.VERSION_CODES.N)
fun handleImportance(data: Map<String, String>): Int {
    when (data[NotificationData.IMPORTANCE]) {
        "high" -> {
            return NotificationManager.IMPORTANCE_HIGH
        }
        "low" -> {
            return NotificationManager.IMPORTANCE_LOW
        }
        "max" -> {
            return NotificationManager.IMPORTANCE_MAX
        }
        "min" -> {
            return NotificationManager.IMPORTANCE_MIN
        }
        else -> {
            return NotificationManager.IMPORTANCE_DEFAULT
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun handleChannelSound(context: Context, channel: NotificationChannel) {
    val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
        .setLegacyStreamType(AudioManager.STREAM_ALARM)
        .setUsage(AudioAttributes.USAGE_ALARM)
        .build()
    channel.setSound(
        RingtoneManager.getActualDefaultRingtoneUri(
            context,
            RingtoneManager.TYPE_ALARM,
        )
            ?: RingtoneManager.getActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE,
            ),
        audioAttributes,
    )
}

fun setChannelLedColor(context: Context, data: Map<String, String>, channel: NotificationChannel) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val ledColor = data[NotificationData.LED_COLOR]
        if (!ledColor.isNullOrBlank()) {
            channel.enableLights(true)
            channel.lightColor = parseColor(context, ledColor, R.color.colorPrimary)
        }
    }
}

fun setChannelVibrationPattern(data: Map<String, String>, channel: NotificationChannel) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val vibrationPattern = data[NotificationData.VIBRATION_PATTERN]
        val arrVibrationPattern = parseVibrationPattern(vibrationPattern)
        if (arrVibrationPattern.isNotEmpty()) {
            channel.vibrationPattern = arrVibrationPattern
        }
    }
}

fun parseVibrationPattern(vibrationPattern: String?): LongArray {
    if (!vibrationPattern.isNullOrBlank()) {
        val pattern = vibrationPattern.split(",").toTypedArray()
        val list = mutableListOf<Long>()
        pattern.forEach {
            val ms = it.trim().toLongOrNull()
            if (ms != null) {
                list.add(ms)
            }
        }
        if (list.isNotEmpty()) {
            return list.toLongArray()
        }
    }
    return LongArray(0)
}

fun parseColor(context: Context, colorString: String?, default: Int): Int {
    if (!colorString.isNullOrBlank()) {
        try {
            return colorString.toColorInt()
        } catch (e: Exception) {
            Timber.tag(NotificationData.TAG).e(e, "Unable to parse color")
        }
    }
    return ContextCompat.getColor(context, default)
}

fun handleSmallIcon(context: Context, builder: NotificationCompat.Builder, data: Map<String, String>) {
    val notificationIcon = data[NotificationData.NOTIFICATION_ICON] ?: ""
    if (notificationIcon.startsWith("mdi:") &&
        notificationIcon.substringAfter("mdi:").isNotBlank()
    ) {
        val iconName = notificationIcon.split(":")[1]
        val iconDrawable =
            IconicsDrawable(context, "cmd-$iconName")
        if (iconDrawable.icon != null) {
            builder.setSmallIcon(
                iconDrawable.colorFilter {
                    PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                }.toAndroidIconCompat(),
            )
        } else {
            builder.setSmallIcon(R.drawable.ic_stat_ic_notification)
        }
    } else {
        builder.setSmallIcon(R.drawable.ic_stat_ic_notification)
    }
}

fun getGroupNotificationBuilder(
    context: Context,
    channelId: String,
    group: String,
    data: Map<String, String>,
): NotificationCompat.Builder {
    val groupNotificationBuilder = NotificationCompat.Builder(context, channelId)
        .setStyle(
            NotificationCompat.BigTextStyle()
                .setSummaryText(
                    prepareText(group.substring(NotificationData.GROUP_PREFIX.length)),
                ),
        )
        .setGroup(group)
        .setGroupSummary(true)

    if (data[NotificationData.ALERT_ONCE].toBoolean()) {
        groupNotificationBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
    }
    handleColor(context, groupNotificationBuilder, data)
    handleSmallIcon(context, groupNotificationBuilder, data)
    return groupNotificationBuilder
}

fun prepareText(text: String): Spanned {
    // Replace control char \r\n, \r, \n and also \r\n, \r, \n as text literals in strings to <br>
    val brText = text.replace("(\r\n|\r|\n)|(\\\\r\\\\n|\\\\r|\\\\n)".toRegex(), "<br>")
    val emojiParsedText = EmojiParser.parseToUnicode(brText)
    return HtmlCompat.fromHtml(emojiParsedText, HtmlCompat.FROM_HTML_MODE_LEGACY)
}

fun handleColor(context: Context, builder: NotificationCompat.Builder, data: Map<String, String>) {
    val colorString = data["color"]
    val color = parseColor(context, colorString, R.color.colorPrimary)
    builder.color = color
}

fun handleText(builder: NotificationCompat.Builder, data: Map<String, String>) {
    data[NotificationData.TITLE]?.let {
        builder.setContentTitle(prepareText(it))
    }
    data[NotificationData.MESSAGE]?.let {
        val text = prepareText(it)
        builder.setContentText(text)
        builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
    }
}

fun clearNotification(context: Context, tag: String) {
    Timber.tag(NotificationData.TAG).d("Clearing notification with tag: $tag")
    val notificationManagerCompat = NotificationManagerCompat.from(context)
    val messageId = tag.hashCode()
    notificationManagerCompat.cancel(tag, messageId, true)
}

fun handleDeleteIntent(
    context: Context,
    builder: NotificationCompat.Builder,
    data: Map<String, String>,
    messageId: Int,
    group: String?,
    groupId: Int,
    databaseId: Long?,
) {
    val deleteIntent = Intent(context, NotificationDeleteReceiver::class.java).apply {
        putExtra(NotificationDeleteReceiver.EXTRA_DATA, HashMap(data))
        putExtra(NotificationDeleteReceiver.EXTRA_NOTIFICATION_GROUP, group)
        putExtra(NotificationDeleteReceiver.EXTRA_NOTIFICATION_GROUP_ID, groupId)
        putExtra(NotificationDeleteReceiver.EXTRA_NOTIFICATION_DB, databaseId)
    }
    val deletePendingIntent = PendingIntent.getBroadcast(
        context,
        messageId,
        deleteIntent,
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    builder.setDeleteIntent(deletePendingIntent)
}
