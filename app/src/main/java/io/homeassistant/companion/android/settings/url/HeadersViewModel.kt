package io.homeassistant.companion.android.settings.url

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HeadersViewModel @Inject constructor(
    state: SavedStateHandle,
    private val serverManager: ServerManager,
    application: Application
) : AndroidViewModel(application) {

    var headers: SnapshotStateMap<String, String> = mutableStateMapOf()
        private set

    private var serverId = -1

    init {
        state.get<Int>(HeadersFragment.EXTRA_SERVER)?.let { serverId = it }
        serverManager.getServer(serverId)?.let { server ->
            server.connection.headers.entries.forEach { entry ->
                headers[entry.key] = entry.value
            }
        }
    }

    fun removeHeader(headerName: String) {
        headers.remove(headerName)
        saveHeaders()
    }

    fun setHeader(headerName: String, headerValue: String) {
        headers[headerName] = headerValue
        saveHeaders()
    }

    fun setHeaders(newHeaders: MutableMap<String, String>) {
        viewModelScope.launch {
            headers.clear()
            newHeaders.forEach {
                headers[it.key] = it.value
            }
        }
    }

    private fun saveHeaders() {
        serverManager.getServer(serverId)?.let {
            serverManager.updateServer(
                it.copy(
                    connection = it.connection.copy(
                        headers = headers
                    )
                )
            )
        }
    }

}
