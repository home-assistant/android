package io.homeassistant.companion.android.webview.addto

import android.content.Context
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.widgets.camera.CameraWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.entity.EntityWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.mediaplayer.MediaPlayerControlsWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.todo.TodoWidgetConfigureActivity
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Represent an action that is going to be sent and received by the frontend to trigger
 * an action for a given entity on the application.
 */
@Serializable
sealed interface EntityAddToAction {

    /**
     * We don't need to send this value since we use a default value for each action.
     * If we were to use a dynamic icon we might need to serialize it too
     */
    @Transient
    val mdiIcon: String

    val enabled: Boolean
        get() = true

    fun text(context: Context): String

    fun details(context: Context): String? = null

    fun action(context: Context, viewModel: EntityAddToViewModel, entityId: String)

    @Serializable
    data object AndroidAutoFavorite : EntityAddToAction {
        override val mdiIcon: String = "mdi:car"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_android_auto_favorite)
        }

        override fun action(context: Context, viewModel: EntityAddToViewModel, entityId: String) {
            viewModel.addToAndroidAutoFavorite(entityId)
        }
    }

    @Serializable
    data class Shortcut(override val enabled: Boolean) : EntityAddToAction {
        override val mdiIcon: String = "mdi:open-in-new"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_shortcut)
        }

        override fun details(context: Context): String? {
            return if (enabled) null else context.getString(commonR.string.add_to_shortcut_limit)
        }

        override fun action(context: Context, viewModel: EntityAddToViewModel, entityId: String) {
            TODO("Not yet implemented")
        }
    }

    @Serializable
    data object Tile : EntityAddToAction {
        override val mdiIcon: String = "mdi:cog"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_tile)
        }

        override fun action(context: Context, viewModel: EntityAddToViewModel, entityId: String) {
            // TODO go to a new tile
//                                    startActivity(SettingsActivity.newInstance(requireContext(),
//                                        SettingsActivity.Deeplink.QSTile()
//                                    ))
        }
    }

    @Serializable
    data object EntityWidget : EntityAddToAction {
        override val mdiIcon: String = "mdi:widgets"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_entity_widget)
        }

        override fun action(context: Context, viewModel: EntityAddToViewModel, entityId: String) {
            context.startActivity(
                EntityWidgetConfigureActivity.newInstance(
                    context,
                    entityId,
                ),
            )
        }
    }

    @Serializable
    data object MediaPlayerWidget : EntityAddToAction {
        override val mdiIcon: String = "mdi:play-box-multiple"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_media_player_widget)
        }

        override fun action(context: Context, viewModel: EntityAddToViewModel, entityId: String) {
            context.startActivity(
                MediaPlayerControlsWidgetConfigureActivity.newInstance(
                    context,
                    entityId,
                ),
            )
        }
    }

    @Serializable
    data object CameraWidget : EntityAddToAction {
        override val mdiIcon: String = "mdi:camera"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_camera_widget)
        }

        override fun action(context: Context, viewModel: EntityAddToViewModel, entityId: String) {
            context.startActivity(
                CameraWidgetConfigureActivity.newInstance(
                    context,
                    entityId,
                ),
            )
        }
    }

    @Serializable
    data object TodoWidget : EntityAddToAction {
        override val mdiIcon: String = "mdi:checkbox-marked-circle-plus-outline"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_todo_widget)
        }

        override fun action(context: Context, viewModel: EntityAddToViewModel, entityId: String) {
            context.startActivity(
                TodoWidgetConfigureActivity.newInstance(
                    context,
                    entityId,
                ),
            )
        }
    }

    @Serializable
    data class Watch(val name: String, override val enabled: Boolean) : EntityAddToAction {
        override val mdiIcon: String = "mdi:watch-import"

        override fun text(context: Context): String {
            return context.getString(commonR.string.add_to_watch_favorite, name)
        }

        override fun details(context: Context): String? {
            return if (enabled) null else context.getString(commonR.string.add_to_watch_favorite_disconnected)
        }

        override fun action(context: Context, viewModel: EntityAddToViewModel, entityId: String) {
            TODO("Not yet implemented")
        }
    }
}
