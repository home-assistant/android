package io.homeassistant.companion.android.settings.notification

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.runtime.toMutableStateList
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
@RequiresApi(Build.VERSION_CODES.O)
class NotificationViewModel @Inject constructor(application: Application) : AndroidViewModel(application) {

    val app = application

    private var notificationManager = application.applicationContext.getSystemService<NotificationManager>()!!

    var channelList = notificationManager.notificationChannels.sortedBy { it.name.toString() }.toMutableStateList()
        private set

    fun deleteChannel(channelId: String) {
        notificationManager.deleteNotificationChannel(channelId)
    }

    fun editChannelDetails(channelId: String) {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, app.applicationContext.packageName)
        intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.applicationContext.startActivity(intent)
    }

    fun updateChannelList() {
        channelList.clear()
        channelList.addAll(notificationManager.notificationChannels.sortedBy { it.name.toString() })
    }

    fun createChannel(channel: NotificationChannel) {
        notificationManager.createNotificationChannel(channel)
    }
}
