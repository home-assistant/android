package io.homeassistant.companion.android.settings.url

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * State of the extra URLs screen.
 *
 * @property urls the extra URLs of the server, in priority order
 * @property error error of the last add attempt, cleared once the input changes
 */
data class ExtraUrlsUiState(val urls: List<String> = emptyList(), val error: ExtraUrlError? = null)

@HiltViewModel
class ExtraUrlsViewModel @Inject constructor(
    state: SavedStateHandle,
    private val serverManager: ServerManager,
    application: Application,
) : AndroidViewModel(application) {

    private val serverId = state.get<Int>(ExtraUrlsFragment.EXTRA_SERVER) ?: -1

    private val _uiState = MutableStateFlow(ExtraUrlsUiState())
    val uiState: StateFlow<ExtraUrlsUiState> = _uiState.asStateFlow()

    /**
     * Loading and every edit read the list from the database, change it and write it back. Running them one
     * after the other keeps quick successive edits from working on a list that is already outdated.
     */
    private val editLock = Mutex()

    init {
        viewModelScope.launch {
            editLock.withLock {
                serverManager.getServer(serverId)?.let { server ->
                    _uiState.update { it.copy(urls = server.connection.extraUrls) }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Adds [input] to the extra URLs. The change is made in [viewModelScope] so it completes even when the
     * caller stops waiting, for example because the screen is left.
     *
     * @return `true` if [input] was added, so the caller can clear its text field
     */
    suspend fun addUrl(input: String): Boolean = viewModelScope.async {
        editLock.withLock {
            val connection = serverManager.getServer(serverId)?.connection ?: return@withLock false
            val known = listOfNotNull(connection.externalUrl, connection.internalUrl)
            when (val result = ExtraUrlsList.add(connection.extraUrls, input, known)) {
                is AddExtraUrlResult.Added -> {
                    save(result.urls)
                    _uiState.update { it.copy(error = null) }
                    true
                }
                AddExtraUrlResult.Invalid -> {
                    _uiState.update { it.copy(error = ExtraUrlError.Invalid) }
                    false
                }
                AddExtraUrlResult.Duplicate -> {
                    _uiState.update { it.copy(error = ExtraUrlError.Duplicate) }
                    false
                }
            }
        }
    }.await()

    /** Removes [url] from the list. The URL is used instead of a position so a stale position cannot remove another entry. */
    fun removeUrl(url: String) {
        edit { current -> ExtraUrlsList.remove(current, current.indexOf(url)) }
    }

    /** Moves [url] by [offset] positions (-1 up, +1 down). */
    fun moveUrl(url: String, offset: Int) {
        edit { current -> ExtraUrlsList.move(current, current.indexOf(url), offset) }
    }

    private fun edit(transform: (List<String>) -> List<String>) {
        viewModelScope.launch {
            editLock.withLock {
                val current = serverManager.getServer(serverId)?.connection?.extraUrls ?: return@withLock
                val updated = transform(current)
                if (updated != current) save(updated)
            }
        }
    }

    private suspend fun save(newUrls: List<String>) {
        serverManager.updateExtraUrls(serverId, newUrls)
        _uiState.update { it.copy(urls = newUrls) }
    }
}

enum class ExtraUrlError { Invalid, Duplicate }
