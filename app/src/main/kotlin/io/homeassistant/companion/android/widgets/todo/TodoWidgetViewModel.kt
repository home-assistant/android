package io.homeassistant.companion.android.widgets.todo

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class TodoWidgetViewModel @Inject constructor(
    private val todoWidgetDao: TodoWidgetDao,
    private val serverManager: ServerManager
) : ViewModel() {
    private var supportedTextColors: List<String> = emptyList()
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    val servers = serverManager.defaultServersFlow
    var selectedServerId by mutableIntStateOf(ServerManager.SERVER_ID_ACTIVE)
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    val entities: StateFlow<List<Entity<*>>> = snapshotFlow { selectedServerId }
        .distinctUntilChanged()
        .mapLatest { serverId ->
            serverManager.integrationRepository(serverId)
                .getEntities()
                .orEmpty()
                .filter { entity -> entity.domain == "todo" }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())
    var selectedEntityId by mutableStateOf<String?>(null)
    var selectedBackgroundType by mutableStateOf(WidgetBackgroundType.DAYNIGHT)
    var textColorIndex by mutableIntStateOf(0)
    var showCompletedState by mutableStateOf(true)
    var isUpdateWidget by mutableStateOf(false)

    fun onSetup(widgetId: Int, supportedTextColors: List<String>) {
        this.supportedTextColors = supportedTextColors
        if (this.widgetId == AppWidgetManager.INVALID_APPWIDGET_ID && selectedEntityId == null) {
            loadPreviousState(widgetId)
        }
        this.widgetId = widgetId
    }

    private fun loadPreviousState(widgetId: Int) = viewModelScope.launch {
        todoWidgetDao.get(widgetId)?.let {
            isUpdateWidget = true
            selectedServerId = it.serverId
            selectedEntityId = it.entityId
            selectedBackgroundType = it.backgroundType
            val colorIndex = supportedTextColors.indexOf(it.textColor)
            textColorIndex = if (colorIndex == -1) 0 else colorIndex
            showCompletedState = it.showCompleted
        }
    }

    fun setServer(serverId: Int) {
        if (selectedServerId == serverId) return
        selectedServerId = serverId
        selectedEntityId = null
    }

    fun isValidSelection(): Boolean {
        return serverManager.getServer(selectedServerId) != null &&
            selectedEntityId in entities.value.map { it.entityId }
    }

    fun prepareData(componentName: ComponentName): Intent? {
        if (!isValidSelection()) {
            Timber.d("Widget data is invalid")
            return null
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Timber.w("Widget ID is invalid")
            return null
        }

        return Intent().apply {
            action = BaseWidgetProvider.RECEIVE_DATA
            component = componentName

            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(TodoWidget.EXTRA_SERVER_ID, selectedServerId)
            putExtra(TodoWidget.EXTRA_ENTITY_ID, selectedEntityId)
            putExtra(TodoWidget.EXTRA_SHOW_COMPLETED, showCompletedState)
            putExtra(TodoWidget.EXTRA_BACKGROUND_TYPE, selectedBackgroundType)
            if (selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
                val hexForColor = supportedTextColors.getOrNull(textColorIndex) ?: supportedTextColors.first()
                putExtra(TodoWidget.EXTRA_TEXT_COLOR, hexForColor)
            }
        }
    }
}
