package io.homeassistant.companion.android.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.notifications.NotificationData
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val TAG = "test_alarm"
private const val NOTIFICATION_ID = 1
private const val HYPER_OS_3_BUILD = "OS3.0.303.0.WOLMIXM"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [Build.VERSION_CODES.BAKLAVA])
class HyperOsAlarmNotificationPlayerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val channelSound = Uri.parse("content://test/sound")
    private val player = HyperOsAlarmNotificationPlayer(context, "Xiaomi", HYPER_OS_3_BUILD)

    @Test
    fun `Given HyperOS 3 in either non-normal ringer mode when posting an alarm then use the channel tone`() {
        createAlarmChannel()

        for (ringerMode in listOf(AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE)) {
            audioManager.ringerMode = ringerMode
            assertEquals(channelSound, player.getSound(alarm(), TAG, NOTIFICATION_ID))
        }
    }

    @Test
    fun `Given Normal ringer mode when posting an alarm then delegate playback to Android`() {
        createAlarmChannel()
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

        assertNull(player.getSound(alarm(), TAG, NOTIFICATION_ID))
    }

    @Test
    fun `Given another firmware or a non-alarm notification when posting then delegate playback to Android`() {
        createAlarmChannel()
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE

        val otherManufacturer = HyperOsAlarmNotificationPlayer(context, "Google", HYPER_OS_3_BUILD)
        val olderHyperOs = HyperOsAlarmNotificationPlayer(context, "Xiaomi", "OS2.0.7.0")
        assertNull(otherManufacturer.getSound(alarm(), TAG, NOTIFICATION_ID))
        assertNull(olderHyperOs.getSound(alarm(), TAG, NOTIFICATION_ID))
        assertNull(player.getSound(alarm(category = Notification.CATEGORY_MESSAGE), TAG, NOTIFICATION_ID))
    }

    @Test
    fun `Given disabled notifications when posting an alarm then do not play directly`() {
        createAlarmChannel()
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        shadowOf(notificationManager).setNotificationsEnabled(false)

        assertNull(player.getSound(alarm(), TAG, NOTIFICATION_ID))
    }

    @Test
    fun `Given an alarm channel with no sound when posting an alarm then do not play directly`() {
        createAlarmChannel(sound = null)
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE

        assertNull(player.getSound(alarm(), TAG, NOTIFICATION_ID))
    }

    @Test
    fun `Given an alarm with alert once when updating the same notification then do not replay the sound`() {
        createAlarmChannel()
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        val alarm = alarm(onlyAlertOnce = true)

        assertEquals(channelSound, player.getSound(alarm, TAG, NOTIFICATION_ID))
        notificationManager.notify(TAG, NOTIFICATION_ID, alarm)

        assertNull(player.getSound(alarm, TAG, NOTIFICATION_ID))
        assertEquals(channelSound, player.getSound(alarm(onlyAlertOnce = false), TAG, NOTIFICATION_ID))
    }

    private fun createAlarmChannel(sound: Uri? = channelSound) {
        val channel = NotificationChannel(NotificationData.ALARM_STREAM, "Alarm", NotificationManager.IMPORTANCE_DEFAULT)
        channel.setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        notificationManager.createNotificationChannel(channel)
    }

    private fun alarm(category: String = Notification.CATEGORY_ALARM, onlyAlertOnce: Boolean = false): Notification {
        return Notification.Builder(context, NotificationData.ALARM_STREAM)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setCategory(category)
            .setOnlyAlertOnce(onlyAlertOnce)
            .build()
    }
}
