package io.homeassistant.companion.android.notifications.callscreen

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.notifications.MessagingManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import timber.log.Timber

private const val KEY_TAG = "tag"
private const val KEY_TITLE = "title"
private const val KEY_MESSAGE = "message"
private const val KEY_CHANNEL = "channel"
private const val KEY_IMAGE = "image"
private const val KEY_ACTION = "action"
private const val BACKGROUND_IMAGE = "image"
private const val BACKGROUND_ENTITY_IMAGE = "entity_image"
private const val BACKGROUND_STREAM = "stream"
private const val BEHAVIOR_ANSWER = "answer"
private const val BEHAVIOR_DECLINE = "decline"
private const val BEHAVIOR_MENU = "menu"
private const val BEHAVIOR_MENU_ITEM = "menu_item"
private const val CAMERA_PROXY_PATH = "/api/camera_proxy/"
private const val MAX_BUTTONS_BESIDES_ANSWER_DECLINE = 2
private const val DEFAULT_TAG_PREFIX = "call_screen_"

/** Action key used for the built-in answer button when the payload doesn't define one. */
internal const val ACTION_KEY_ANSWER = "ANSWER"

/** Action key used for the built-in decline button when the payload doesn't define one. */
internal const val ACTION_KEY_DECLINE = "DECLINE"

/** Action key used for the built-in menu button when the payload needs one but doesn't define it. */
internal const val ACTION_KEY_MENU = "MENU"

private const val ACTION_KEY_URI = MessagingManager.URI
private val DEFAULT_RING_TIMEOUT = 60.seconds

/**
 * Parsed call screen notification.
 *
 * @property callId database id of the notification, used to match intents to the active call
 * @property tag notification tag, also used to clear the call
 * @property poster server path or URL of the image shown before the stream starts
 * @property stream source of the go2rtc stream, `null` if the payload doesn't reference one
 * @property server optional go2rtc server forwarded as `&server=`
 * @property buttons ringing buttons in display order, at most 4
 * @property menuItems entries of the popup opened by the [CallActionRole.Menu] button
 * @property eventData payload sent with the `mobile_app_notification_action` event, without the `action` key
 */
internal data class CallScreenConfig(
    val callId: Long,
    val serverId: Int,
    val tag: String,
    val title: String?,
    val message: String?,
    val channelName: String?,
    val background: CallBackground,
    val poster: String?,
    val stream: Go2RtcSource?,
    val server: String?,
    val media: Set<CallMedia>,
    val ringTimeout: Duration,
    val buttons: List<CallAction>,
    val menuItems: List<CallAction>,
    val eventData: Map<String, String>,
)

/** What is drawn behind the call UI. */
internal sealed interface CallBackground {
    /** Plain neutral background. */
    data object None : CallBackground

    /** Static image at a server path or URL. */
    data class Image(val path: String) : CallBackground

    /** Live go2rtc video once available, the poster until then. */
    data object Stream : CallBackground
}

/** Where go2rtc gets its stream from. */
internal sealed interface Go2RtcSource {
    /** A go2rtc stream name or URL, sent as `url=`. */
    data class Url(val url: String) : Go2RtcSource

    /** A camera entity handled by the WebRTC integration, sent as `entity=`. */
    data class Entity(val entityId: String) : Go2RtcSource
}

/** Media directions requested from go2rtc. */
internal enum class CallMedia(val token: String) {
    VIDEO("video"),
    AUDIO("audio"),
    MICROPHONE("microphone"),
}

/** Position and behavior of an action in the call UI. */
internal sealed interface CallActionRole {
    /** Default icon, as a Home Assistant MDI name. */
    val defaultIcon: String

    data object Answer : CallActionRole {
        override val defaultIcon = "mdi:phone"
    }

    data object Decline : CallActionRole {
        override val defaultIcon = "mdi:phone-hangup"
    }

    data object Menu : CallActionRole {
        override val defaultIcon = "mdi:dots-horizontal"
    }

    data object Custom : CallActionRole {
        override val defaultIcon = "mdi:gesture-tap-button"
    }

    data object MenuItem : CallActionRole {
        override val defaultIcon = "mdi:gesture-tap-button"
    }
}

