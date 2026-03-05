package io.homeassistant.companion.android.widgets.button

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Action
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.common.ActionFieldBinder
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class ButtonWidgetViewModel @Inject constructor(
    val buttonWidgetDao: ButtonWidgetDao,
    val serverManager: ServerManager,
) : ViewModel() {

    private var actions = mutableMapOf<Int, HashMap<String, Action>>()
    private var entities = mutableMapOf<Int, HashMap<String, Entity>>()
    var dynamicFields = ArrayList<ActionFieldBinder>()
    var selectedBackgroundType by mutableStateOf(
        if (DynamicColors.isDynamicColorAvailable()) {
            WidgetBackgroundType.DYNAMICCOLOR
        } else {
            WidgetBackgroundType.DAYNIGHT
        },
    )

    val servers = serverManager.serversFlow
    private val selectedServerMutex = Mutex()
    var selectedServerId by mutableIntStateOf(ServerManager.SERVER_ID_ACTIVE)
        private set

    fun setServer(serverId: Int) {
        if (selectedServerId == serverId) return
        selectedServerId = serverId
        viewModelScope.launch { selectedServerMutex.withLock {  } }
    }

    fun updateActionFields(text: String) {
    }
}
