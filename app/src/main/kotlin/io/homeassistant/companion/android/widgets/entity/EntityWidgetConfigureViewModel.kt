package io.homeassistant.companion.android.widgets.entity

import android.annotation.SuppressLint
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
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.GetEntitiesForDisplayUseCase
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import kotlinx.coroutines.CancellationException
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

@HiltViewModel(assistedFactory = EntityWidgetConfigureViewModel.Factory::class)
class EntityWidgetConfigureViewModel @AssistedInject constructor(
    private val staticWidgetDao: StaticWidgetDao,
    private val serverManager: ServerManager,
    private val getEntitiesForDisplay: GetEntitiesForDisplayUseCase,
    @Assisted private val widgetId: Int,
    @Assisted preselectedEntityId: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        EntityWidgetConfigureState(
            selectedEntityId = preselectedEntityId,
            dynamicColorAvailable = DynamicColors.isDynamicColorAvailable(),
            selectedBackgroundType = if (DynamicColors.isDynamicColorAvailable()) {
                WidgetBackgroundType.DYNAMICCOLOR
            } else {
                WidgetBackgroundType.DAYNIGHT
            },
        ),
    )
    internal val state: StateFlow<EntityWidgetConfigureState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<Int>(replay = 1)

    /** Errors to surface to the user, as string resources. */
    val errors = _errors.asSharedFlow()

    private var labelFromEntity = false
    private var loadEntitiesJob: Job? = null
    private var loadAttributesJob: Job? = null

    init {
        viewModelScope.launch { restoreConfiguration() }

        viewModelScope.launch {
            serverManager.serversFlow.collect { servers ->
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

    /**
     * Restores the configuration of an existing widget, or falls back to the active server for a new one.
     */
    private suspend fun restoreConfiguration() {
        val widget = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && _state.value.selectedEntityId == null) {
            staticWidgetDao.get(widgetId)
        } else {
            null
        }

        if (widget != null) {
            _state.update {
                it.copy(
                    selectedServerId = widget.serverId,
                    selectedEntityId = widget.entityId,
                    selectedAttributeIds = widget.attributeIds.toAttributeIdsList(),
                    label = widget.label.orEmpty(),
                    textSize = widget.textSize.toInt().toString(),
                    stateSeparator = widget.stateSeparator,
                    attributeSeparator = widget.attributeSeparator,
                    selectedTapAction = widget.tapAction,
                    selectedBackgroundType = widget.backgroundType,
                    textColorHex = widget.textColor,
                    isUpdateWidget = true,
                )
            }
        } else {
            _state.update {
                it.copy(selectedServerId = serverManager.getServer()?.id ?: ServerManager.SERVER_ID_ACTIVE)
            }
        }

        loadEntities(_state.value.selectedServerId)
        loadAttributes(_state.value.selectedEntityId)
    }

    fun onServerSelected(serverId: Int) {
        if (serverId == _state.value.selectedServerId) return

        _state.update { it.changeServer(serverId) }
        loadEntities(serverId)
        loadAttributes(entityId = null)
    }

    fun onEntitySelected(entityId: String?) {
        _state.update { current ->
            val updated = current.copy(
                selectedEntityId = entityId,
                selectedAttributeIds = emptyList(),
                availableAttributes = emptyList(),
            )
            updated.copy(
                selectedTapAction = if (updated.isToggleable) WidgetTapAction.TOGGLE else WidgetTapAction.REFRESH,
            )
        }
        syncLabelWithSelectedEntity()
        loadAttributes(entityId)
    }

    fun onAttributeAdded(attributeId: String) {
        _state.update {
            if (attributeId in it.selectedAttributeIds) {
                it
            } else {
                it.copy(selectedAttributeIds = it.selectedAttributeIds + attributeId)
            }
        }
    }

    fun onAttributeRemoved(attributeId: String) {
        _state.update { it.copy(selectedAttributeIds = it.selectedAttributeIds - attributeId) }
    }

    fun onCustomAttributeChanged(value: String) {
        _state.update { it.copy(customAttribute = value) }
    }

    fun onCustomAttributesAdded() {
        _state.update { current ->
            val attributes = current.customAttribute.toAttributeIdsList()

            if (attributes.isEmpty()) {
                current
            } else {
                current.copy(
                    selectedAttributeIds = current.selectedAttributeIds +
                        attributes.filterNot(current.selectedAttributeIds::contains),
                    customAttribute = "",
                )
            }
        }
    }

    fun onLabelChanged(value: String) {
        _state.update { it.copy(label = value) }
        labelFromEntity = false
    }

    fun onTextSizeChanged(value: String) {
        _state.update { it.copy(textSize = value.filter(Char::isDigit)) }
    }

    fun onStateSeparatorChanged(value: String) {
        _state.update { it.copy(stateSeparator = value) }
    }

    fun onAttributeSeparatorChanged(value: String) {
        _state.update { it.copy(attributeSeparator = value) }
    }

    fun onTapActionSelected(action: WidgetTapAction) {
        _state.update { it.copy(selectedTapAction = action) }
    }

    fun onBackgroundTypeSelected(backgroundType: WidgetBackgroundType) {
        _state.update { it.copy(selectedBackgroundType = backgroundType) }
    }

    internal fun onTextColorSelected(colorHex: String) {
        _state.update { it.copy(textColorHex = colorHex) }
    }

    private fun loadEntities(serverId: Int) {
        loadEntitiesJob?.cancel()
        loadEntitiesJob = viewModelScope.launch {
            if (!serverManager.isRegistered()) {
                Timber.w("No server registered")
                _state.update { it.copy(entityDisplayState = EntityDisplayState.Loaded(emptyList())) }
                return@launch
            }
            getEntitiesForDisplay.snapshot(serverId).collect { displayState ->
                _state.update { it.copy(entityDisplayState = displayState) }
                // The selection can be set before the entities resolve (preselected entity or
                // restored widget), so the generated label is refreshed once they are available.
                syncLabelWithSelectedEntity()
            }
        }
    }

    /**
     * Loads the attributes the selected entity exposes, offered as suggestions when the user
     * appends attributes to the widget.
     */
    private fun loadAttributes(entityId: String?) {
        loadAttributesJob?.cancel()
        loadAttributesJob = viewModelScope.launch {
            val attributes = entityId?.let { readAttributes(it) }.orEmpty()
            _state.update { it.copy(availableAttributes = attributes) }
        }
    }

    private suspend fun readAttributes(entityId: String): List<String> = try {
        serverManager.integrationRepository(_state.value.selectedServerId).getEntity(entityId)
            ?.attributes
            ?.keys
            .orEmpty()
            .sorted()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Failed to load the attributes of the selected entity")
        emptyList()
    }

    /**
     * Aligns the label with the display name of the selected entity, unless the user typed their own.
     */
    private fun syncLabelWithSelectedEntity() {
        val current = _state.value
        val entity = current.selectedEntity ?: return
        val name = entity.name.takeIf { it != entity.entityId }.orEmpty()

        if (current.label == name) {
            labelFromEntity = name.isNotEmpty()
        } else if (current.label.isBlank() || labelFromEntity) {
            _state.update { it.copy(label = name) }
            labelFromEntity = name.isNotEmpty()
        }
    }

    private suspend fun isValidSelection(): Boolean {
        val current = _state.value
        return current.isActionEnabled &&
            serverManager.getServer(current.selectedServerId) != null &&
            current.selectedEntity != null
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

        staticWidgetDao.add(widget)
        return true
    }

    /** Asks the already placed widgets to redraw with the configuration that was just saved. */
    fun updateWidget(context: Context) {
        context.sendBroadcast(
            Intent(context, EntityWidget::class.java).apply {
                action = BaseWidgetProvider.UPDATE_WIDGETS
            },
        )
    }

    /**
     * Builds the widget to persist from the current configuration, or null when it is incomplete.
     */
    internal suspend fun getPendingDaoEntity(): StaticWidgetEntity? {
        if (!isValidSelection()) {
            Timber.e("Cannot build the widget, the current configuration is invalid")
            return null
        }
        val current = _state.value
        val entity = current.selectedEntity
        if (entity == null) {
            Timber.e("Cannot build the widget, the selected entity is unknown on the server")
            return null
        }

        return StaticWidgetEntity(
            id = widgetId,
            serverId = current.selectedServerId,
            entityId = entity.entityId,
            attributeIds = current.selectedAttributeIds.takeIf { it.isNotEmpty() }?.joinToString(","),
            label = current.label,
            textSize = current.textSizeOrDefault,
            stateSeparator = current.stateSeparator,
            attributeSeparator = current.attributeSeparator
                .takeIf { current.selectedAttributeIds.isNotEmpty() }
                .orEmpty(),
            tapAction = if (current.isToggleable) current.selectedTapAction else WidgetTapAction.REFRESH,
            lastUpdate = staticWidgetDao.get(widgetId)?.lastUpdate.orEmpty(),
            backgroundType = current.selectedBackgroundType,
            textColor = current.textColorHex.takeIf {
                current.selectedBackgroundType == WidgetBackgroundType.TRANSPARENT
            },
        )
    }

    /**
     * Asks the launcher to pin the configured widget and suspends until it is added, reporting
     * through [errors] and returning false when the widget cannot be requested at all.
     */
    @SuppressLint("NewApi") // The API 26 requirement is checked below before touching the pinning APIs.
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
        staticWidgetDao.getWidgetCountFlow()
            // We drop the first value since we only care about knowing when the widget is actually added
            .drop(1)
            .onStart {
                requestAccepted = appWidgetManager.requestPinAppWidget(
                    ComponentName(context, EntityWidget::class.java),
                    null,
                    PendingIntent.getBroadcast(
                        context,
                        System.currentTimeMillis().toInt(),
                        Intent(context, EntityWidget::class.java).apply {
                            action = ACTION_APPWIDGET_CREATED
                            putExtra(EXTRA_WIDGET_ENTITY, widget)
                        },
                        PendingIntent.FLAG_MUTABLE,
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

    @AssistedFactory
    interface Factory {
        fun create(widgetId: Int, preselectedEntityId: String?): EntityWidgetConfigureViewModel
    }
}

/**
 * Splits a comma separated list of attributes, dropping the blank entries.
 */
private fun String?.toAttributeIdsList(): List<String> =
    this?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
