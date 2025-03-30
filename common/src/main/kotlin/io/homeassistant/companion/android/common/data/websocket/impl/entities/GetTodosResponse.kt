package io.homeassistant.companion.android.common.data.websocket.impl.entities

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class GetTodosResponse(
    val response: Map<String, TodoResponse>
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TodoResponse(
        val items: List<Todo>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Todo(
        val uid: String,
        val summary: String,
        val status: String
    )
}
