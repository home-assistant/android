package io.homeassistant.companion.android.widgets.todo

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
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
class TodoWidgetConfigureViewModel @Inject constructor(
    private val todoWidgetDao: TodoWidgetDao,
    private val serverManager: ServerManager,
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
        val entity = todoWidgetDao.get(widgetId)

        entity?.let {
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

    fun prepareData() {
        if (!isValidSelection()) {
            // TODO properly react to this in the activity
            Timber.d("Widget data is invalid")
            return
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Timber.w("Widget ID is invalid")
            return
        }

        val textColor = if (selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
            supportedTextColors.getOrNull(textColorIndex) ?: supportedTextColors.first()
        } else {
            ""
        }

        viewModelScope.launch {
            val listEntityId = selectedEntityId!!
            val integrationRepository = serverManager.integrationRepository(selectedServerId)
            val webSocketRepository = serverManager.webSocketRepository(selectedServerId)
            val name = integrationRepository.getEntity(listEntityId)?.friendlyName
            val todos = webSocketRepository.getTodos(listEntityId)?.response?.get(listEntityId)?.items.orEmpty()
            todoWidgetDao.add(
                TodoWidgetEntity(
                    id = widgetId,
                    serverId = selectedServerId,
                    entityId = selectedEntityId!!,
                    backgroundType = selectedBackgroundType,
                    textColor = textColor,
                    showCompleted = showCompletedState,
                    latestUpdateData = TodoWidgetEntity.LastUpdateData(
                        entityName = name,
                        todos = todos.map {
                            TodoWidgetEntity.TodoItem(
                                uid = it.uid,
                                summary = it.summary,
                                status = it.status,
                            )
                        },
                    ),
                ),
            )
        }
    }

    fun updateWidget(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(widgetId)
            TodoGlanceAppWidget().update(appContext, glanceId)
        }
    }
}
