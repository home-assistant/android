package io.homeassistant.companion.android.notifications.callscreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.toBitmap
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.notifications.createChannelID
import io.homeassistant.companion.android.common.notifications.handleSmallIcon
import io.homeassistant.companion.android.common.util.CHANNEL_CALL_SCREEN
import io.homeassistant.companion.android.common.util.CHANNEL_CALL_SCREEN_STATUS
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.common.util.fromHaName
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.intentLaunchWithNavigateTo
import javax.inject.Inject
import javax.inject.Singleton

private val RING_VIBRATION = longArrayOf(0, 1000, 1000)
private const val ACTION_ICON_SIZE_DP = 24f

/** Id of the foreground notification shown while a call is connected. */
internal const val ONGOING_NOTIFICATION_ID = 0x0CA11

/** Pending intents of a ringing call, built by [CallScreenManager]. */
internal data class RingingIntents(
    val answer: PendingIntent,
    val decline: PendingIntent,
    val fullScreen: PendingIntent,
    /** Intents of the custom buttons, by [CallAction.index]. */
    val custom: Map<Int, PendingIntent>,
)

/** Builds the channels and notifications of the call screen. */
@Singleton
internal class CallScreenNotifications @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Make sure the ringing channel exists and return its id. An existing channel is never modified so
     * that edits made by the user stick.
     *
     * @param name channel name from the payload, the default call channel when `null`
     */
    @Suppress("ReturnCount")
    fun ensureRingChannel(name: String?): String {
        val id = name?.let { createChannelID(it) } ?: CHANNEL_CALL_SCREEN
        if (!SdkVersion.isAtLeast(Build.VERSION_CODES.O)) return id
        val manager = NotificationManagerCompat.from(context)
        if (manager.getNotificationChannel(id) != null) return id

        val channel = NotificationChannel(
            id,
            name?.trim() ?: context.getString(commonR.string.call_screen_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(
                ringtoneUri(),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            vibrationPattern = RING_VIBRATION
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
        return id
    }

    /** Make sure the silent channel used for silenced, ongoing and missed calls exists. */
    fun ensureStatusChannel(): String {
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            val channel = NotificationChannel(
                CHANNEL_CALL_SCREEN_STATUS,
                context.getString(commonR.string.call_screen_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
        return CHANNEL_CALL_SCREEN_STATUS
    }

    /** The caller shown by the system, named after the notification title with the poster as avatar. */
    fun buildPerson(config: CallScreenConfig, poster: Bitmap?): Person = Person.Builder()
        .setName(config.title ?: context.getString(commonR.string.app_name))
        .setIcon(poster?.let { IconCompat.createWithBitmap(it) })
        .setImportant(true)
        .build()

    /**
     * Build the incoming call notification.
     *
     * @param silenced `true` to repost the call on the silent channel, which stops ringtone and vibration
     * @param useCallStyle `false` to fall back to plain answer and decline actions when the system
     * rejects [NotificationCompat.CallStyle]
     */
    @Suppress("LongParameterList")
    fun buildRinging(
        config: CallScreenConfig,
        person: Person,
        intents: RingingIntents,
        silenced: Boolean,
        useCallStyle: Boolean = true,
    ): Notification {
        val channelId = if (silenced) ensureStatusChannel() else ensureRingChannel(config.channelName)
        val builder = baseBuilder(config, channelId)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(intents.fullScreen, true)
            .setContentIntent(intents.fullScreen)
            .setTimeoutAfter(config.ringTimeout.inWholeMilliseconds)

        if (!silenced && !SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            builder.setSound(ringtoneUri(), AudioManager.STREAM_RING).setVibrate(RING_VIBRATION)
        }

        if (useCallStyle) {
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(person, intents.decline, intents.answer)
                    .setIsVideo(config.background == CallBackground.Stream),
            )
        } else {
            builder.addAction(plainAction(config, CallActionRole.Decline, intents.decline))
            builder.addAction(plainAction(config, CallActionRole.Answer, intents.answer))
        }
        config.buttons.filter { it.role == CallActionRole.Custom }.forEach { action ->
            intents.custom[action.index]?.let { builder.addAction(customAction(action, it)) }
        }

        return builder.build().also {
            if (!silenced) it.flags = it.flags or Notification.FLAG_INSISTENT
        }
    }

    /** Build the foreground notification of a connected call. */
    fun buildOngoing(
        config: CallScreenConfig,
        person: Person,
        hangUp: PendingIntent,
        content: PendingIntent,
    ): Notification = baseBuilder(config, ensureStatusChannel())
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setOngoing(true)
        .setUsesChronometer(true)
        .setContentIntent(content)
        .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangUp))
        .build()

    /** Build the silent notification left when a call rang out without an answer. */
    fun buildMissed(config: CallScreenConfig): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            config.callId.hashCode(),
            context.intentLaunchWithNavigateTo(FrontendTarget.Default, config.serverId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, ensureStatusChannel())
            .also { handleSmallIcon(context, it, config.eventData) }
            .setContentTitle(context.getString(commonR.string.call_screen_missed))
            .setContentText(config.title ?: config.message)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
    }

    private fun baseBuilder(config: CallScreenConfig, channelId: String) =
        NotificationCompat.Builder(context, channelId).also {
            handleSmallIcon(context, it, config.eventData)
            it.setContentTitle(config.title ?: context.getString(commonR.string.call_screen_incoming))
            it.setContentText(config.message)
        }

    private fun plainAction(config: CallScreenConfig, role: CallActionRole, intent: PendingIntent) =
        NotificationCompat.Action.Builder(
            mdiIcon(role.defaultIcon, role.defaultIcon),
            config.buttons.first { it.role == role }.title,
            intent,
        ).build()

    private fun customAction(action: CallAction, intent: PendingIntent) = NotificationCompat.Action.Builder(
        mdiIcon(action.icon, action.role.defaultIcon),
        action.title,
        intent,
    )
        .setShowsUserInterface(false)
        .setAuthenticationRequired(action.authenticationRequired)
        .build()

    private fun mdiIcon(name: String, fallback: String): IconCompat? {
        val icon = Mdi.fromHaName(name) ?: Mdi.fromHaName(fallback) ?: return null
        val sizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            ACTION_ICON_SIZE_DP,
            context.resources.displayMetrics,
        ).toInt()
        return IconCompat.createWithBitmap(icon.toBitmap(sizePx, Color.WHITE))
    }

    private fun ringtoneUri() = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
        ?: Settings.System.DEFAULT_RINGTONE_URI
}
