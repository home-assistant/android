package io.homeassistant.companion.android.webview.addto

import android.content.Context
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.isAutomotive
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Represents an action that allows users to add a Home Assistant entity to various Android
 * platform features (widgets, shortcuts, tiles, etc.) or connected devices (watches, automotive).
 *
 * This sealed interface is used for bidirectional communication between the Android app and the
 * Home Assistant frontend. The frontend can query available actions, and the user can select
 * which action to perform for a specific entity.
 */
@Serializable
sealed interface EntityAddToAction {

    /**
     * The Material Design Icon identifier for this action, used for visual representation in the UI.
     *
     * The format must be `mdi:NAME_OF_ASSET`, for example `mdi:car`.
     *
     * This value is not serialized because each action type has a fixed icon. The @Transient
     * annotation prevents it from being included in JSON serialization to reduce payload size.
     */
    @Transient
    val mdiIcon: String

    /**
     * Indicates whether this action is currently available for use. The frontend is going to display
     * this action but it won't be usable.
     *
     * Some actions may be disabled based on system limitations (for example, the maximum number of
     * shortcuts has been reached) or device state (for example, a watch is disconnected).
     *
     * @return true if the action can be performed, false otherwise
     */
    val enabled: Boolean
        get() = true

    /**
     * Returns the localized display text for this action.
     *
     * This text is shown to the user in the action selection UI and should clearly describe
     * what the action will do (for example, "Add to Android Auto favorite" or "Add to home screen").
     *
     * @param context Android context used for accessing localized string resources
     * @return The localized action text to display to the user
     */
    fun text(context: Context): String

    /**
     * Returns optional additional details or status information about this action.
     *
     * This can be used to provide context about why an action might be disabled or other
     * relevant information (for example, "Maximum shortcuts reached" or "Watch disconnected").
     *
     * @param context Android context used for accessing localized string resources
     * @return Additional details text, or null if no additional information is needed
     */
    fun details(context: Context): String? = null

    /**
     * Action to add an entity to Android Auto or Android Automotive favorites.
     *
     * The display text varies depending on whether the app is running on an automotive head unit
     * or on a phone.
     */
    @Serializable
    data object AndroidAutoFavorite : EntityAddToAction {
        override val mdiIcon: String = "mdi:car"

        override fun text(context: Context): String {
            return if (context.isAutomotive()) {
                context.getString(commonR.string.add_to_android_auto_driving_favorite)
            } else {
                context.getString(commonR.string.add_to_android_auto_favorite)
            }
        }
    }

    /**
     * Action to add a pinned shortcut to the entity to the device home screen.
     *
     * Android devices have a system-imposed limit on the number of pinned shortcuts that can be created,
     * so this action may be disabled if that limit has been reached.
     *
     * @property enabled Indicates whether a new shortcut can be created. False if the system
     *                   limit for pinned shortcuts has been reached.
     */
    @Serializable
    data class Shortcut(override val enabled: Boolean) : EntityAddToAction {
        override val mdiIcon: String = "mdi:open-in-new"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_shortcut)
        }

        override fun details(context: Context): String? {
            return if (enabled) null else context.getString(commonR.string.add_to_shortcut_limit)
        }
    }

    /**
     * Action to add an entity to Quick Settings as a tile.
     */
    @Serializable
    data object Tile : EntityAddToAction {
        override val mdiIcon: String = "mdi:tune"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_tile)
        }
    }

    /**
     * Action to add an entity state widget to the home screen.
     */
    @Serializable
    data object EntityWidget : EntityAddToAction {
        override val mdiIcon: String = "mdi:shape"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_entity_widget)
        }
    }

    /**
     * Action to add a media player widget to the home screen.
     */
    @Serializable
    data object MediaPlayerWidget : EntityAddToAction {
        override val mdiIcon: String = "mdi:play-box-multiple"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_media_player_widget)
        }
    }

    /**
     * Action to add a camera widget to the home screen.
     */
    @Serializable
    data object CameraWidget : EntityAddToAction {
        override val mdiIcon: String = "mdi:camera-image"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_camera_widget)
        }
    }

    /**
     * Action to add a to-do list widget to the home screen.
     */
    @Serializable
    data object TodoWidget : EntityAddToAction {
        override val mdiIcon: String = "mdi:clipboard-list"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_todo_widget)
        }
    }

    /**
     * Action to add an entity to favorites on a connected Wear OS watch.
     *
     * This action may be disabled if the watch is currently disconnected from the phone.
     *
     * @property name The display name of the connected watch (for example, "Pixel Watch" or "Galaxy Watch")
     * @property enabled Indicates whether the watch is currently connected and can receive favorites.
     *                   False if the watch is disconnected or unavailable.
     */
    @Serializable
    data class Watch(val name: String, override val enabled: Boolean) : EntityAddToAction {
        override val mdiIcon: String = "mdi:watch-import"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_watch_favorite, name)
        }

        override fun details(context: Context): String? {
            return if (enabled) null else context.getString(commonR.string.add_to_watch_favorite_disconnected)
        }
    }
}
