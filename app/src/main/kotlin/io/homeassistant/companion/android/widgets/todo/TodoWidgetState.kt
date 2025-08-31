package io.homeassistant.companion.android.widgets.todo

import android.os.Build
import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import androidx.glance.material.ColorProviders
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse.TodoItem.Companion.COMPLETED_STATUS
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTheme
import io.homeassistant.companion.android.util.compose.glanceHaLightColors
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class TodoItemState(val uid: String?, val name: String, val done: Boolean) : Parcelable {
    companion object {
        fun from(todoItem: TodoWidgetEntity.TodoItem): TodoItemState {
            return TodoItemState(
                uid = todoItem.uid,
                name = todoItem.summary ?: "",
                done = todoItem.status == COMPLETED_STATUS,
            )
        }
    }
}

internal sealed interface TodoState {
    val backgroundType: WidgetBackgroundType
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WidgetBackgroundType.DYNAMICCOLOR
        } else {
            WidgetBackgroundType.DAYNIGHT
        }
    val textColor: String?
        get() = null

    companion object {
        @Composable
        fun TodoState.getColors(): ColorProviders {
            return when (backgroundType) {
                WidgetBackgroundType.DYNAMICCOLOR -> GlanceTheme.colors
                WidgetBackgroundType.DAYNIGHT -> HomeAssistantGlanceTheme.colors
                WidgetBackgroundType.TRANSPARENT -> ColorProviders(
                    glanceHaLightColors
                        .copy(
                            background = Color.Transparent,
                            onSurface = Color(
                                textColor?.toColorInt() ?: glanceHaLightColors.onSurface.toArgb(),
                            ),
                        ),
                )
            }
        }
    }
}

internal object LoadingTodoState : TodoState
internal object EmptyTodoState : TodoState

internal data class TodoStateWithData(
    override val backgroundType: WidgetBackgroundType,
    override val textColor: String?,
    val serverId: Int,
    val listEntityId: String,
    val listName: String?,
    val todoItems: List<TodoItemState>,
    val outOfSync: Boolean,
    val showComplete: Boolean,
) : TodoState {

    fun hasDisplayableItems(): Boolean {
        return if (showComplete) {
            todoItems.isNotEmpty()
        } else {
            todoItems.any { !it.done }
        }
    }

    companion object {
        /**
         * Create a complete [TodoStateWithData] from the DB and from the server. Set the flag [outOfSync] to false, since the data
         * includes an updated state from the server.
         */
        fun from(
            todoEntity: TodoWidgetEntity,
            entity: Entity,
            todos: List<TodoWidgetEntity.TodoItem>,
        ): TodoStateWithData {
            return TodoStateWithData(
                backgroundType = todoEntity.backgroundType,
                textColor = todoEntity.textColor,
                serverId = todoEntity.serverId,
                listEntityId = entity.entityId,
                listName = entity.friendlyName,
                todoItems = todos.map(TodoItemState::from),
                outOfSync = false,
                showComplete = todoEntity.showCompleted,
            )
        }

        /**
         * Create a [TodoStateWithData] with data only from the DB. Set the flag [outOfSync] to true, since the data
         * doesn't have an updated state from the server.
         */
        fun from(todoEntity: TodoWidgetEntity): TodoStateWithData {
            return TodoStateWithData(
                backgroundType = todoEntity.backgroundType,
                textColor = todoEntity.textColor,
                serverId = todoEntity.serverId,
                listEntityId = todoEntity.entityId,
                listName = todoEntity.latestUpdateData?.entityName,
                todoItems = todoEntity.latestUpdateData?.todos?.map(TodoItemState::from) ?: emptyList(),
                outOfSync = true,
                showComplete = todoEntity.showCompleted,
            )
        }
    }
}
