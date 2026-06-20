package io.homeassistant.companion.android.widgets.mediaplayer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.color.DynamicColors
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Stable request code for the widget-creation broadcast [PendingIntent]. A fixed value (combined with
 * [PendingIntent.FLAG_UPDATE_CURRENT]) keeps the pending intent deterministic and testable while making
 * sure a reconfigured request replaces the previously registered extras.
 */
private const val PIN_WIDGET_REQUEST_CODE = 0

/**
 * The values the user can edit on the Media Player Controls widget configuration screen.
 */
@Stable
internal data class MediaPlayerControlsWidgetConfigureViewState(
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val selectedEntityIds: List<String> = emptyList(),
    val label: String = "",
    val showVolume: Boolean = true,
    val showSkip: Boolean = true,
    val showSeek: Boolean = true,
    val showSource: Boolean = true,
    val backgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    val isUpdateWidget: Boolean = false,
)

/**
 * Complete UI state for the Media Player Controls widget configuration screen.
 *
 * Bundles the user-editable [config] together with the server-dependent data the screen renders
 * (available servers, media-player entities and registries) and whether the current selection can be
 * saved ([isInputValid]), so the screen only collects a single state.
 */
@Stable
internal data class MediaPlayerControlsWidgetConfigureUiState(
    val config: MediaPlayerControlsWidgetConfigureViewState = MediaPlayerControlsWidgetConfigureViewState(),
    val servers: List<Server> = emptyList(),
    val availableEntities: List<Entity> = emptyList(),
    val entityRegistry: List<EntityRegistryResponse>? = null,
    val deviceRegistry: List<DeviceRegistryResponse>? = null,
    val areaRegistry: List<AreaRegistryResponse>? = null,
    val isInputValid: Boolean = false,
    /** One-shot message to surface as a Snackbar, then cleared via [onUserMessageShown]. */
    @StringRes val userMessage: Int? = null,
)

/** Server-dependent data combined into [MediaPlayerControlsWidgetConfigureUiState]. */
private data class ServerData(
    val servers: List<Server>,
    val availableEntities: List<Entity>,
    val entityRegistry: List<EntityRegistryResponse>?,
    val deviceRegistry: List<DeviceRegistryResponse>?,
    val areaRegistry: List<AreaRegistryResponse>?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MediaPlayerControlsWidgetConfigureViewModel.Factory::class)
