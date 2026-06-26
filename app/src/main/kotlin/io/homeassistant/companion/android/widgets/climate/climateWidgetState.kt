package io.homeassistant.companion.android.widgets.climate

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
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.HomeAssistantGlanceTheme
import io.homeassistant.companion.android.util.compose.glanceHaLightColors
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class ClimateItemState(val uid: String?, val name: String, val done: Boolean) : Parcelable {
    companion object {
        fun from(todoItem: TodoWidgetEntity.TodoItem): ClimateItemState {           // TODO: cambiar entity a climate
            return ClimateItemState(
                uid = todoItem.uid,
                name = todoItem.summary ?: "",
                done = todoItem.status == COMPLETED_STATUS,
            )
        }
    }
}

internal sealed interface ClimateState {
    val backgroundType: WidgetBackgroundType
        get() = if (SdkVersion.isAtLeast(Build.VERSION_CODES.S)) {
            WidgetBackgroundType.DYNAMICCOLOR
        } else {
            WidgetBackgroundType.DAYNIGHT
        }
    val textColor: String?
        get() = null

    companion object {
        @Composable
        fun ClimateState.getColors(): ColorProviders {
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

internal object LoadingClimateState : ClimateState
internal object EmptyClimateState : ClimateState

internal data class ClimateStateWithData(
    override val backgroundType: WidgetBackgroundType,
    override val textColor: String?,
    val serverId: Int,
    val currentTemp: Float? = null,
    val climateTemp: Float? = null,
    val outOfSync: Boolean,
    val showComplete: Boolean,
) : ClimateState {

    fun isControlEnabled(): Boolean {
        return showComplete && climateTemp != null
    }

    // TODO: crear DAO ENTITIES para climate
    companion object {
        /**
         * Create a complete [ClimateStateWithData] from the DB and from the server. Set the flag [outOfSync] to false, since the data
         * includes an updated state from the server.
         */
        fun from(
            todoEntity: TodoWidgetEntity,
            entity: Entity,
            todos: List<TodoWidgetEntity.TodoItem>,
        ): ClimateStateWithData {
            return ClimateStateWithData(
                backgroundType = todoEntity.backgroundType,
                textColor = todoEntity.textColor,
                serverId = todoEntity.serverId,
//                listEntityId = entity.entityId,
//                listName = entity.friendlyName,
//                todoItems = todos.map(ClimateItemState::from),
                outOfSync = false,
                showComplete = todoEntity.showCompleted,
            )
        }

        /**
         * Create a [ClimateStateWithData] with data only from the DB. Set the flag [outOfSync] to true, since the data
         * doesn't have an updated state from the server.
         */
        fun from(todoEntity: TodoWidgetEntity): ClimateStateWithData {
            return ClimateStateWithData(
                backgroundType = todoEntity.backgroundType,
                textColor = todoEntity.textColor,
                serverId = todoEntity.serverId,
//                listEntityId = todoEntity.entityId,
//                listName = todoEntity.latestUpdateData?.entityName,
//                todoItems = todoEntity.latestUpdateData?.todos?.map(ClimateItemState::from) ?: emptyList(),
                outOfSync = true,
                showComplete = todoEntity.showCompleted,
            )
        }
    }
}
