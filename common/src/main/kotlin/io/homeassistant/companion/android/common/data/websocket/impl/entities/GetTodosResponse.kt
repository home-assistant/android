package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class GetTodosResponse(
    val response: Map<String, TodoResponse>
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TodoResponse(
        val items: List<TodoItem>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
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
