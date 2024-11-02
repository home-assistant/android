package io.homeassistant.companion.android.widgets.grid.config

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.GridWidgetDao
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import io.homeassistant.companion.android.widgets.grid.GridGlanceAppWidget
import io.homeassistant.companion.android.widgets.grid.GridWidget
import io.homeassistant.companion.android.widgets.grid.asDbEntity
import io.homeassistant.companion.android.widgets.grid.asGridConfiguration
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class GridConfigurationViewModel @Inject constructor(
    private val gridWidgetDao: GridWidgetDao,
    private val serverManager: ServerManager,
    @param:ApplicationContext private val applicationContext: Context,
) : ViewModel() {
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    val servers = serverManager.defaultServersFlow

    private val _gridConfig = MutableStateFlow(GridConfiguration())
    val gridConfig = _gridConfig.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val entities: StateFlow<List<Entity>> = gridConfig
        .map { it.serverId }
        .distinctUntilChanged()
        .filterNotNull()
        .mapLatest { serverId ->
            if (serverManager.isRegistered()) {
                serverManager.integrationRepository(serverId)
                    .getEntities()
                    .orEmpty()
            } else {
                Timber.w("No server registered")
                emptyList()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500.milliseconds), emptyList())

    fun onSetup(widgetId: Int) {
        if (this.widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            loadPreviousState(widgetId)
        }
        this.widgetId = widgetId
    }

    suspend fun isValidConfig(config: GridConfiguration): Boolean {
        val entityIds = entities.value.map { it.entityId }
        return serverManager.getServer(config.serverId) != null && config.items.all { it.entityId in entityIds }
    }

    private fun loadPreviousState(widgetId: Int) = viewModelScope.launch {
        gridWidgetDao.get(widgetId)?.let { dao ->
            _gridConfig.update { dao.asGridConfiguration() }
        }
    }

    fun setServer(serverId: Int) = _gridConfig.update { currentConfig ->
        currentConfig.copy(
            serverId = serverId,
            items = if (currentConfig.serverId == serverId) currentConfig.items else emptyList(),
        )
    }

    fun setLabel(label: String) = _gridConfig.update { it.copy(label = label) }

    fun addItem(entity: Entity) = _gridConfig.update { it.copy(items = it.items + entity.asGridItem()) }

    fun editItem(i: Int, entity: Entity) = _gridConfig.update {
        it.copy(items = it.items.mapIndexed { index, it -> if (index == i) entity.asGridItem() else it })
    }

    fun deleteItem(i: Int) = _gridConfig.update {
        it.copy(items = it.items.filterIndexed { index, _ -> index != i })
    }

    suspend fun updateWidgetConfiguration(config: GridConfiguration) {
        if (!isValidConfig(config)) {
            Timber.d("Widget data is invalid")
            error("Widget data is invalid")
        }

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Timber.w("Widget ID is invalid")
            error("Widget ID is invalid")
        }

        Timber.d("Updating widget $widgetId with $config")
        val entity = config.asDbEntity(widgetId)
        gridWidgetDao.add(entity)
    }

    /**
     * Requests the widget to be created and waits until it has been saved to the DAO.
     *
     * **WARNING**: This function does not handle user cancellation. If a user cancels the widget creation,
     * this function will not return. If this function is called again and the user does not cancel,
     * both calls to the function will return. While this behavior could be avoided,
     * it does not cause issues in the current implementation as returning multiple times has no adverse effects.
     */
    suspend fun requestWidgetCreation(context: Context, config: GridConfiguration) {
        // We drop the first value since we only care about knowing when the widget is actually added
        gridWidgetDao.getWidgetCountFlow().drop(1).onStart {
            Timber.d("Requesting widget creation with config $config")
            GlanceAppWidgetManager(context)
                .requestPinGlanceAppWidget(
                    GridWidget::class.java,
                    successCallback = PendingIntent.getActivity(
                        context,
                        System.currentTimeMillis().toInt(),
                        Intent(context, GridWidget::class.java).apply {
                            action = ACTION_APPWIDGET_CREATED
                            putExtra(EXTRA_WIDGET_ENTITY, config.asDbEntity(widgetId))
                        },
                        // We need the PendingIntent to be mutable so the system inject the EXTRA_APPWIDGET_ID of the created widget
                        PendingIntent.FLAG_MUTABLE,
                    ),
                )
        }.first()
    }

    fun updateWidget(context: Context) = viewModelScope.launch {
        val appContext = context.applicationContext
        val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(widgetId)
        GridGlanceAppWidget().update(appContext, glanceId)
    }

    private fun Entity.asGridItem() = GridItem(
        entityId = entityId,
        label = friendlyName,
        icon = getIcon(applicationContext).name,
    )
}