class MediaPlayerControlsWidgetConfigureViewModel @AssistedInject constructor(
    private val mediaPlayerControlsWidgetDao: MediaPlayerControlsWidgetDao,
    private val serverManager: ServerManager,
    @Assisted preselectedEntityId: String?,
) : ViewModel() {

    private var initialized = false

    var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
        private set

    private val editState = MutableStateFlow(
        MediaPlayerControlsWidgetConfigureViewState(selectedEntityIds = listOfNotNull(preselectedEntityId)),
    )

    /** One-shot user-facing message (string resource) folded into [uiState] and cleared via [onUserMessageShown]. */
    private val userMessage = MutableStateFlow<Int?>(null)

    private val servers = serverManager.serversFlow

    private val selectedServerIdFlow = editState
        .map { it.selectedServerId }
        .distinctUntilChanged()

    private val entities: StateFlow<List<Entity>> = selectedServerIdFlow
        .mapLatest { serverId -> loadMediaPlayerEntities(serverId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500.milliseconds), emptyList())

    private val entityRegistry: StateFlow<List<EntityRegistryResponse>?> =
        registryFlow { serverManager.webSocketRepository(it).getEntityRegistry() }

    private val deviceRegistry: StateFlow<List<DeviceRegistryResponse>?> =
        registryFlow { serverManager.webSocketRepository(it).getDeviceRegistry() }

    private val areaRegistry: StateFlow<List<AreaRegistryResponse>?> =
        registryFlow { serverManager.webSocketRepository(it).getAreaRegistry() }

    private val serverData: Flow<ServerData> = combine(
        servers,
        entities,
        entityRegistry,
        deviceRegistry,
        areaRegistry,
    ) { serverList, availableEntities, entityReg, deviceReg, areaReg ->
        ServerData(serverList, availableEntities, entityReg, deviceReg, areaReg)
    }

    /**
     * Single source of truth the screen collects: the user-editable [MediaPlayerControlsWidgetConfigureViewState]
     * combined with the server-dependent data (servers, entities, registries) and whether the current
     * selection can be saved. Started eagerly so the activity and tests can read [StateFlow.value] directly.
     *
     * [MediaPlayerControlsWidgetConfigureUiState.isInputValid] is `true` when at least one selected entity
     * exists in the media-player entities loaded for the selected server (which only happens for a valid,
     * registered server); it drives the enabled state of the confirm button.
     */
    internal val uiState: StateFlow<MediaPlayerControlsWidgetConfigureUiState> = combine(
        editState,
        serverData,
        userMessage,
    ) { config, data, message ->
        MediaPlayerControlsWidgetConfigureUiState(
            config = config,
            servers = data.servers,
            availableEntities = data.availableEntities,
            entityRegistry = data.entityRegistry,
            deviceRegistry = data.deviceRegistry,
            areaRegistry = data.areaRegistry,
            isInputValid = config.selectedEntityIds.any { id -> data.availableEntities.any { it.entityId == id } },
            userMessage = message,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MediaPlayerControlsWidgetConfigureUiState())

    /**
     * Initializes the screen for the given [widgetId]. Restores the persisted configuration when the
     * widget already exists. Safe to call multiple times: only the first call has an effect, so a
     * configuration change does not discard the current selection.
     */
    fun onSetup(widgetId: Int) {
        if (initialized) return
        initialized = true
        this.widgetId = widgetId
        loadInitialConfiguration(widgetId)
    }

    private fun loadInitialConfiguration(widgetId: Int) = viewModelScope.launch {
        val existingWidget = if (
            widgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
            editState.value.selectedEntityIds.isEmpty()
        ) {
            mediaPlayerControlsWidgetDao.get(widgetId)
        } else {
            null
        }

        if (existingWidget != null) {
            editState.update {
                it.copy(
                    isUpdateWidget = true,
                    selectedServerId = existingWidget.serverId,
                    // Widgets store one or several comma-separated entities; restore all of them
                    // (de-duplicated) so the multi-player "show whichever is currently playing"
                    // behaviour is preserved without rendering or re-saving duplicate rows.
                    selectedEntityIds = existingWidget.entityId
                        .split(",")
                        .map { id -> id.trim() }
                        .filter { id -> id.isNotBlank() }
                        .distinct(),
                    label = existingWidget.label.orEmpty(),
                    showVolume = existingWidget.showVolume,
                    showSkip = existingWidget.showSkip,
                    showSeek = existingWidget.showSeek,
                    showSource = existingWidget.showSource,
                    backgroundType = existingWidget.backgroundType,
                )
            }
        } else {
            editState.update {
                it.copy(
                    selectedServerId = serverManager.getServer()?.id ?: ServerManager.SERVER_ID_ACTIVE,
                    backgroundType = defaultBackgroundType(),
                )
            }
        }
    }

    /** New widgets default to dynamic color when the device supports it, else day/night. */
    private fun defaultBackgroundType(): WidgetBackgroundType = if (DynamicColors.isDynamicColorAvailable()) {
        WidgetBackgroundType.DYNAMICCOLOR
    } else {
        WidgetBackgroundType.DAYNIGHT
    }

    fun onServerSelected(serverId: Int) {
        if (serverId == editState.value.selectedServerId) return
        editState.update { it.copy(selectedServerId = serverId, selectedEntityIds = emptyList()) }
    }

    fun onEntityAdded(entityId: String) {
        if (entityId.isBlank()) return
        editState.update { state ->
            if (entityId in state.selectedEntityIds) {
                state
            } else {
                state.copy(selectedEntityIds = state.selectedEntityIds + entityId)
            }
        }
    }

    fun onEntityRemoved(entityId: String) {
        editState.update { it.copy(selectedEntityIds = it.selectedEntityIds - entityId) }
    }

    fun onLabelChanged(label: String) {
        editState.update { it.copy(label = label) }
    }

    fun onShowVolumeChanged(show: Boolean) {
        editState.update { it.copy(showVolume = show) }
    }

    fun onShowSkipChanged(show: Boolean) {
        editState.update { it.copy(showSkip = show) }
    }

    fun onShowSeekChanged(show: Boolean) {
        editState.update { it.copy(showSeek = show) }
    }

    fun onShowSourceChanged(show: Boolean) {
        editState.update { it.copy(showSource = show) }
    }

    fun onBackgroundTypeSelected(backgroundType: WidgetBackgroundType) {
        editState.update { it.copy(backgroundType = backgroundType) }
    }

    fun onUserMessage(@StringRes messageResId: Int) {
        userMessage.value = messageResId
    }

    /** Clears the current [MediaPlayerControlsWidgetConfigureUiState.userMessage] once the screen has shown it. */
    fun onUserMessageShown() {
        userMessage.value = null
    }

    suspend fun isValidSelection(): Boolean {
        val state = editState.value
        val availableEntityIds = entities.value.map { it.entityId }
        return serverManager.getServer(state.selectedServerId) != null &&
            state.selectedEntityIds.any { it in availableEntityIds }
    }

    /**
     * Persists the current configuration for an existing widget.
     *
     * @throws IllegalStateException when the widget id or the current selection is invalid.
     */
    suspend fun updateWidgetConfiguration() {
        check(widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) { "Widget ID is invalid" }
        check(isValidSelection()) { "Widget data is invalid" }
        mediaPlayerControlsWidgetDao.add(getPendingDaoEntity())
    }

    private fun getPendingDaoEntity(): MediaPlayerControlsWidgetEntity {
        val state = editState.value
        val entityId = state.selectedEntityIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(",")
        check(entityId.isNotBlank()) { "No entity selected" }
        return MediaPlayerControlsWidgetEntity(
            id = widgetId,
            serverId = state.selectedServerId,
            entityId = entityId,
            label = state.label,
            showSkip = state.showSkip,
            showSeek = state.showSeek,
            showVolume = state.showVolume,
            showSource = state.showSource,
            backgroundType = state.backgroundType,
        )
    }

    /**
     * Requests the widget to be pinned and waits until it has been saved to the DAO.
     *
     * **WARNING**: This function does not handle user cancellation. If a user cancels the widget creation,
     * this function will not return. If this function is called again and the user does not cancel,
     * both calls to the function will return. While this behavior could be avoided,
     * it does not cause issues in the current implementation as returning multiple times has no adverse effects.
     *
     * @throws IllegalStateException when widget pinning is not supported or the request is rejected.
     */
    @SuppressLint("NewApi") // The caller guards this with an API 26 runtime check before invoking it.
    suspend fun requestWidgetCreation(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        check(appWidgetManager.isRequestPinAppWidgetSupported) { "Widget pinning is not supported" }

        // We drop the first value since we only care about knowing when the widget is actually added.
        mediaPlayerControlsWidgetDao.getWidgetCountFlow().drop(1).onStart {
            val requestAccepted = appWidgetManager.requestPinAppWidget(
                ComponentName(context, MediaPlayerControlsWidget::class.java),
                null,
                PendingIntent.getBroadcast(
                    context,
                    PIN_WIDGET_REQUEST_CODE,
                    Intent(context, MediaPlayerControlsWidget::class.java).apply {
                        action = ACTION_APPWIDGET_CREATED
                        putExtra(EXTRA_WIDGET_ENTITY, getPendingDaoEntity())
                    },
                    // FLAG_MUTABLE: the system injects the created EXTRA_APPWIDGET_ID.
                    // FLAG_UPDATE_CURRENT: refresh the extras when the screen is reconfigured and re-requested.
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            check(requestAccepted) { "Widget pin request was rejected" }
        }.first()
    }

    fun updateWidget(context: Context) {
        context.sendBroadcast(
            Intent(context, MediaPlayerControlsWidget::class.java).apply {
                action = BaseWidgetProvider.UPDATE_WIDGETS
            },
        )
    }

    private suspend fun loadMediaPlayerEntities(serverId: Int): List<Entity> {
        if (!serverManager.isRegistered()) {
            Timber.w("No server registered")
            return emptyList()
        }
        return try {
            serverManager.integrationRepository(serverId)
                .getEntities()
                .orEmpty()
                .filter { it.domain == MEDIA_PLAYER_DOMAIN }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to get media player entities")
            userMessage.value = commonR.string.widget_entity_fetch_error
            emptyList()
        }
    }

    private fun <T> registryFlow(loader: suspend (serverId: Int) -> T?): StateFlow<T?> = selectedServerIdFlow
        .mapLatest { serverId ->
            if (!serverManager.isRegistered()) {
                null
            } else {
                try {
                    loader(serverId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get registry")
                    null
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500.milliseconds), null)

    @AssistedFactory
    interface Factory {
        fun create(preselectedEntityId: String?): MediaPlayerControlsWidgetConfigureViewModel
    }
}
