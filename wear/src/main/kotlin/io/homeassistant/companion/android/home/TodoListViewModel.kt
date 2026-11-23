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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private const val ADD_ITEM_ACTION = "add_item"
private const val REMOVE_ITEM_ACTION = "remove_item"
private const val REMOVE_COMPLETED_ACTION = "remove_completed_items"
private const val KEY_ENTITY_ID = "entity_id"
private const val KEY_ITEM = "item"

/** Loads and modifies the items of a Home Assistant to-do list for the list screen. */
@HiltViewModel
class TodoListViewModel @Inject constructor(
    private val serverManager: ServerManager,
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
) : ViewModel() {

    /** Where the loading of the to-do lists of the server stands. */
    sealed interface ListsStatus {
        data object Loading : ListsStatus

        data object Loaded : ListsStatus

        data object Error : ListsStatus
    }

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

    private val _listsStatus = MutableStateFlow<ListsStatus>(ListsStatus.Loading)

    /** Tells lists that are still loading or failed to load from a server without to-do lists. */
    val listsStatus: StateFlow<ListsStatus> = _listsStatus.asStateFlow()

    private val listsRetry = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Incremented each time another list is opened, to ignore the late results of the previous one
    private var generation = 0

    // Modifications sent and not yet answered for the open list, their optimistic result is on screen
    private var pendingModifications = 0

    // The items of the open list as the server last returned them
    private var confirmedItems: List<TodoItem> = emptyList()

    private var loadJob: Job? = null

    /** Keeps [lists] current until cancelled, observing again when [retryLists] is called. */
    suspend fun observeLists() {
        listsRetry.collectLatest { _ ->
            if (_listsStatus.value != ListsStatus.Loaded) _listsStatus.value = ListsStatus.Loading
            entitiesForDisplayManager
                .observeInContext(ServerManager.SERVER_ID_ACTIVE) { it.domain == TODO_DOMAIN }
                .collect { state ->
                    when (state) {
                        is EntityDisplayState.Loaded -> {
                            _lists.value = state.entities.sortedBy { it.name }
                            _listsStatus.value = ListsStatus.Loaded
                        }
                        // Keep showing the lists already known when only a refresh failed
                        is EntityDisplayState.Error -> if (_lists.value.isEmpty()) {
                            _listsStatus.value = ListsStatus.Error
                        }
                        EntityDisplayState.Loading -> Unit
                    }
                }
        }
    }

    /** Tries again to load the lists after [ListsStatus.Error]. */
    fun retryLists() {
        listsRetry.update { it + 1 }
    }

    /** Loads the items of [entityId], showing the loading state only when it is another list. */
    fun load(entityId: String) {
        val switching = _uiState.value.entityId != entityId
        if (switching) {
            generation++
            pendingModifications = 0
            confirmedItems = emptyList()
        }
        _uiState.update {
            if (switching) UiState(entityId = entityId, isLoading = true) else it.copy(hasLoadError = false)
        }
        loadJob?.cancel()
        val requestGeneration = generation
        loadJob = viewModelScope.launch { fetch(entityId, requestGeneration) }
    }

    fun setCompleted(entityId: String, item: TodoItem, completed: Boolean) {
        val key = item.key() ?: return
        val status = if (completed) TodoItem.COMPLETED_STATUS else TodoItem.NEEDS_ACTION_STATUS
        // Optimistic update, the fetch following the call restores the server state on failure
        updateOpenList(entityId) { state ->
            state.copy(items = state.items.map { if (it.key() == key) it.copy(status = status) else it })
        }
        modify(entityId) { serverManager.webSocketRepository().updateTodo(entityId, key, null, status) }
    }

    fun addItem(entityId: String, summary: String) {
        val trimmed = summary.trim()
        if (trimmed.isEmpty()) return
        modify(entityId) { callTodoAction(ADD_ITEM_ACTION, mapOf(KEY_ENTITY_ID to entityId, KEY_ITEM to trimmed)) }
    }

    fun removeItem(entityId: String, item: TodoItem) {
        val key = item.key() ?: return
        updateOpenList(entityId) { state -> state.copy(items = state.items.filterNot { it.key() == key }) }
        modify(entityId) { callTodoAction(REMOVE_ITEM_ACTION, mapOf(KEY_ENTITY_ID to entityId, KEY_ITEM to key)) }
    }

    fun clearCompleted(entityId: String) {
        updateOpenList(entityId) { state ->
            state.copy(items = state.items.filterNot { it.status == TodoItem.COMPLETED_STATUS })
        }
        modify(entityId) { callTodoAction(REMOVE_COMPLETED_ACTION, mapOf(KEY_ENTITY_ID to entityId)) }
    }

    private fun updateOpenList(entityId: String, transform: (UiState) -> UiState) {
        _uiState.update { if (it.entityId == entityId) transform(it) else it }
    }

    /** Calls a service of the to-do domain, throwing when Home Assistant does not accept it. */
    private suspend fun callTodoAction(action: String, data: Map<String, Any?>): Boolean {
        serverManager.integrationRepository().callAction(TODO_DOMAIN, action, data)
        return true
    }

    private fun modify(entityId: String, call: suspend () -> Boolean) {
        val isOpenList = _uiState.value.entityId == entityId
        if (isOpenList) pendingModifications++
        val requestGeneration = generation
        viewModelScope.launch {
            val success = try {
                call()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Unable to modify to-do list $entityId")
                false
            }
            // Another list is open now, its state is not ours to change
            if (!isOpenList || requestGeneration != generation) return@launch
            pendingModifications = (pendingModifications - 1).coerceAtLeast(0)
            _uiState.update { it.copy(hasActionError = !success) }
            fetch(entityId, requestGeneration)
        }
    }

    private suspend fun fetch(entityId: String, requestGeneration: Int) {
        val items = try {
            serverManager.webSocketRepository().getTodos(entityId)?.response?.get(entityId)?.items
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unable to load to-do list $entityId")
            null
        }
        if (requestGeneration != generation) return
        if (items != null) confirmedItems = items
        // The answer to a modification still waiting would drop its optimistic result, the last one wins
        if (pendingModifications > 0) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        _uiState.update {
            if (items == null) {
                // Show what the server last confirmed instead of edits that were not saved
                it.copy(items = confirmedItems, isLoading = false, hasLoadError = true)
            } else {
                it.copy(items = items, isLoading = false, hasLoadError = false)
            }
        }
    }

    /** What Home Assistant identifies an item by: its uid, or its summary when it has none. */
    private fun TodoItem.key(): String? = uid ?: summary
}
