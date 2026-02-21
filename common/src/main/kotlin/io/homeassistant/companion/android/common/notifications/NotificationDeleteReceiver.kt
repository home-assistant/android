package io.homeassistant.companion.android.common.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.cancelGroupIfNeeded
import io.homeassistant.companion.android.database.notification.NotificationDao
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class NotificationDeleteReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_DATA = "EXTRA_DATA"
        const val EXTRA_NOTIFICATION_GROUP = "EXTRA_NOTIFICATION_GROUP"
        const val EXTRA_NOTIFICATION_GROUP_ID = "EXTRA_NOTIFICATION_GROUP_ID"
        const val EXTRA_NOTIFICATION_DB = "EXTRA_NOTIFICATION_DB"
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var notificationDao: NotificationDao

    @Suppress("UNCHECKED_CAST")
    override fun onReceive(context: Context, intent: Intent) {
        val hashData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_DATA, HashMap::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_DATA)
        } as HashMap<String, *>
        val group = intent.getStringExtra(EXTRA_NOTIFICATION_GROUP)
        val groupId = intent.getIntExtra(EXTRA_NOTIFICATION_GROUP_ID, -1)

        val notificationManagerCompat = NotificationManagerCompat.from(context)

        // Cancel any left empty group of the notification, if needed
        // This maybe the case if timeoutAfter has deleted the notification
        // Then only the empty group is left and needs to be cancelled
        notificationManagerCompat.cancelGroupIfNeeded(group, groupId)

        ioScope.launch {
            try {
                val databaseId = intent.getLongExtra(EXTRA_NOTIFICATION_DB, 0)
                val serverId = notificationDao.get(databaseId.toInt())?.serverId ?: ServerManager.SERVER_ID_ACTIVE

                serverManager.integrationRepository(serverId).fireEvent("mobile_app_notification_cleared", hashData)
                Timber.d("Notification cleared event successful!")
            } catch (e: Exception) {
                Timber.e(e, "Issue sending event to Home Assistant")
            }
        }
    }
}
