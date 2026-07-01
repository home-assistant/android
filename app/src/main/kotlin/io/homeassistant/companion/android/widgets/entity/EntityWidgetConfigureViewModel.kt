package io.homeassistant.companion.android.widgets.entity

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.RemoteException
import androidx.compose.runtime.getValue
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
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class EntityWidgetTextColors(val white: String, val black: String)

internal enum class EntityWidgetTextColor {
    WHITE,
    BLACK,
}

internal enum class EntityWidgetConfigureError {
    CREATE,
    UPDATE,
}

internal data class EntityWidgetConfigureViewState(
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val selectedEntityId: String? = null,
    val appendAttributes: Boolean = false,
    val selectedAttributeIds: List<String> = emptyList(),
    val customAttribute: String = "",
    val label: String = "",
    val textSize: String = DEFAULT_TEXT_SIZE,
    val stateSeparator: String = "",
    val attributeSeparator: String = "",
    val selectedTapAction: WidgetTapAction = WidgetTapAction.REFRESH,
    val selectedBackgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    val selectedTextColor: EntityWidgetTextColor = EntityWidgetTextColor.WHITE,
    val isUpdateWidget: Boolean = false,
    val error: EntityWidgetConfigureError? = null,
) {
    val hasValidTextSize: Boolean
        get() = textSize.toFloatOrNull()?.let { it.isFinite() && it > 0 } == true

    val isActionEnabled: Boolean
        get() = selectedEntityId != null && hasValidTextSize
}

