package io.homeassistant.companion.android.common.util

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.server.ServerConnectionInfo
import io.homeassistant.companion.android.database.server.ServerSessionInfo
import io.homeassistant.companion.android.database.server.ServerUserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class LocalNetworkPermissionWarningTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Test
    fun `Given show called when single server then posts ongoing notification with content intent`() {
        LocalNetworkPermissionWarning.show(context, affectedServers = listOf(server(id = 1, name = "Home")))

        val posted = shadowOf(notificationManager).allNotifications
        assertEquals(1, posted.size)
        val notif = posted.first()
        assertTrue(
            "notification must be ongoing",
            notif.flags and Notification.FLAG_ONGOING_EVENT != 0,
        )
        assertNotNull("must have a content intent", notif.contentIntent)
    }

    @Test
    fun `Given show called twice when one is already visible then second call is a no-op`() {
        LocalNetworkPermissionWarning.show(context, affectedServers = listOf(server(id = 1, name = "Home")))
        LocalNetworkPermissionWarning.show(
            context,
            affectedServers = listOf(
                server(id = 1, name = "Home"),
                server(id = 2, name = "Cabin"),
            ),
        )

        // Re-posting an already-visible notification would trip Android's noisy-notifications
        // rate limiter, so the second show() must short-circuit. Exactly one notification stays.
        assertEquals(1, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun `Given show then cancel when called then notification is removed`() {
        LocalNetworkPermissionWarning.show(context, affectedServers = listOf(server(id = 1, name = "Home")))
        LocalNetworkPermissionWarning.cancel(context)

        assertEquals(0, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun `Given cancel when nothing posted then no error`() {
        LocalNetworkPermissionWarning.cancel(context)
        assertEquals(0, shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun `Given show when called then channel is registered`() {
        LocalNetworkPermissionWarning.show(context, affectedServers = listOf(server(id = 1, name = "Home")))

        val channel = notificationManager.getNotificationChannel(CHANNEL_LOCAL_NETWORK_PERMISSION)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    private fun server(id: Int, name: String): Server = Server(
        id = id,
        _name = name,
        connection = ServerConnectionInfo(
            externalUrl = "http://192.168.1.$id:8123",
        ),
        session = ServerSessionInfo(),
        user = ServerUserInfo(),
    )
}
