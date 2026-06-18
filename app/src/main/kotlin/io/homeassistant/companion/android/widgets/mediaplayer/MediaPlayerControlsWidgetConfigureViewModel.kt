package io.homeassistant.companion.android.widgets.mediaplayer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Immutable UI state for the Media Player Controls widget configuration screen.
 *
 * Only holds the values the user can edit. Server-dependent data (the list of servers, entities and
 * registries) is exposed as separate flows on [MediaPlayerControlsWidgetConfigureViewModel] because
 * it is derived rather than directly edited.
 */
internal data class MediaPlayerControlsWidgetConfigureViewState(
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val selectedEntityId: String? = null,
    val label: String = "",
    val showVolume: Boolean = true,
    val showSkip: Boolean = true,
    val showSeek: Boolean = true,
    val showSource: Boolean = true,
    val backgroundType: WidgetBackgroundType = WidgetBackgroundType.DAYNIGHT,
    val isUpdateWidget: Boolean = false,
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

    private val _viewState = MutableStateFlow(
        MediaPlayerControlsWidgetConfigureViewState(selectedEntityId = preselectedEntityId),
    )
    internal val viewState: StateFlow<MediaPlayerControlsWidgetConfigureViewState> = _viewState.asStateFlow()

    /** One-shot user-facing messages (string resources) surfaced by the screen as a Snackbar. */
    private val userMessageChannel = Channel<Int>(Channel.BUFFERED)
    val userMessages: Flow<Int> = userMessageChannel.receiveAsFlow()

    val servers = serverManager.serversFlow

    private val selectedServerIdFlow = _viewState
        .map { it.selectedServerId }
        .distinctUntilChanged()

    val entities: StateFlow<List<Entity>> = selectedServerIdFlow
        .mapLatest { serverId -> loadMediaPlayerEntities(serverId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500.milliseconds), emptyList())

    val entityRegistry: StateFlow<List<EntityRegistryResponse>?> =
        registryFlow { serverManager.webSocketRepository(it).getEntityRegistry() }

    val deviceRegistry: StateFlow<List<DeviceRegistryResponse>?> =
        registryFlow { serverManager.webSocketRepository(it).getDeviceRegistry() }

    val areaRegistry: StateFlow<List<AreaRegistryResponse>?> =
        registryFlow { serverManager.webSocketRepository(it).getAreaRegistry() }

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
            _viewState.value.selectedEntityId == null
        ) {
            mediaPlayerControlsWidgetDao.get(widgetId)
        } else {
            null
        }

        if (existingWidget != null) {
            _viewState.update {
                it.copy(
                    isUpdateWidget = true,
                    selectedServerId = existingWidget.serverId,
                    // Legacy widgets could store several comma-separated entities; the picker
                    // selects a single one, so we restore the first configured entity.
                    selectedEntityId = existingWidget.entityId
                        .split(",")
                        .firstOrNull { id -> id.isNotBlank() }
                        ?.trim(),
                    label = existingWidget.label.orEmpty(),
                    showVolume = existingWidget.showVolume,
                    showSkip = existingWidget.showSkip,
                    showSeek = existingWidget.showSeek,
                    showSource = existingWidget.showSource,
                    backgroundType = existingWidget.backgroundType,
                )
            }
        } else {
            _viewState.update {
                it.copy(selectedServerId = serverManager.getServer()?.id ?: ServerManager.SERVER_ID_ACTIVE)
            }
        }
    }

    fun onServerSelected(serverId: Int) {
        if (serverId == _viewState.value.selectedServerId) return
        _viewState.update { it.copy(selectedServerId = serverId, selectedEntityId = null) }
    }

    fun onEntitySelected(entityId: String?) {
        _viewState.update { it.copy(selectedEntityId = entityId) }
    }

    fun onLabelChanged(label: String) {
        _viewState.update { it.copy(label = label) }
    }

    fun onShowVolumeChanged(show: Boolean) {
        _viewState.update { it.copy(showVolume = show) }
    }

    fun onShowSkipChanged(show: Boolean) {
        _viewState.update { it.copy(showSkip = show) }
    }

    fun onShowSeekChanged(show: Boolean) {
        _viewState.update { it.copy(showSeek = show) }
    }

    fun onShowSourceChanged(show: Boolean) {
        _viewState.update { it.copy(showSource = show) }
    }

    fun onBackgroundTypeSelected(backgroundType: WidgetBackgroundType) {
        _viewState.update { it.copy(backgroundType = backgroundType) }
    }

    fun onUserMessage(@StringRes messageResId: Int) {
        userMessageChannel.trySend(messageResId)
    }

    suspend fun isValidSelection(): Boolean {
        val state = _viewState.value
        return serverManager.getServer(state.selectedServerId) != null &&
            state.selectedEntityId in entities.value.map { it.entityId }
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
        val state = _viewState.value
        val entityId = checkNotNull(state.selectedEntityId?.takeIf { it.isNotBlank() }) { "No entity selected" }
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
                    System.currentTimeMillis().toInt(),
                    Intent(context, MediaPlayerControlsWidget::class.java).apply {
                        action = ACTION_APPWIDGET_CREATED
                        putExtra(EXTRA_WIDGET_ENTITY, getPendingDaoEntity())
                    },
                    // The PendingIntent must be mutable so the system can inject the created EXTRA_APPWIDGET_ID.
                    PendingIntent.FLAG_MUTABLE,
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
