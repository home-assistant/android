package io.homeassistant.companion.android.widgets.mediaplayer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import io.homeassistant.companion.android.widgets.configure.BaseEntityWidgetConfigureViewModel
import timber.log.Timber

/**
 * Stable request code for the widget-creation broadcast [PendingIntent]. A fixed value (combined with
 * [PendingIntent.FLAG_UPDATE_CURRENT]) keeps the pending intent deterministic and testable while making
 * sure a reconfigured request replaces the previously registered extras.
 */
private const val PIN_WIDGET_REQUEST_CODE = 0

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

    /**
     * Uses a fixed request code and [PendingIntent.FLAG_UPDATE_CURRENT] so a reconfigured request
     * replaces the previously registered extras (see [PIN_WIDGET_REQUEST_CODE]).
     */
    @SuppressLint("NewApi") // Guarded by requestWidgetCreation before this is reached.
    override suspend fun requestPin(context: Context, widget: MediaPlayerControlsWidgetEntity): Boolean {
        return AppWidgetManager.getInstance(context).requestPinAppWidget(
            ComponentName(context, MediaPlayerControlsWidget::class.java),
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
    }

    @AssistedFactory
    interface Factory {
        fun create(widgetId: Int, preselectedEntityId: String?): MediaPlayerControlsWidgetConfigureViewModel
    }
}
