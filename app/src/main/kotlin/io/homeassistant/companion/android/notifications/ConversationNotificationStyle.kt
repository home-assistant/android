package io.homeassistant.companion.android.notifications

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.toBitmap
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.notifications.NotificationData
import io.homeassistant.companion.android.common.notifications.parseColor
import io.homeassistant.companion.android.common.notifications.prepareText
import io.homeassistant.companion.android.common.util.MDI_PREFIX
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.common.util.fromHaName
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.frontend.navigation.FrontendTarget
import io.homeassistant.companion.android.launch.link.navigateDeepLinkUri
import io.homeassistant.companion.android.util.QuestUtil
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Clock
import timber.log.Timber

private const val NOTIFICATION_STYLE = "notification_style"
private const val CONVERSATION_STYLE = "conversation"
private const val NOTIFICATION_TAG = "tag"
private const val SHORTCUT_PREFIX = "notification_conversation_"
private const val AVATAR_SIZE = 192
private const val GLYPH_SIZE = 112

private data class NotificationConversation(val tag: String, val title: String, val serverId: Int, val icon: MdiIcon)

/** Opt-in conversation presentation for notifications with a stable tag and an MDI sender icon. */
class ConversationNotificationStyle @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    /** Leaves the existing style intact when the payload or the device cannot support a conversation. */
    internal suspend fun apply(builder: NotificationCompat.Builder, data: Map<String, String>) {
        if (!supportsConversations() ||
            data[NOTIFICATION_STYLE] != CONVERSATION_STYLE ||
            hasIncompatibleStyle(data)
        ) {
            return
        }

        val conversation = parseConversation(data) ?: return
        val avatar = createAvatar(conversation.icon, data)
        val topicId = UUID.nameUUIDFromBytes(conversation.tag.toByteArray(Charsets.UTF_8))
        val shortcutId = "$SHORTCUT_PREFIX${conversation.serverId}_$topicId"
        val title = prepareText(conversation.title)
        val message = prepareText(data[NotificationData.MESSAGE].orEmpty())
        if (publishShortcut(shortcutId, title.toString(), avatar, conversation.serverId)) {
            val sender = Person.Builder().setName(title).setKey(shortcutId).setIcon(avatar).setBot(true).build()
            builder.setShortcutId(shortcutId)
                .setStyle(
                    NotificationCompat.MessagingStyle(
                        Person.Builder().setName(context.getString(commonR.string.app_name)).build(),
                    )
                        .setGroupConversation(false)
                        .addMessage(message, clock.now().toEpochMilliseconds(), sender),
                )
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    private fun supportsConversations() =
        SdkVersion.isAtLeast(Build.VERSION_CODES.R) && !context.isAutomotive() && !QuestUtil.isQuest

    private fun parseConversation(data: Map<String, String>): NotificationConversation? {
        val tag = data[NOTIFICATION_TAG]
        val title = data[NotificationData.TITLE]
        if (tag.isNullOrBlank() || title.isNullOrBlank()) return null
        val serverId = data[MessagingManager.THIS_SERVER_ID]?.toIntOrNull()
        val icon = data[NotificationData.NOTIFICATION_ICON]
            ?.takeIf { it.startsWith(MDI_PREFIX) }
            ?.let { Mdi.fromHaName(it) }
        return if (serverId != null && icon != null) NotificationConversation(tag, title, serverId, icon) else null
    }

    private fun hasIncompatibleStyle(data: Map<String, String>): Boolean {
        val attachments =
            listOf(
                MessagingManager.IMAGE_URL,
                MessagingManager.VIDEO_URL,
                MessagingManager.ICON_URL,
                MessagingManager.SUBJECT,
            )
        val flags = listOf(MessagingManager.CHRONOMETER, MessagingManager.LIVE_UPDATE, MessagingManager.CAR_UI)
        return attachments.any { !data[it].isNullOrBlank() } ||
            flags.any { data[it].toBoolean() } ||
            data.containsKey(MessagingManager.PROGRESS) ||
            data[MessagingManager.PROGRESS_INDETERMINATE].toBoolean()
    }

    private fun createAvatar(icon: MdiIcon, data: Map<String, String>): IconCompat {
        val background = parseColor(context, data[NotificationData.COLOR], commonR.color.colorPrimary)
        val foreground = parseColor(context, data[NotificationData.NOTIFICATION_ICON_COLOR], android.R.color.white)
        val bitmap = createBitmap(AVATAR_SIZE, AVATAR_SIZE)
        val canvas = Canvas(bitmap)
        val radius = AVATAR_SIZE / 2f
        canvas.drawCircle(radius, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
        val inset = (AVATAR_SIZE - GLYPH_SIZE) / 2f
        canvas.drawBitmap(icon.toBitmap(GLYPH_SIZE, foreground), inset, inset, null)
        return IconCompat.createWithBitmap(bitmap)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun publishShortcut(id: String, title: String, icon: IconCompat, serverId: Int): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, navigateDeepLinkUri(FrontendTarget.Default, serverId))
            .setPackage(context.packageName)
        val shortcut = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(title)
            .setIcon(icon)
            .setIntent(intent)
            .setLongLived(true)
            // The notification identifies the sender as a bot. A bot-only shortcut participant list
            // makes Android reject the conversation, so this topic shortcut has no participants.
            .build()
        return try {
            val shortcuts = ShortcutManagerCompat.getShortcuts(
                context,
                ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED or
                    ShortcutManagerCompat.FLAG_MATCH_PINNED,
            )
            removeInactiveDynamicShortcuts(shortcuts, id)
            if (shortcuts.any { it.id == id }) {
                ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
            } else {
                // Unlike pushDynamicShortcut, this cannot evict the user's launcher shortcuts.
                ShortcutManagerCompat.addDynamicShortcuts(context, listOf(shortcut))
            }
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Unable to publish notification conversation shortcut")
            false
        } catch (e: IllegalStateException) {
            Timber.w(e, "Notification conversation shortcuts are unavailable")
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun removeInactiveDynamicShortcuts(shortcuts: List<ShortcutInfoCompat>, currentId: String) {
        val activeIds = NotificationManagerCompat.from(context).activeNotifications.map {
            it.notification.shortcutId
        }.toSet()
        val inactive = shortcuts.filter {
            // Only prune shortcuts already cached by Android, never one awaiting its first notification.
            it.id.startsWith(SHORTCUT_PREFIX) &&
                it.id != currentId &&
                it.isDynamic &&
                it.isCached &&
                it.id !in activeIds
        }
        if (inactive.isNotEmpty()) {
            ShortcutManagerCompat.removeDynamicShortcuts(context, inactive.map { it.id })
        }
    }
}
