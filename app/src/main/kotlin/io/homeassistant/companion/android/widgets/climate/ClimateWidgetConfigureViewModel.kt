package io.homeassistant.companion.android.widgets.climate

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.RemoteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.color.DynamicColors
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.CLIMATE_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.widget.ClimateWidgetDao
import io.homeassistant.companion.android.database.widget.ClimateWidgetEntity
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import io.homeassistant.companion.android.widgets.mediaplayer.MediaPlayerControlsWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber


/**
 * Stable request code for the widget-creation broadcast [PendingIntent]. A fixed value (combined with
 * [PendingIntent.FLAG_UPDATE_CURRENT]) keeps the pending intent deterministic and testable while making
 * sure a reconfigured request replaces the previously registered extras.
 */
private const val PIN_WIDGET_REQUEST_CODE = 0

@HiltViewModel(assistedFactory = ClimateWidgetConfigureViewModel.Factory::class)
class ClimateWidgetConfigureViewModel @AssistedInject constructor(
    private val climateWidgetDao: ClimateWidgetDao,
    private val serverManager: ServerManager,
    private val entitiesForDisplayManager: EntitiesForDisplayManager,
    @Assisted private val widgetId: Int,
    @Assisted preselectedEntityId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ClimateWidgetConfigureState(
            selectedEntityId = preselectedEntityId ?: "",
            dynamicColorAvailable = DynamicColors.isDynamicColorAvailable(),
            selectedBackgroundType = if (DynamicColors.isDynamicColorAvailable()) {
                WidgetBackgroundType.DYNAMICCOLOR
            } else {
                WidgetBackgroundType.DAYNIGHT
            },
        ),
    )

    internal val state: StateFlow<ClimateWidgetConfigureState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<Int>(replay = 1)

    /** Errors to surface to the user, as string resources. */
    val errors = _errors.asSharedFlow()

    private var loadEntitiesJob: Job? = null

    init {
        viewModelScope.launch { restoreConfiguration() }

        viewModelScope.launch {
            serverManager.serversFlow.collect { servers ->

                Timber.i("servers climate: $servers")
                _state.update { current ->
                    current.copy(
                        serversDropdownItems = servers.map { server ->
                            HADropdownItem(key = server.id, label = server.friendlyName)
                        },
                    )
                }
            }
        }
    }

    fun onServerSelected(serverId: Int) {
        if (serverId == _state.value.selectedServerId) return
        _state.update { it.changeServer(serverId) }
        loadEntities(serverId)
    }

    fun onChangeEntity(entityId: String) {
        _state.update { current ->
            current.copy(selectedEntityId = entityId)
        }
    }

    fun onTextColorChange(textColor: String) {
        _state.update { it.copy(textColor = textColor) }
    }

    fun onBackgroundTypeSelected(backgroundType: WidgetBackgroundType) {
        _state.update { it.copy(selectedBackgroundType = backgroundType) }
    }

    /**
     * Persists the current configuration, reporting through [errors] and returning false when it
     * cannot be saved.
     */
    suspend fun updateWidgetConfiguration(): Boolean {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Timber.e("Cannot save the widget configuration, the widget ID is invalid")
            _errors.emit(commonR.string.widget_update_error)
            return false
        }
        val widget = getPendingDaoEntity()
        if (widget == null) {
            _errors.emit(commonR.string.widget_update_error)
            return false
        }

        Timber.d("ClimateConfig add: $widget")
        climateWidgetDao.add(widget)
        return true
    }

    /**
     * Requests the widget to be pinned and waits until it has been saved to the DAO.
     *
     * **WARNING**: This function does not handle user cancellation. If a user cancels the widget creation,
     * this function will not return. If this function is called again and the user does not cancel,
     * both calls to the function will return. While this behavior could be avoided,
     * it does not cause issues in the current implementation as returning multiple times has no adverse effects.
     */
    suspend fun requestWidgetCreation(context: Context): Boolean {
        if (!SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            Timber.e("Cannot pin the widget, pinning requires API ${Build.VERSION_CODES.O}")
            _errors.emit(commonR.string.widget_creation_error)
            return false
        }

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val pinningSupported = try {
            appWidgetManager.isRequestPinAppWidgetSupported
        } catch (e: RemoteException) {
            Timber.e(e, "Unable to read isRequestPinAppWidgetSupported")
            false
        }

        if (!pinningSupported) {
            Timber.e("Cannot pin the widget, the launcher does not support it")
            _errors.emit(commonR.string.widget_creation_error)
            return false
        }

        val widget = getPendingDaoEntity()
        if (widget == null) {
            _errors.emit(commonR.string.widget_creation_error)
            return false
        }

        var requestAccepted = false

        // We drop the first value since we only care about knowing when the widget is actually added.
        climateWidgetDao.getWidgetCountFlow().drop(1).onStart {
            requestAccepted = appWidgetManager.requestPinAppWidget(
                ComponentName(context, MediaPlayerControlsWidget::class.java),          // TODO climate: revisar como reemplazar estos intent
                null,
                PendingIntent.getBroadcast(
                    context,
                    PIN_WIDGET_REQUEST_CODE,
                    Intent(context, MediaPlayerControlsWidget::class.java).apply {
                        action = ACTION_APPWIDGET_CREATED
                        putExtra(EXTRA_WIDGET_ENTITY, widget)
                    },
                    // FLAG_MUTABLE: the system injects the created EXTRA_APPWIDGET_ID.
                    // FLAG_UPDATE_CURRENT: refresh the extras when the screen is reconfigured and re-requested.
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            // A rejected request never adds a widget, so emit to stop waiting for one
            if (!requestAccepted) emit(0)
        }.first()

        if (!requestAccepted) {
            Timber.e("The launcher rejected the widget pin request")
            _errors.emit(commonR.string.widget_creation_error)
        }
        return requestAccepted
    }

    fun updateWidget(context: Context) {
        context.sendBroadcast(
            Intent(context, MediaPlayerControlsWidget::class.java).apply {
                action = BaseWidgetProvider.UPDATE_WIDGETS
            },
        )
    }

    /**
     * Restores the configuration of an existing widget, or falls back to the active server for a new one.
     */
    private suspend fun restoreConfiguration() {
        val existingWidget = if (
            widgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
            _state.value.selectedEntityId == null
        ) {
            climateWidgetDao.get(widgetId)
        } else {
            null
        }

        if (existingWidget != null) {
            _state.update {
                it.copy(
                    selectedServerId = existingWidget.serverId,
                    // Widgets store one or several comma-separated entities; restore all of them
                    // (de-duplicated) so the multi-player "show whichever is currently playing"
                    // behaviour is preserved without rendering or re-saving duplicate rows.
                    selectedEntityId = existingWidget.entityId,
                    textColor = existingWidget.textColor.orEmpty(),
                    selectedBackgroundType = existingWidget.backgroundType,
                    isUpdateWidget = true,
                )
            }
        } else {
            _state.update {
                it.copy(selectedServerId = serverManager.getServer()?.id ?: ServerManager.SERVER_ID_ACTIVE)
            }
        }
        loadEntities(_state.value.selectedServerId)
    }

    private suspend fun isValidSelection(): Boolean {
        val current = _state.value
//        return current.isActionEnabled && // TODO climate: REVISAR
        return serverManager.getServer(current.selectedServerId) != null
    }

    private suspend fun getPendingDaoEntity(): ClimateWidgetEntity? {
        if (!isValidSelection()) {
            Timber.e("Cannot build the widget, the current configuration is invalid")
            return null
        }
        val current = _state.value

        // Persist only the entities resolved on the server, so ids that no longer exist (and are
        // not rendered as selected rows) cannot silently end up in the widget.
        val entityId = current.selectedEntityId

        if (entityId.isEmpty()) {
            Timber.e("Cannot build the widget, the selected entities are unknown on the server")
            return null
        }

        val name = (current.entityDisplayState as? EntityDisplayState.Loaded)?.entity(entityId)?.name.orEmpty()

        Timber.i("Name de la entidad: $name")
        return ClimateWidgetEntity(
            id = widgetId,
            serverId = current.selectedServerId,
            entityId = entityId,
            textColor = current.textColor,
            backgroundType = current.selectedBackgroundType,
            latestUpdateData = ClimateWidgetEntity.LastUpdateData(entityName = name)
        )
    }

    private fun loadEntities(serverId: Int) {
        loadEntitiesJob?.cancel()
        loadEntitiesJob = viewModelScope.launch {
            if (!serverManager.isRegistered()) {
                Timber.w("No server registered")
                _state.update { it.copy(entityDisplayState = EntityDisplayState.Loaded(emptyList())) }
                return@launch
            }
            entitiesForDisplayManager.snapshotInContext(serverId) { it.domain == CLIMATE_DOMAIN }
                .collect { displayState ->
                    Timber.i("entities display: ${displayState}")
                    _state.update { it.copy(entityDisplayState = displayState) }
                }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(widgetId: Int, preselectedEntityId: String?): ClimateWidgetConfigureViewModel
    }
}
