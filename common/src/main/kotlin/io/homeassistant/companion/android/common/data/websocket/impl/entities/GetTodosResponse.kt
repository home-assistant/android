package io.homeassistant.companion.android.common.data.websocket.impl.entities

import kotlinx.serialization.Serializable

@Serializable
data class GetTodosResponse(
    val response: Map<String, TodoResponse>
) {

    @Serializable
    data class TodoResponse(
        val items: List<TodoItem>
    )

    @Serializable
    data class TodoItem(
        val uid: String?,
        val summary: String?,
        val status: String?
    ) {

        companion object {
            const val COMPLETED_STATUS = "completed"
            const val NEEDS_ACTION_STATUS = "needs_action"
        }
    }
}