@HiltViewModel(assistedFactory = EntityWidgetConfigureViewModel.Factory::class)
class EntityWidgetConfigureViewModel @AssistedInject constructor(
    private val staticWidgetDao: StaticWidgetDao,
    private val serverManager: ServerManager,
    @Assisted preselectedEntityId: String?,
) : ViewModel() {

    private lateinit var textColors: EntityWidgetTextColors
    private var initialized = false

    internal var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
        private set

    val servers = serverManager.serversFlow

    internal var viewState by mutableStateOf(EntityWidgetConfigureViewState(selectedEntityId = preselectedEntityId))
        private set

    val selectedServerId: Int get() = viewState.selectedServerId
    val selectedEntityId: String? get() = viewState.selectedEntityId
    val appendAttributes: Boolean get() = viewState.appendAttributes
    val selectedAttributeIds: List<String> get() = viewState.selectedAttributeIds
    val label: String get() = viewState.label
    val textSize: String get() = viewState.textSize
    val stateSeparator: String get() = viewState.stateSeparator
    val attributeSeparator: String get() = viewState.attributeSeparator
    val selectedTapAction: WidgetTapAction get() = viewState.selectedTapAction
    val selectedBackgroundType: WidgetBackgroundType get() = viewState.selectedBackgroundType
    internal val selectedTextColor: EntityWidgetTextColor get() = viewState.selectedTextColor
    val isUpdateWidget: Boolean get() = viewState.isUpdateWidget

    @OptIn(ExperimentalCoroutinesApi::class)
    val entities: StateFlow<List<Entity>> = snapshotFlow { selectedServerId }
        .distinctUntilChanged()
        .mapLatest { serverId ->
            if (!serverManager.isRegistered()) {
                emptyList()
            } else {
                try {
                    serverManager.integrationRepository(serverId).getEntities().orEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to query entities")
                    emptyList()
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(500.milliseconds),
            initialValue = emptyList(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val entityRegistry: StateFlow<List<EntityRegistryResponse>?> = snapshotFlow { selectedServerId }
        .distinctUntilChanged()
        .mapLatest { serverId ->
            if (!serverManager.isRegistered()) {
                null
            } else {
                try {
                    serverManager.webSocketRepository(serverId).getEntityRegistry()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get entity registry")
                    null
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500.milliseconds), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val deviceRegistry: StateFlow<List<DeviceRegistryResponse>?> = snapshotFlow { selectedServerId }
        .distinctUntilChanged()
        .mapLatest { serverId ->
            if (!serverManager.isRegistered()) {
                null
            } else {
                try {
                    serverManager.webSocketRepository(serverId).getDeviceRegistry()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get device registry")
                    null
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500.milliseconds), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val areaRegistry: StateFlow<List<AreaRegistryResponse>?> = snapshotFlow { selectedServerId }
        .distinctUntilChanged()
        .mapLatest { serverId ->
            if (!serverManager.isRegistered()) {
                null
            } else {
                try {
                    serverManager.webSocketRepository(serverId).getAreaRegistry()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get area registry")
                    null
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(500.milliseconds), null)

    private var labelFromEntity = false

    internal fun onSetup(
        widgetId: Int,
        defaultBackgroundType: WidgetBackgroundType,
        textColors: EntityWidgetTextColors,
    ) {
        if (initialized) return
        initialized = true

        this.widgetId = widgetId
        this.textColors = textColors
        viewState = viewState.copy(selectedBackgroundType = defaultBackgroundType)

        initializeState(widgetId)
    }

    private fun initializeState(widgetId: Int) = viewModelScope.launch {
        val widget = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && selectedEntityId == null) {
            staticWidgetDao.get(widgetId)
        } else {
            null
        }

        if (widget != null) {
            viewState = viewState.copy(
                selectedServerId = widget.serverId,
                selectedEntityId = widget.entityId,
                appendAttributes = !widget.attributeIds.isNullOrBlank(),
                selectedAttributeIds = widget.attributeIds
                    ?.split(',')
                    ?.map(String::trim)
                    ?.filter(String::isNotEmpty)
                    .orEmpty(),
                label = widget.label.orEmpty(),
                textSize = widget.textSize.toInt().toString(),
                stateSeparator = widget.stateSeparator,
                attributeSeparator = widget.attributeSeparator,
                selectedTapAction = widget.tapAction,
                selectedBackgroundType = widget.backgroundType,
                selectedTextColor = if (widget.textColor == textColors.black) {
                    EntityWidgetTextColor.BLACK
                } else {
                    EntityWidgetTextColor.WHITE
                },
                isUpdateWidget = true,
            )
        } else {
            val serverId = serverManager.getServer()?.id ?: ServerManager.SERVER_ID_ACTIVE
            viewState = viewState.copy(selectedServerId = serverId)
        }
    }

    fun onServerSelected(serverId: Int) {
        if (serverId == selectedServerId) return

        viewState = viewState.copy(
            selectedServerId = serverId,
            selectedEntityId = null,
            selectedAttributeIds = emptyList(),
            selectedTapAction = WidgetTapAction.REFRESH,
        )
    }

    fun onEntitySelected(entityId: String?) {
        val domain = entityId?.substringBefore('.')
        viewState = viewState.copy(
            selectedEntityId = entityId,
            selectedAttributeIds = emptyList(),
            selectedTapAction = if (domain in EntityExt.APP_PRESS_ACTION_DOMAINS) {
                WidgetTapAction.TOGGLE
            } else {
                WidgetTapAction.REFRESH
            },
        )

        if (label.isBlank() || labelFromEntity) {
            updateLabelFromEntity(null)
        }
    }

    internal fun onSelectedEntityLoaded(entity: Entity?) {
        if (entity == null || entity.entityId != selectedEntityId) return

        val friendlyName = entity.friendlyName.takeIf { it != entity.entityId }.orEmpty()
        if (label == friendlyName) {
            labelFromEntity = friendlyName.isNotEmpty()
        } else if (label.isBlank() || labelFromEntity) {
            updateLabelFromEntity(entity)
        }
    }

    fun onAppendAttributesChanged(append: Boolean) {
        viewState = viewState.copy(appendAttributes = append)
    }

    fun onAttributeAdded(attributeId: String) {
        if (attributeId !in selectedAttributeIds) {
            viewState = viewState.copy(selectedAttributeIds = selectedAttributeIds + attributeId)
        }
    }

    fun onAttributeRemoved(attributeId: String) {
        viewState = viewState.copy(selectedAttributeIds = selectedAttributeIds - attributeId)
    }

    fun onCustomAttributeChanged(value: String) {
        viewState = viewState.copy(customAttribute = value)
    }

    fun onCustomAttributesAdded() {
        val attributes = viewState.customAttribute
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)

        if (attributes.isEmpty()) return

        viewState = viewState.copy(
            selectedAttributeIds = selectedAttributeIds + attributes.filterNot(selectedAttributeIds::contains),
            customAttribute = "",
        )
    }

    fun onLabelChanged(value: String) {
        viewState = viewState.copy(label = value)
        labelFromEntity = false
    }

    fun onTextSizeChanged(value: String) {
        viewState = viewState.copy(textSize = value.filter(Char::isDigit))
    }

    fun onStateSeparatorChanged(value: String) {
        viewState = viewState.copy(stateSeparator = value)
    }

    fun onAttributeSeparatorChanged(value: String) {
        viewState = viewState.copy(attributeSeparator = value)
    }

    fun onTapActionSelected(action: WidgetTapAction) {
        viewState = viewState.copy(selectedTapAction = action)
    }

    fun onBackgroundTypeSelected(backgroundType: WidgetBackgroundType) {
        viewState = viewState.copy(selectedBackgroundType = backgroundType)
    }

    internal fun onTextColorSelected(textColor: EntityWidgetTextColor) {
        viewState = viewState.copy(selectedTextColor = textColor)
    }

    internal fun onActionError(error: EntityWidgetConfigureError) {
        viewState = viewState.copy(error = error)
    }

    internal fun onErrorShown() {
        viewState = viewState.copy(error = null)
    }

    private suspend fun selectedEntity(): Entity? = withContext(Dispatchers.Default) {
        entities.value.firstOrNull { it.entityId == selectedEntityId }
    }

    suspend fun isValidSelection(): Boolean {
        return viewState.isActionEnabled &&
            serverManager.getServer(selectedServerId) != null &&
            selectedEntity() != null
    }

    suspend fun updateWidgetConfiguration() {
        check(widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) { "Widget ID is invalid" }
        staticWidgetDao.add(getPendingDaoEntity())
    }

    internal suspend fun getPendingDaoEntity(): StaticWidgetEntity {
        check(isValidSelection()) { "Widget data is invalid" }
        val entity = checkNotNull(selectedEntity()) { "Selected entity is unknown on server" }

        return StaticWidgetEntity(
            id = widgetId,
            serverId = selectedServerId,
            entityId = entity.entityId,
            attributeIds = selectedAttributeIds.takeIf { appendAttributes && it.isNotEmpty() }?.joinToString(","),
            label = label,
            textSize = textSize.toFloatOrNull() ?: DEFAULT_TEXT_SIZE.toFloat(),
            stateSeparator = stateSeparator,
            attributeSeparator = attributeSeparator.takeIf { appendAttributes }.orEmpty(),
            tapAction = if (entity.domain in EntityExt.APP_PRESS_ACTION_DOMAINS) {
                selectedTapAction
            } else {
                WidgetTapAction.REFRESH
            },
            lastUpdate = staticWidgetDao.get(widgetId)?.lastUpdate.orEmpty(),
            backgroundType = selectedBackgroundType,
            textColor = if (selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
                when (selectedTextColor) {
                    EntityWidgetTextColor.WHITE -> textColors.white
                    EntityWidgetTextColor.BLACK -> textColors.black
                }
            } else {
                null
            },
        )
    }

    @SuppressLint("NewApi") // The activity calls this only after its API 26 runtime check.
    suspend fun requestWidgetCreation(context: Context) {
        check(SdkVersion.isAtLeast(Build.VERSION_CODES.O)) { "Widget pinning is not supported" }

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val pinningSupported = try {
            appWidgetManager.isRequestPinAppWidgetSupported
        } catch (e: RemoteException) {
            Timber.e(e, "Unable to read isRequestPinAppWidgetSupported")
            false
        }
        check(pinningSupported) { "Widget pinning is not supported" }

        staticWidgetDao.getWidgetCountFlow().drop(1).onStart {
            val requestAccepted = appWidgetManager.requestPinAppWidget(
                ComponentName(context, EntityWidget::class.java),
                null,
                PendingIntent.getBroadcast(
                    context,
                    System.currentTimeMillis().toInt(),
                    Intent(context, EntityWidget::class.java).apply {
                        action = ACTION_APPWIDGET_CREATED
                        putExtra(EXTRA_WIDGET_ENTITY, getPendingDaoEntity())
                    },
                    PendingIntent.FLAG_MUTABLE,
                ),
            )
            check(requestAccepted) { "Widget pin request was rejected" }
        }.first()
    }

    fun updateWidget(context: Context) {
        context.sendBroadcast(
            Intent(context, EntityWidget::class.java).apply {
                action = BaseWidgetProvider.UPDATE_WIDGETS
            },
        )
    }

    private fun updateLabelFromEntity(entity: Entity?) {
        val friendlyName = entity?.friendlyName?.takeIf { it != entity.entityId }.orEmpty()
        viewState = viewState.copy(label = friendlyName)
        labelFromEntity = friendlyName.isNotEmpty()
    }

    @AssistedFactory
    interface Factory {
        fun create(preselectedEntityId: String?): EntityWidgetConfigureViewModel
    }
}

private const val DEFAULT_TEXT_SIZE = "30"
