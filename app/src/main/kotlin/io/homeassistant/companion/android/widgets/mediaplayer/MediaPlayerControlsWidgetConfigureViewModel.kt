package io.homeassistant.companion.android.widgets.mediaplayer

import android.content.Context
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.widgets.configure.BaseEntityWidgetConfigureViewModel
import timber.log.Timber

@HiltViewModel(assistedFactory = MediaPlayerControlsWidgetConfigureViewModel.Factory::class)
internal class MediaPlayerControlsWidgetConfigureViewModel @AssistedInject constructor(
    mediaPlayerControlsWidgetDao: MediaPlayerControlsWidgetDao,
    serverManager: ServerManager,
    entitiesForDisplayManager: EntitiesForDisplayManager,
    @Assisted widgetId: Int,
    @Assisted preselectedEntityId: String?,
) : BaseEntityWidgetConfigureViewModel<MediaPlayerControlsWidgetConfigureState, MediaPlayerControlsWidgetEntity>(
    serverManager = serverManager,
    widgetId = widgetId,
    dao = mediaPlayerControlsWidgetDao,
    entitiesForDisplayManager = entitiesForDisplayManager,
    initialState = MediaPlayerControlsWidgetConfigureState(
        selectedEntityIds = preselectedEntityId?.let { listOf(it) } ?: emptyList(),
    ),
    entityFilter = { it.domain == MEDIA_PLAYER_DOMAIN },
) {

    fun onEntityAdded(entityId: String) {
        updateState { current ->
            if (entityId in current.selectedEntityIds) {
                current
            } else {
                current.copy(selectedEntityIds = current.selectedEntityIds + entityId)
            }
        }
    }

    fun onEntityRemoved(entityId: String) {
        updateState { it.copy(selectedEntityIds = it.selectedEntityIds - entityId) }
    }

    fun onLabelChanged(label: String) {
        updateState { it.copy(label = label) }
    }

    fun onShowVolumeChanged(show: Boolean) {
        updateState { it.copy(showVolume = show) }
    }

    fun onShowSkipChanged(show: Boolean) {
        updateState { it.copy(showSkip = show) }
    }

    fun onShowSeekChanged(show: Boolean) {
        updateState { it.copy(showSeek = show) }
    }

    fun onShowSourceChanged(show: Boolean) {
        updateState { it.copy(showSource = show) }
    }

    override val widgetProviderClass: Class<*> = MediaPlayerControlsWidget::class.java

    override fun withEntityDisplayState(
        state: MediaPlayerControlsWidgetConfigureState,
        displayState: EntityDisplayState<EntityDisplayWithContext>,
    ) = state.copy(entityDisplayState = displayState)

    override fun restoreState(
        state: MediaPlayerControlsWidgetConfigureState,
        widget: MediaPlayerControlsWidgetEntity,
    ) = state.copy(
        selectedServerId = widget.serverId,
        // Widgets store one or several comma-separated entities; restore all of them
        // (de-duplicated) so the multi-player "show whichever is currently playing"
        // behaviour is preserved without rendering or re-saving duplicate rows.
        selectedEntityIds = widget.entityId
            .split(",")
            .map { id -> id.trim() }
            .filter { id -> id.isNotBlank() }
            .distinct(),
        label = widget.label.orEmpty(),
        showVolume = widget.showVolume,
        showSkip = widget.showSkip,
        showSeek = widget.showSeek,
        showSource = widget.showSource,
        selectedBackgroundType = widget.backgroundType,
        isUpdateWidget = true,
    )

    override suspend fun buildPendingEntity(context: Context): MediaPlayerControlsWidgetEntity? {
        val current = currentState()
        // Persist only the entities resolved on the server (so ids that no longer exist cannot end up
        // in the widget), and only when the configuration is complete on a known server.
        val entityId = current.selectedEntities
            .takeIf { current.isActionEnabled && serverManager.getServer(current.selectedServerId) != null }
            ?.joinToString(",") { it.entityId }
            ?.takeIf { it.isNotEmpty() }
        if (entityId == null) {
            Timber.e("Cannot build the widget, the current configuration is invalid")
        }

        return entityId?.let {
            MediaPlayerControlsWidgetEntity(
                id = widgetId,
                serverId = current.selectedServerId,
                entityId = it,
                label = current.label,
                showSkip = current.showSkip,
                showSeek = current.showSeek,
                showVolume = current.showVolume,
                showSource = current.showSource,
                backgroundType = current.selectedBackgroundType,
            )
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(widgetId: Int, preselectedEntityId: String?): MediaPlayerControlsWidgetConfigureViewModel
    }
}