/**
 * One action of the call UI.
 *
 * @property index 1-based position in the notification payload, `0` for built-in actions
 * @property icon configured icon (MDI name), falls back to [CallActionRole.defaultIcon] when unresolvable
 */
internal data class CallAction(
    val index: Int,
    val key: String,
    val title: String,
    val icon: String,
    val role: CallActionRole,
    val uri: String?,
    val endCall: Boolean,
    val authenticationRequired: Boolean,
)

/** Localized titles for the built-in actions. */
internal data class CallScreenDefaults(val answerTitle: String, val declineTitle: String, val moreTitle: String)

@Serializable
private data class CallScreenPayload(val callScreen: CallScreenJson? = null)

@Serializable
private data class CallScreenJson(
    val entity: String? = null,
    val stream: String? = null,
    val server: String? = null,
    val background: String? = null,
    val media: String? = null,
    val ringTimeout: Int? = null,
    val actions: Map<String, CallActionJson> = emptyMap(),
)

@Serializable
private data class CallActionJson(val icon: String? = null, val endCall: Boolean = false)

/**
 * Parse [data] as a call screen notification.
 *
 * @return the config, or `null` if `intent_extras` is not a JSON object with a `call_screen` object, in
 * which case the notification is a regular one
 */
internal fun parseCallScreenConfig(
    data: Map<String, String>,
    callId: Long,
    defaults: CallScreenDefaults,
): CallScreenConfig? {
    val json = parsePayload(data[MessagingManager.INTENT_EXTRAS]) ?: return null
    val actions = buildActions(data, json.actions, defaults)
    val layout = layoutActions(actions, defaults)

    val entity = json.entity?.takeIf { it.isNotBlank() }
    val streamName = json.stream?.takeIf { it.isNotBlank() }
    val image = data[KEY_IMAGE]?.takeIf { it.isNotBlank() }
    val entityImage = entity?.let { CAMERA_PROXY_PATH + it }
    val stream = streamName?.let { Go2RtcSource.Url(it) } ?: entity?.let { Go2RtcSource.Entity(it) }

    return CallScreenConfig(
        callId = callId,
        serverId = data.getValue(MessagingManager.THIS_SERVER_ID).toInt(),
        tag = data[KEY_TAG]?.takeIf { it.isNotBlank() } ?: "$DEFAULT_TAG_PREFIX$callId",
        title = data[KEY_TITLE],
        message = data[KEY_MESSAGE],
        channelName = data[KEY_CHANNEL]?.takeIf { it.isNotBlank() },
        background = resolveBackground(json.background, image, entityImage, stream != null),
        poster = image ?: entityImage,
        stream = stream,
        server = json.server?.takeIf { it.isNotBlank() },
        media = parseMedia(json.media),
        ringTimeout = json.ringTimeout?.takeIf { it > 0 }?.seconds ?: DEFAULT_RING_TIMEOUT,
        buttons = layout.buttons,
        menuItems = layout.menuItems,
        eventData = data.filterKeys { !it.startsWith(MessagingManager.SOURCE_REPLY_HISTORY) },
    )
}

private fun parsePayload(intentExtras: String?): CallScreenJson? {
    if (intentExtras.isNullOrBlank()) return null
    return try {
        kotlinJsonMapper.decodeFromString<CallScreenPayload>(intentExtras).callScreen
    } catch (e: SerializationException) {
        Timber.w(e, "intent_extras is not a valid call screen payload")
        null
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "intent_extras is not a valid call screen payload")
        null
    }
}

private fun resolveBackground(
    explicit: String?,
    image: String?,
    entityImage: String?,
    hasStream: Boolean,
): CallBackground {
    val fromExplicit = when (explicit?.trim()?.lowercase()) {
        BACKGROUND_IMAGE -> image?.let { CallBackground.Image(it) }
        BACKGROUND_ENTITY_IMAGE -> entityImage?.let { CallBackground.Image(it) }
        BACKGROUND_STREAM -> CallBackground.Stream.takeIf { hasStream }
        else -> null
    }
    return fromExplicit ?: when {
        hasStream -> CallBackground.Stream
        image != null -> CallBackground.Image(image)
        else -> CallBackground.None
    }
}

