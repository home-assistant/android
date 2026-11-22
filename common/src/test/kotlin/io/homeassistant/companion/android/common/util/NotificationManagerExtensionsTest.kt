package io.homeassistant.companion.android.common.util

import android.app.Notification.FLAG_GROUP_SUMMARY
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.notifications.FLAG_AUTOGROUP_SUMMARY
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TAG = "test-tag"
private const val OTHER_TAG = "other-tag"
private const val GROUP = "group_test-group"

/** Group Android uses for notifications it bundles automatically. */
private const val SYSTEM_GROUP = "ranker_group"
private const val CHANNEL_ID = "test-channel"

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class NotificationManagerExtensionsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notificationManagerCompat = NotificationManagerCompat.from(context)
    private val cancel: (String, Int) -> Unit = mockk(relaxed = true)

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
        postNotification(OTHER_TAG, TAG.hashCode(), GROUP)

        assertNull(notificationManagerCompat.getActiveNotificationGroup(TAG, TAG.hashCode()))
    }

    @Test
    fun `Given last notification in an app group when canceling group if needed then cancel the group summary`() {
        postNotification(TAG, TAG.hashCode(), GROUP)
        postGroupSummary(GROUP)

        assertTrue(cancelGroupIfNeeded(TAG, TAG.hashCode()))
        verify(exactly = 1) { cancel(GROUP, GROUP.hashCode()) }
    }

    @Test
    fun `Given other notifications remain in an app group when canceling group if needed then cancel nothing`() {
        postNotification(TAG, TAG.hashCode(), GROUP)
        postNotification(OTHER_TAG, OTHER_TAG.hashCode(), GROUP)
        postGroupSummary(GROUP)

        assertFalse(cancelGroupIfNeeded(TAG, TAG.hashCode()))
        verify(exactly = 0) { cancel(any(), any()) }
    }

    @Test
    fun `Given app notification without group notification when canceling group if needed then cancel nothing`() {
        postNotification(TAG, TAG.hashCode(), GROUP)
        // Group is not created

        assertFalse(cancelGroupIfNeeded(TAG, TAG.hashCode()))
        verify(exactly = 0) { cancel(any(), any()) }
    }

    @Test
    fun `Given last notification in a system generated group when canceling group if needed then cancel nothing`() {
        postNotification(TAG, TAG.hashCode(), SYSTEM_GROUP)
        postGroupSummary(SYSTEM_GROUP, systemGenerated = true)

        assertFalse(cancelGroupIfNeeded(TAG, TAG.hashCode()))
        verify(exactly = 0) { cancel(any(), any()) }
    }

    @Test
    fun `Given notification without a group when canceling group if needed then cancel nothing`() {
        postNotification(TAG, TAG.hashCode(), group = null)

        assertFalse(cancelGroupIfNeeded(TAG, TAG.hashCode()))
        verify(exactly = 0) { cancel(any(), any()) }
    }

    @Test
    fun `Given no active notification with the tag when canceling group if needed then cancel nothing`() {
        postNotification(OTHER_TAG, OTHER_TAG.hashCode(), GROUP)
        postGroupSummary(GROUP)

        assertFalse(cancelGroupIfNeeded(TAG, TAG.hashCode()))
        verify(exactly = 0) { cancel(any(), any()) }
    }

    private fun postNotification(tag: String, id: Int, group: String?) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setGroup(group)
            .build()
        notificationManager.notify(tag, id, notification)
    }

    private fun postGroupSummary(group: String, systemGenerated: Boolean = false) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setGroup(group)
            .setGroupSummary(true)
            .build()
        if (systemGenerated) {
            notification.flags = notification.flags or FLAG_GROUP_SUMMARY or FLAG_AUTOGROUP_SUMMARY
        }
        notificationManager.notify(group, group.hashCode(), notification)
    }

    private fun cancelGroupIfNeeded(tag: String, id: Int): Boolean = cancelNotificationGroupIfNeeded(notificationManager, tag, id, cancel)
}
