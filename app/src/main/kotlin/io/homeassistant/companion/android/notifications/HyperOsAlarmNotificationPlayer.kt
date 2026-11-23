package io.homeassistant.companion.android.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.notifications.NotificationData
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.util.sensitive
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val XIAOMI_MANUFACTURER = "Xiaomi"
private const val HYPER_OS_3_BUILD_PREFIX = "OS3."
private val ALARM_PLAYBACK_TIMEOUT = 30.seconds

/** Plays the alarm channel tone when HyperOS 3 silences notification sounds in non-normal ringer modes. */
class HyperOsAlarmNotificationPlayer @VisibleForTesting internal constructor(
    private val context: Context,
    manufacturer: String,
    buildVersion: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val isHyperOs3 = manufacturer.equals(XIAOMI_MANUFACTURER, ignoreCase = true) &&
        buildVersion.startsWith(HYPER_OS_3_BUILD_PREFIX)

    @Inject
    constructor(@ApplicationContext context: Context) : this(context, Build.MANUFACTURER, Build.VERSION.INCREMENTAL)

    /** Returns the configured tone if this notification needs direct alarm playback. */
    fun getSound(notification: Notification, tag: String?, id: Int): Uri? {
        if (!isHyperOs3 || !SdkVersion.isAtLeast(Build.VERSION_CODES.P)) return null

        val isAlarm = notification.channelId == NotificationData.ALARM_STREAM &&
            notification.category == Notification.CATEGORY_ALARM
        return if (isAlarm && isSilentOrVibrate()) {
            context.getSystemService<NotificationManager>()
                ?.takeIf { it.areNotificationsEnabled() }
                ?.takeUnless { it.isAlertOnceUpdate(notification, tag, id) }
                ?.getAudibleSound(notification.channelId)
        } else {
            null
        }
    }

    private fun isSilentOrVibrate(): Boolean {
        // HyperOS can report Vibrate or Silent for the same Silent tile, depending on DND.
        val ringerMode = context.getSystemService<AudioManager>()?.ringerMode
        return ringerMode == AudioManager.RINGER_MODE_SILENT || ringerMode == AudioManager.RINGER_MODE_VIBRATE
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun NotificationManager.getAudibleSound(channelId: String): Uri? = getNotificationChannel(channelId)
        ?.takeIf { it.importance >= NotificationManager.IMPORTANCE_DEFAULT }
        ?.takeUnless { it.group?.let { groupId -> getNotificationChannelGroup(groupId)?.isBlocked } == true }
        ?.sound

    private fun NotificationManager.isAlertOnceUpdate(notification: Notification, tag: String?, id: Int): Boolean {
        val onlyAlertOnce = notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
        return onlyAlertOnce && activeNotifications.any { it.tag == tag && it.id == id }
    }

    /** Plays one tone on the alarm stream and releases the player when playback ends or is cancelled. */
    suspend fun play(sound: Uri) {
        val player = MediaPlayer()
        try {
            withTimeoutOrNull(ALARM_PLAYBACK_TIMEOUT) {
                if (player.prepareAlarm(sound)) player.awaitPlayback()
            } ?: Timber.w("Critical notification alarm playback timed out")
        } finally {
            player.release()
        }
    }

    private suspend fun MediaPlayer.prepareAlarm(sound: Uri): Boolean = withContext(ioDispatcher) {
        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        listOf(sound, defaultSound).distinct().any { tryPrepare(it) }
    }

    private fun MediaPlayer.tryPrepare(sound: Uri): Boolean = try {
        reset()
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        setDataSource(context, sound)
        prepare()
        true
    } catch (e: IOException) {
        logPreparationFailure(e)
    } catch (e: SecurityException) {
        logPreparationFailure(e)
    } catch (e: IllegalArgumentException) {
        logPreparationFailure(e)
    } catch (e: IllegalStateException) {
        logPreparationFailure(e)
    }

    private fun logPreparationFailure(exception: Exception): Boolean {
        Timber.w("Could not prepare critical notification alarm playback (${sensitive { exception.toString() }})")
        return false
    }

    private suspend fun MediaPlayer.awaitPlayback(): Unit = suspendCancellableCoroutine { continuation ->
        fun finish() {
            if (continuation.isActive) continuation.resume(Unit)
        }

        setOnCompletionListener { finish() }
        setOnErrorListener { _, what, extra ->
            Timber.w("Critical notification alarm playback failed (what=$what, extra=$extra)")
            finish()
            true
        }
        try {
            start()
        } catch (e: IllegalStateException) {
            Timber.w(e, "Could not start critical notification alarm playback")
            finish()
        }
    }
}
