package io.homeassistant.companion.android.frontend.navigation

import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.widgets.camera.CameraWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.entity.EntityWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.mediaplayer.MediaPlayerControlsWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.todo.TodoWidgetConfigureActivity

/**
 * Widget types that can be configured via the EntityAddTo flow.
 *
 * Each variant knows how to build the configuration [Intent] for its underlying widget activity,
 * so callers can launch the right configure screen.
 */
sealed interface WidgetType {

    /**
     * Builds the configuration [Intent] for this widget type, pre-filled with [entityId].
     */
    fun toConfigureIntent(context: Context, entityId: String): Intent

    data object Entity : WidgetType {
        override fun toConfigureIntent(context: Context, entityId: String): Intent =
            EntityWidgetConfigureActivity.newInstance(context, entityId)
    }

    data object MediaPlayer : WidgetType {
        override fun toConfigureIntent(context: Context, entityId: String): Intent =
            MediaPlayerControlsWidgetConfigureActivity.newInstance(context, entityId)
    }

    data object Camera : WidgetType {
        override fun toConfigureIntent(context: Context, entityId: String): Intent =
            CameraWidgetConfigureActivity.newInstance(context, entityId)
    }

    data object Todo : WidgetType {
        override fun toConfigureIntent(context: Context, entityId: String): Intent =
            TodoWidgetConfigureActivity.newInstance(context, entityId)
    }
}
