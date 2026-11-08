package io.homeassistant.companion.android.common.util

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TAG = "test-tag"
private const val GROUP = "group_test-group"
private const val CHANNEL_ID = "test-channel"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class NotificationManagerExtensionsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notificationManagerCompat = NotificationManagerCompat.from(context)

    @Test
    fun `Given active notification in a group when getting its group then return the group`() {
        postNotification(TAG, TAG.hashCode(), GROUP)

        assertEquals(GROUP, notificationManagerCompat.getActiveNotificationGroup(TAG, TAG.hashCode()))
    }

    @Test
    fun `Given active notification without a group when getting its group then return null`() {
        postNotification(TAG, TAG.hashCode(), group = null)

        assertNull(notificationManagerCompat.getActiveNotificationGroup(TAG, TAG.hashCode()))
    }

    @Test
    fun `Given no active notification with the tag when getting its group then return null`() {
        postNotification("other-tag", TAG.hashCode(), GROUP)

        assertNull(notificationManagerCompat.getActiveNotificationGroup(TAG, TAG.hashCode()))
    }

    private fun postNotification(tag: String, id: Int, group: String?) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setGroup(group)
            .build()
        notificationManager.notify(tag, id, notification)
    }
}
