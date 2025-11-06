package io.homeassistant.companion.android.widgets.todo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.color.DynamicColors
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.TODO_DOMAIN
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.TodoWidgetDao
import io.homeassistant.companion.android.database.widget.TodoWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@HiltViewModel(assistedFactory = TodoWidgetConfigureViewModel.Factory::class)
class TodoWidgetConfigureViewModel @AssistedInject constructor(
    private val todoWidgetDao: TodoWidgetDao,
    private val serverManager: ServerManager,
    @Assisted preSelectedEntityId: String?,
) : ViewModel() {
    private var supportedTextColors: List<String> = emptyList()
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    val servers = serverManager.defaultServersFlow
    var selectedServerId by mutableIntStateOf(ServerManager.SERVER_ID_ACTIVE)
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    val entities: StateFlow<List<Entity>> = snapshotFlow { selectedServerId }
        .distinctUntilChanged()
        .mapLatest { serverId ->
            if (serverManager.isRegistered()) {
                serverManager.integrationRepository(serverId)
                    .getEntities()
                    .orEmpty()
                    .filter { entity -> entity.domain == TODO_DOMAIN }
            } else {
                Timber.w("No server registered")
                emptyList()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500.milliseconds), emptyList())

    // We need a mutex since the update of the entities might happen concurrently with onSetup and the viewModel creation
    private val selectedEntityMutex = Mutex()
    var selectedEntityId by mutableStateOf<String?>(preSelectedEntityId)
    var selectedBackgroundType by mutableStateOf(
        if (DynamicColors.isDynamicColorAvailable()) {
            WidgetBackgroundType.DYNAMICCOLOR
        } else {
            WidgetBackgroundType.DAYNIGHT
        },
    )
    var textColorIndex by mutableIntStateOf(0)
    var showCompletedState by mutableStateOf(true)
    var isUpdateWidget by mutableStateOf(false)

    init {
        viewModelScope.launch {
            entities.collect { entities ->
                selectedEntityMutex.withLock {
                    if (selectedEntityId == null) {
                        selectedEntityId = entities.firstOrNull()?.entityId
                    }
                }
            }
        }
    }

    fun onSetup(widgetId: Int, supportedTextColors: List<String>) {
        this.supportedTextColors = supportedTextColors
        maybeLoadPreviousState(widgetId)
        this.widgetId = widgetId
    }

    private fun maybeLoadPreviousState(widgetId: Int) = viewModelScope.launch {
        selectedEntityMutex.withLock {
            if (this@TodoWidgetConfigureViewModel.widgetId == AppWidgetManager.INVALID_APPWIDGET_ID &&
                selectedEntityId == null
            ) {
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
        }
    }

    fun setServer(serverId: Int) {
        if (selectedServerId == serverId) return
        selectedServerId = serverId
        viewModelScope.launch { selectedEntityMutex.withLock { selectedEntityId = null } }
    }

    suspend fun isValidSelection(): Boolean {
        selectedEntityMutex.withLock {
            return serverManager.getServer(selectedServerId) != null &&
                selectedEntityId in entities.value.map { it.entityId }
        }
    }

    suspend fun updateWidgetConfiguration() {
        if (!isValidSelection()) {
            Timber.d("Widget data is invalid")
            throw IllegalArgumentException("Widget data is invalid")
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Timber.w("Widget ID is invalid")
            throw IllegalArgumentException("Widget ID is invalid")
        }

        val entity = getPendingDaoEntity()
        todoWidgetDao.add(entity)
    }

    /**
     * Return a [TodoWidgetEntity] with the current selection, but without pushing this to the [todoWidgetDao]
     */
    private suspend fun getPendingDaoEntity(): TodoWidgetEntity {
        val textColor = if (selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
            supportedTextColors.getOrNull(textColorIndex) ?: supportedTextColors.first()
        } else {
            ""
        }
        selectedEntityMutex.withLock {
            val listEntityId = selectedEntityId!!

            val integrationRepository = serverManager.integrationRepository(selectedServerId)
            val webSocketRepository = serverManager.webSocketRepository(selectedServerId)
            val name = integrationRepository.getEntity(listEntityId)?.friendlyName
            val todos = webSocketRepository.getTodos(listEntityId)?.response?.get(listEntityId)?.items.orEmpty()

            return TodoWidgetEntity(
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
            )
        }
    }

    /**
     * Requests the widget to be created and waits until it has been saved to the DAO.
     *
     * **WARNING**: This function does not handle user cancellation. If a user cancels the widget creation,
     * this function will not return. If this function is called again and the user does not cancel,
     * both calls to the function will return. While this behavior could be avoided,
     * it does not cause issues in the current implementation as returning multiple times has no adverse effects.
     */
    suspend fun requestWidgetCreation(context: Context) {
        // We drop the first value since we only care about knowing when the widget is actually added
        todoWidgetDao.getWidgetCountFlow().drop(1).onStart {
            GlanceAppWidgetManager(context)
                .requestPinGlanceAppWidget(
                    TodoWidget::class.java,
                    successCallback = PendingIntent.getBroadcast(
                        context,
                        System.currentTimeMillis().toInt(),
                        Intent(context, TodoWidget::class.java).apply {
                            action = ACTION_APPWIDGET_CREATED
                            putExtra(EXTRA_WIDGET_ENTITY, getPendingDaoEntity())
                        },
                        // We need the PendingIntent to be mutable so the system inject the EXTRA_APPWIDGET_ID of the created widget
                        PendingIntent.FLAG_MUTABLE,
                    ),
                )
        }.first()
    }

    fun updateWidget(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(widgetId)
            TodoGlanceAppWidget().update(appContext, glanceId)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(preSelectedEntityId: String?): TodoWidgetConfigureViewModel
    }
}
