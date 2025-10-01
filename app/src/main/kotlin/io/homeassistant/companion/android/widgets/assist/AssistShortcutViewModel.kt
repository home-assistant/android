package io.homeassistant.companion.android.widgets.assist

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AssistPipelineListResponse
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AssistShortcutViewModel @Inject constructor(val serverManager: ServerManager, application: Application) :
    AndroidViewModel(application) {

    var serverId by mutableIntStateOf(ServerManager.SERVER_ID_ACTIVE)
        private set

    var servers by mutableStateOf(serverManager.defaultServers)
        private set

    var supported by mutableStateOf<Boolean?>(null)
        private set

    var pipelines by mutableStateOf<AssistPipelineListResponse?>(null)
        private set

    init {
        viewModelScope.launch {
            if (serverManager.isRegistered()) {
                serverManager.getServer()?.id?.let { serverId = it }
                getData()
            } else {
                supported = false
            }
        }
    }

    fun setServer(serverId: Int) {
        if (serverId == this.serverId) return

        this.serverId = serverId
        getData()
    }

    private fun getData() {
        viewModelScope.launch {
            // Loading states
            supported = null
            pipelines = null

            // Update data
            supported = serverManager.getServer(serverId)?.version?.isAtLeast(2023, 5) == true &&
                serverManager.webSocketRepository(serverId).getConfig()?.components?.contains("assist_pipeline") == true
            if (supported == true) {
                pipelines = serverManager.webSocketRepository(serverId).getAssistPipelines()
            }
        }
    }
}
