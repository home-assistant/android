package io.homeassistant.companion.android.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltTestApplication
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.notifications.NotificationData
import io.homeassistant.companion.android.testing.unit.FakeClock
import io.homeassistant.companion.android.util.QuestUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [30])
class ConversationNotificationStyleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock().apply { currentInstant = Instant.parse("2026-10-30T12:00:00Z") }
    private val style = ConversationNotificationStyle(context, clock)
    private val published = mutableListOf<ShortcutInfoCompat>()
    private val data = mapOf(
        "notification_style" to "conversation",
        "tag" to "dishwasher",
        NotificationData.TITLE to "Dishwasher",
        NotificationData.MESSAGE to "<b>Finished</b>",
        NotificationData.NOTIFICATION_ICON to "mdi:dishwasher",
        NotificationData.COLOR to "#009688",
        MessagingManager.THIS_SERVER_ID to "1",
    )

    @Before
    fun setUp() {
        mockkStatic(ShortcutManagerCompat::class)
        every { ShortcutManagerCompat.getShortcuts(context, any()) } returns emptyList()
        every { ShortcutManagerCompat.removeDynamicShortcuts(context, any()) } returns Unit
        every { ShortcutManagerCompat.addDynamicShortcuts(context, any()) } answers {
            published.addAll(secondArg<List<ShortcutInfoCompat>>())
            true
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Given a conversation payload when applied then publish a topic shortcut and bot avatar`() = runTest {
        val builder = builder()
        style.apply(builder, data + (NotificationData.TITLE to "<b>Dishwasher</b>"))

        val notification = builder.build()
        val messaging = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)!!
        val message = messaging.messages.single()
        val shortcut = published.single()
        assertEquals("Finished", message.text.toString())
        assertEquals(clock.now().toEpochMilliseconds(), message.timestamp)
        assertEquals("Dishwasher", message.person?.name.toString())
        assertTrue(message.person!!.isBot)
        assertNotNull(message.person?.icon)
        assertFalse(messaging.isGroupConversation)
        assertEquals(shortcut.id, notification.shortcutId)
        assertEquals("Dishwasher", shortcut.shortLabel)
        assertEquals(Int.MAX_VALUE, shortcut.rank)
        assertEquals(context.packageName, shortcut.intent.`package`)
        assertEquals("1", shortcut.intent.data?.getQueryParameter("server_id"))
    }

    @Test
    fun `Given existing notification behavior when applying conversation then preserve intents actions and grouping`() = runTest {
        val intent = PendingIntent.getActivity(context, 0, Intent("test"), PendingIntent.FLAG_IMMUTABLE)
        val builder = builder().setContentIntent(intent).setDeleteIntent(intent)
            .addAction(0, "Open", intent).setGroup("appliances").setTimeoutAfter(60000)
            .setAutoCancel(true).setWhen(1234)
        style.apply(builder, data)

        val notification = builder.build()
        assertEquals(intent, notification.contentIntent)
        assertEquals(intent, notification.deleteIntent)
        assertEquals("Open", notification.actions.single().title)
        assertEquals("appliances", notification.group)
        assertEquals(60000, notification.timeoutAfter)
        assertEquals(1234, notification.`when`)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
    }

    @Test
    fun `Given repeated tags across servers when applied then shortcut identity is stable and server scoped`() = runTest {
        style.apply(builder(), data)
        style.apply(builder(), data + (NotificationData.TITLE to "Renamed"))
        style.apply(builder(), data + (MessagingManager.THIS_SERVER_ID to "2"))
        style.apply(builder(), data + ("tag" to "washing-machine"))

        assertEquals(published[0].id, published[1].id)
        assertNotEquals(published[0].id, published[2].id)
        assertNotEquals(published[0].id, published[3].id)
    }

    @Test
    fun `Given an existing cached conversation when applied then update without consuming another dynamic slot`() = runTest {
        style.apply(builder(), data)
        val existing = published.single()
        every { ShortcutManagerCompat.getShortcuts(context, any()) } returns listOf(existing)
        every { ShortcutManagerCompat.updateShortcuts(context, any()) } returns true

        style.apply(builder(), data + (NotificationData.TITLE to "Renamed"))

        verify(exactly = 1) { ShortcutManagerCompat.addDynamicShortcuts(context, any()) }
        verify { ShortcutManagerCompat.updateShortcuts(context, match { it.single().shortLabel == "Renamed" }) }
    }

    @Test
    fun `Given inactive cached and user shortcuts when applied then only prune inactive conversation dynamics`() = runTest {
        val inactive = shortcut("notification_conversation_old", cached = true)
        val pending = shortcut("notification_conversation_pending", cached = false)
        val user = shortcut("shortcut_1", cached = true)
        every { ShortcutManagerCompat.getShortcuts(context, any()) } returns listOf(inactive, pending, user)

        style.apply(builder(), data)

        verify { ShortcutManagerCompat.removeDynamicShortcuts(context, listOf("notification_conversation_old")) }
    }

    @Test
    fun `Given an active conversation when publishing another then retain its dynamic shortcut`() = runTest {
        val active = shortcut("notification_conversation_active", cached = true)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("test", "Test", NotificationManager.IMPORTANCE_DEFAULT))
        manager.notify(1, builder().setShortcutId(active.id).build())
        every { ShortcutManagerCompat.getShortcuts(context, any()) } returns listOf(active)

        style.apply(builder(), data)

        verify(exactly = 0) { ShortcutManagerCompat.removeDynamicShortcuts(any(), any()) }
    }

    @Test
    fun `Given rate limiting when applied then retain the ordinary notification`() = runTest {
        every { ShortcutManagerCompat.addDynamicShortcuts(context, any()) } returns false
        assertOrdinary(data)
    }

    @Test
    fun `Given a full shortcut quota when applied then retain the ordinary notification without evicting shortcuts`() = runTest {
        every { ShortcutManagerCompat.addDynamicShortcuts(context, any()) } throws IllegalArgumentException("Quota exceeded")
        assertOrdinary(data)
        verify(exactly = 0) { ShortcutManagerCompat.pushDynamicShortcut(any(), any()) }
    }

    @Test
    fun `Given a locked user when applied then retain the ordinary notification`() = runTest {
        every { ShortcutManagerCompat.getShortcuts(context, any()) } throws IllegalStateException("User locked")
        assertOrdinary(data)
    }

    @Test
    fun `Given a missing required field when applied then do not publish a shortcut`() = runTest {
        listOf("notification_style", "tag", NotificationData.TITLE, NotificationData.NOTIFICATION_ICON, MessagingManager.THIS_SERVER_ID).forEach {
            assertOrdinary(data - it)
        }
        assertTrue(published.isEmpty())
    }

    @Test
    fun `Given invalid conversation values when applied then do not publish a shortcut`() = runTest {
        listOf(
            "notification_style" to "unknown",
            "tag" to " ",
            NotificationData.TITLE to " ",
            NotificationData.NOTIFICATION_ICON to "mdi:nonexistent-icon",
            NotificationData.NOTIFICATION_ICON to "dishwasher",
            MessagingManager.THIS_SERVER_ID to "invalid",
        ).forEach { assertOrdinary(data + it) }
        assertTrue(published.isEmpty())
    }

    @Test
    fun `Given a title that renders blank when applied then retain the ordinary notification`() = runTest {
        listOf("<b></b>", "<b> </b>", "&#32;").forEach { title ->
            assertOrdinary(data + (NotificationData.TITLE to title))
        }
        assertTrue(published.isEmpty())
    }

    @Test
    fun `Given the active server sentinel when applied then do not publish an unstable conversation`() = runTest {
        assertOrdinary(data + (MessagingManager.THIS_SERVER_ID to ServerManager.SERVER_ID_ACTIVE.toString()))
        assertTrue(published.isEmpty())
    }

    @Test
    fun `Given another notification presentation when applied then leave that presentation unchanged`() = runTest {
        listOf(
            MessagingManager.IMAGE_URL to "/local/image.jpg",
            MessagingManager.VIDEO_URL to "/local/video.mp4",
            MessagingManager.ICON_URL to "/local/icon.png",
            MessagingManager.SUBJECT to "Summary",
            MessagingManager.PROGRESS to "50",
            MessagingManager.PROGRESS_INDETERMINATE to "true",
            MessagingManager.CHRONOMETER to "true",
            MessagingManager.LIVE_UPDATE to "true",
            MessagingManager.CAR_UI to "true",
        ).forEach { assertOrdinary(data + it) }
        assertTrue(published.isEmpty())
    }

    @Test
    @Config(sdk = [29])
    fun `Given Android before 11 when applied then retain the ordinary notification`() = runTest {
        assertOrdinary(data)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `Given Android Automotive when applied then retain the ordinary notification`() = runTest {
        shadowOf(context.packageManager).setSystemFeature("android.hardware.type.automotive", true)
        assertOrdinary(data)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `Given Meta Quest when applied then retain the ordinary notification`() = runTest {
        mockkObject(QuestUtil)
        every { QuestUtil.isQuest } returns true
        assertOrdinary(data)
        assertTrue(published.isEmpty())
    }

    private fun builder() = NotificationCompat.Builder(context, "test")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Original")
        .setStyle(NotificationCompat.BigTextStyle().bigText("Original body"))

    private suspend fun assertOrdinary(payload: Map<String, String>) {
        val builder = builder()
        style.apply(builder, payload)
        val notification = builder.build()
        assertNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification))
        assertNull(notification.shortcutId)
        assertEquals("Original body", notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
    }

    private fun shortcut(id: String, cached: Boolean): ShortcutInfoCompat = mockk {
        every { this@mockk.id } returns id
        every { isDynamic } returns true
        every { isCached } returns cached
    }
}
