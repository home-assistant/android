package io.homeassistant.companion.android.widgets.camera

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CAMERA_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.IMAGE_DOMAIN
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
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


@HiltViewModel(assistedFactory = CameraWidgetConfigureViewModel.Factory::class)
class CameraWidgetConfigureViewModel @AssistedInject constructor(
    private val cameraWidgetDao: CameraWidgetDao,
    private val serverManager: ServerManager,
    @Assisted preselectedEntityId : String?,
) : ViewModel() {
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    val servers = serverManager.serversFlow
    var selectedServerId by mutableIntStateOf(ServerManager.SERVER_ID_ACTIVE)
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    val entities: StateFlow<List<Entity>> = snapshotFlow { selectedServerId }
        .distinctUntilChanged()
        .mapLatest { serverId ->
            if (serverManager.isRegistered()) {
                try {
                    serverManager.integrationRepository(serverId)
                        .getEntities()
                        .orEmpty()
                        .filter { entity -> entity.domain == CAMERA_DOMAIN || entity.domain == IMAGE_DOMAIN }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get entities")
                    emptyList()
                }
            } else {
                Timber.w("No server registered")
                emptyList()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500.milliseconds), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val entityRegistry: StateFlow<List<EntityRegistryResponse>?> = snapshotFlow { selectedServerId }
        .distinctUntilChanged()
        .mapLatest { serverId ->
            if (serverManager.isRegistered()) {
                try {
                    serverManager.webSocketRepository(serverId).getEntityRegistry()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get entity registry")
                    null
                }
            } else {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500.milliseconds), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val deviceRegistry: StateFlow<List<DeviceRegistryResponse>?> = snapshotFlow { selectedServerId }
        .distinctUntilChanged()
        .mapLatest { serverId ->
            if (serverManager.isRegistered()) {
                try {
                    serverManager.webSocketRepository(serverId).getDeviceRegistry()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get device registry")
                    null
                }
            } else {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500.milliseconds), null)

    val areaRegistry: StateFlow<List<AreaRegistryResponse>?> = snapshotFlow { selectedServerId }
        .distinctUntilChanged()
        .mapLatest { serverId ->
            if (serverManager.isRegistered()) {
                try {
                    serverManager.webSocketRepository(serverId).getAreaRegistry()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get area registry")
                    null
                }
            } else {
                null
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500.milliseconds), null)


    private val selectedEntityMutex = Mutex()
    var selectedEntityId by mutableStateOf<String?>(preselectedEntityId)
    var selectedTapAction by mutableStateOf(WidgetTapAction.REFRESH)
    var isUpdateWidget by mutableStateOf(false)


    init {
        viewModelScope.launch {
            entities.collect { entities ->
                selectedEntityMutex.withLock {
                    if(selectedEntityId == null) {
                        selectedEntityId = entities.firstOrNull()?.entityId
                    }
                }
            }
        }
    }

    fun onSetup(widgetId: Int) {
        maybeLoadPreviousState(widgetId)
        this.widgetId = widgetId
    }

    private fun maybeLoadPreviousState(widgetId: Int) = viewModelScope.launch {
        selectedEntityMutex.withLock {
            if(this@CameraWidgetConfigureViewModel.widgetId == AppWidgetManager.INVALID_APPWIDGET_ID && selectedEntityId == null) {
                cameraWidgetDao.get(widgetId)?.let {
                    isUpdateWidget = true
                    selectedServerId = it.serverId
                    selectedEntityId = it.entityId
                    selectedTapAction = it.tapAction
                }
            }
        }
    }

    fun setServer(serverId: Int) {
        if(serverId == selectedServerId) return
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
        if(!isValidSelection()) {
            Timber.d("Widget data is invalid")
            throw IllegalArgumentException("Widget data is invalid")
        }
        if(widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Timber.w("Widget ID is invalid")
            throw IllegalArgumentException("Widget ID is invalid")
        }

        val entity = getPendingDaoEntity()
        cameraWidgetDao.add(entity)
    }

    private suspend fun getPendingDaoEntity(): CameraWidgetEntity {
        selectedEntityMutex.withLock {
            return CameraWidgetEntity(
                id = widgetId,
                serverId = selectedServerId,
                entityId = selectedEntityId!!,
                tapAction = selectedTapAction
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun requestWidgetCreation(ctx: Context) {
        cameraWidgetDao.getWidgetCountFlow().drop(1).onStart {
            val appWidgetManager = AppWidgetManager.getInstance(ctx)
            val provider = appWidgetManager.getInstalledProviders()
                .first { it.provider.className == CameraWidget::class.java.name }

            appWidgetManager.requestPinAppWidget(
                provider.provider,
                null,
                PendingIntent.getBroadcast(
                    ctx,
                    System.currentTimeMillis().toInt(),
                    Intent(ctx, CameraWidget::class.java).apply {
                        action = ACTION_APPWIDGET_CREATED
                        putExtra(EXTRA_WIDGET_ENTITY, getPendingDaoEntity())
                    },
                    PendingIntent.FLAG_MUTABLE
                )
            )
        }.first()
    }

    fun updateWidget(ctx: Context) {
        val appContext = ctx.applicationContext
        viewModelScope.launch {
            val intent = Intent(appContext, CameraWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
            }
            appContext.sendBroadcast(intent)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(preselectedEntityId: String?) : CameraWidgetConfigureViewModel
    }
}
