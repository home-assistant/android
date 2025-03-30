package io.homeassistant.companion.android.widgets.todo

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.getHexForColor
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TodoWidgetViewModel @Inject constructor(
    private val application: Application,
    private val todoWidgetDao: TodoWidgetDao,
    private val serverManager: ServerManager
) : AndroidViewModel(application) {
    private val textColors = listOf(
        application.getHexForColor(R.color.colorWidgetButtonLabelBlack),
        application.getHexForColor(android.R.color.white)
    )
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    val servers = serverManager.defaultServersFlow
    var selectedServerId by mutableIntStateOf(ServerManager.SERVER_ID_ACTIVE)
        private set

    val entities: StateFlow<List<Entity<*>>> = snapshotFlow { selectedServerId }
        .distinctUntilChanged()
        .mapLatest { serverId ->
            serverManager.integrationRepository(serverId)
                .getEntities()
                .orEmpty()
                .filter { entity -> entity.domain == "todo" }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), emptyList())
    var selectedEntityId by mutableStateOf<String?>(null)
        private set

    var selectedBackgroundType by mutableStateOf(WidgetBackgroundType.DAYNIGHT)
        private set
    var textColorIndex by mutableIntStateOf(0)
        private set

    fun onSetup(widgetId: Int) {
        if (this.widgetId == AppWidgetManager.INVALID_APPWIDGET_ID && selectedEntityId == null) {
            loadPreviousState(widgetId)
        }
        this.widgetId = widgetId
    }

    private fun loadPreviousState(widgetId: Int) = viewModelScope.launch {
        todoWidgetDao.get(widgetId)?.let {
            selectedServerId = it.serverId
            selectedEntityId = it.entityId
            selectedBackgroundType = it.backgroundType
            val colorIndex = textColors.indexOf(it.textColor)
            textColorIndex = if (colorIndex == -1) 0 else colorIndex
        }
    }

    fun setServer(serverId: Int) {
        if (selectedServerId == serverId) return
        selectedServerId = serverId
        selectedEntityId = null
    }

    fun setEntity(entityId: String?) {
        selectedEntityId = entityId
    }

    fun isValidSelection(): Boolean {
        return serverManager.getServer(selectedServerId) != null &&
            selectedEntityId in entities.value.map { it.entityId }
    }

    fun setBackgroundType(backgroundType: WidgetBackgroundType) {
        this.selectedBackgroundType = backgroundType
    }

    fun setTextColor(colorIndex: Int) {
        this.textColorIndex = colorIndex
    }

    fun prepareData(): Intent? {
        if (!isValidSelection()) return null
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return null

        return Intent().apply {
            action = BaseWidgetProvider.RECEIVE_DATA
            component = ComponentName(application, TodoWidget::class.java)

            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(TodoWidget.EXTRA_SERVER_ID, selectedServerId)
            putExtra(TodoWidget.EXTRA_ENTITY_ID, selectedEntityId)
            putExtra(TodoWidget.EXTRA_BACKGROUND_TYPE, selectedBackgroundType)
            if (selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
                val hexForColor = textColors.getOrNull(textColorIndex) ?: textColors.first()
                putExtra(TodoWidget.EXTRA_TEXT_COLOR, hexForColor)
            }
        }
    }
}