private fun parseMedia(media: String?): Set<CallMedia> {
    val parsed = media.orEmpty()
        .split(',')
        .map { it.trim().lowercase() }
        .mapNotNull { token -> CallMedia.entries.firstOrNull { it.token == token } }
        .toSet()
    return parsed.ifEmpty { CallMedia.entries.toSet() }
}

private fun roleOf(behavior: String?): CallActionRole? = when (behavior?.lowercase()) {
    BEHAVIOR_ANSWER -> CallActionRole.Answer
    BEHAVIOR_DECLINE -> CallActionRole.Decline
    BEHAVIOR_MENU -> CallActionRole.Menu
    BEHAVIOR_MENU_ITEM -> CallActionRole.MenuItem
    else -> null
}

/** Read the payload actions, keep only the first answer/decline/menu and add the missing built-ins. */
private fun buildActions(
    data: Map<String, String>,
    configured: Map<String, CallActionJson>,
    defaults: CallScreenDefaults,
): List<CallAction> {
    val seenSpecial = mutableSetOf<CallActionRole>()
    val actions = generateSequence(1) { it + 1 }
        .takeWhile { data.containsKey("${KEY_ACTION}_${it}_key") }
        .map { i ->
            val key = data.getValue("${KEY_ACTION}_${i}_key")
            val requested = roleOf(data["${KEY_ACTION}_${i}_behavior"])
            val unique = requested == CallActionRole.MenuItem || requested == null || seenSpecial.add(requested)
            val role = if (unique) requested ?: CallActionRole.Custom else CallActionRole.Custom
            CallAction(
                index = i,
                key = key,
                title = data["${KEY_ACTION}_${i}_title"] ?: key,
                icon = configured[key]?.icon?.takeIf { it.isNotBlank() } ?: role.defaultIcon,
                role = role,
                uri = data["${KEY_ACTION}_${i}_uri"].takeIf { key == ACTION_KEY_URI },
                endCall = configured[key]?.endCall == true,
                authenticationRequired = data["${KEY_ACTION}_${i}_authenticationRequired"]?.toBoolean() == true,
            )
        }
        .toList()

    val decline = if (actions.none { it.role == CallActionRole.Decline }) {
        listOf(builtIn(ACTION_KEY_DECLINE, defaults.declineTitle, CallActionRole.Decline))
    } else {
        emptyList()
    }
    val answer = if (actions.none { it.role == CallActionRole.Answer }) {
        listOf(builtIn(ACTION_KEY_ANSWER, defaults.answerTitle, CallActionRole.Answer))
    } else {
        emptyList()
    }
    return decline + actions + answer
}

private fun builtIn(key: String, title: String, role: CallActionRole) = CallAction(
    index = 0,
    key = key,
    title = title,
    icon = role.defaultIcon,
    role = role,
    uri = null,
    endCall = false,
    authenticationRequired = false,
)

private data class ActionLayout(val buttons: List<CallAction>, val menuItems: List<CallAction>)

/** Fit [actions] in the button cap, moving what doesn't fit into the menu. */
private fun layoutActions(actions: List<CallAction>, defaults: CallScreenDefaults): ActionLayout {
    val customs = actions.filter { it.role == CallActionRole.Custom }
    val menuItems = actions.filter { it.role == CallActionRole.MenuItem }
    val menuNeeded = menuItems.isNotEmpty() || customs.size > MAX_BUTTONS_BESIDES_ANSWER_DECLINE
    val slots = MAX_BUTTONS_BESIDES_ANSWER_DECLINE - if (menuNeeded) 1 else 0
    val keptCustoms = customs.take(slots).toSet()
    val overflow = customs.drop(slots).map { it.copy(role = CallActionRole.MenuItem) }

    val buttons = actions.filter {
        when (it.role) {
            CallActionRole.Answer, CallActionRole.Decline -> true
            CallActionRole.Custom -> it in keptCustoms
            CallActionRole.Menu -> menuNeeded
            CallActionRole.MenuItem -> false
        }
    }.toMutableList()

    if (menuNeeded && buttons.none { it.role == CallActionRole.Menu }) {
        val menu = builtIn(ACTION_KEY_MENU, defaults.moreTitle, CallActionRole.Menu)
        buttons.add(buttons.indexOfFirst { it.role == CallActionRole.Answer }, menu)
    }
    return ActionLayout(buttons, overflow + menuItems)
}
