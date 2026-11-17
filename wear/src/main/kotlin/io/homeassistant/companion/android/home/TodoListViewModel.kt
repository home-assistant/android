package io.homeassistant.companion.android.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.TODO_DOMAIN
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse.TodoItem
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** Loads and modifies the items of a Home Assistant to-do list for the list screen. */
@HiltViewModel
class TodoListViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
) : ViewModel() {

    data class UiState(
        val entityId: String? = null,
        val items: List<TodoItem> = emptyList(),
        val isLoading: Boolean = false,
        val hasLoadError: Boolean = false,
        val hasActionError: Boolean = false,
    )

    private val _lists = MutableStateFlow<List<EntityDisplay>>(emptyList())

    /** The to-do list entities of the server, kept current by [observeLists]. */
    val lists: StateFlow<List<EntityDisplay>> = _lists.asStateFlow()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Keeps [lists] current until cancelled. */
    suspend fun observeLists() {
        entitiesForDisplayManager
            .observeInContext(ServerManager.SERVER_ID_ACTIVE) { it.domain == TODO_DOMAIN }
            .collect { state ->
                if (state is EntityDisplayState.Loaded) {
                    _lists.value = state.entities.sortedBy { it.name }
                }
            }
    }

    /** Loads the items of [entityId], showing the loading state only when it is another list. */
    fun load(entityId: String) {
        _uiState.update {
            if (it.entityId == entityId) {
                it.copy(hasLoadError = false)
            } else {
                UiState(entityId = entityId, isLoading = true)
            }
        }
        viewModelScope.launch { fetch(entityId) }
    }

    fun setCompleted(entityId: String, item: TodoItem, completed: Boolean) {
        val key = item.key() ?: return
        val status = if (completed) TodoItem.COMPLETED_STATUS else TodoItem.NEEDS_ACTION_STATUS
        // Optimistic update, the fetch following the call restores the server state on failure
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.key() == key) it.copy(status = status) else it })
        }
        modify(entityId) { it.updateTodo(entityId, key, null, status) }
    }

    fun addItem(entityId: String, summary: String) {
        val trimmed = summary.trim()
        if (trimmed.isEmpty()) return
        modify(entityId) { it.addTodo(entityId, trimmed) }
    }

    fun removeItem(entityId: String, item: TodoItem) {
        val key = item.key() ?: return
        _uiState.update { state -> state.copy(items = state.items.filterNot { it.key() == key }) }
        modify(entityId) { it.removeTodo(entityId, key) }
    }

    fun clearCompleted(entityId: String) {
        _uiState.update { state -> state.copy(items = state.items.filterNot { it.status == TodoItem.COMPLETED_STATUS }) }
        modify(entityId) { it.removeCompletedTodos(entityId) }
    }

    private fun modify(
        entityId: String,
        call: suspend (io.homeassistant.companion.android.common.data.websocket.WebSocketRepository) -> Boolean,
    ) {
        viewModelScope.launch {
            val success = try {
                call(serverManager.webSocketRepository())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unable to modify to-do list $entityId")
                false
            }
            _uiState.update { it.copy(hasActionError = !success) }
            fetch(entityId)
        }
    }

    private suspend fun fetch(entityId: String) {
        try {
            val items = serverManager.webSocketRepository().getTodos(entityId)?.response?.get(entityId)?.items
            _uiState.update {
                if (items == null) {
                    it.copy(isLoading = false, hasLoadError = it.items.isEmpty())
                } else {
                    it.copy(items = items, isLoading = false, hasLoadError = false)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unable to load to-do list $entityId")
            _uiState.update { it.copy(isLoading = false, hasLoadError = it.items.isEmpty()) }
        }
    }

    /** What Home Assistant identifies an item by: its uid, or its summary when it has none. */
    private fun TodoItem.key(): String? = uid ?: summary
}
