package io.homeassistant.companion.android.widgets.entity

import android.content.Context
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayWithContext
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.widgets.configure.BaseEntityWidgetConfigureViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel(assistedFactory = EntityWidgetConfigureViewModel.Factory::class)
internal class EntityWidgetConfigureViewModel @AssistedInject constructor(
    staticWidgetDao: StaticWidgetDao,
    serverManager: ServerManager,
    entitiesForDisplayManager: EntitiesForDisplayManager,
    @Assisted widgetId: Int,
    @Assisted preselectedEntityId: String?,
) : BaseEntityWidgetConfigureViewModel<EntityWidgetConfigureState, StaticWidgetEntity>(
    serverManager = serverManager,
    widgetId = widgetId,
    dao = staticWidgetDao,
    entitiesForDisplayManager = entitiesForDisplayManager,
    initialState = EntityWidgetConfigureState(
        selectedEntityId = preselectedEntityId,
    ),
) {

    private var labelFromEntity = false
    private var loadAttributesJob: Job? = null

    fun onEntitySelected(entityId: String?) {
        updateState { current ->
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
        updateState {
            if (attributeId in it.selectedAttributeIds) {
                it
            } else {
                it.copy(selectedAttributeIds = it.selectedAttributeIds + attributeId)
            }
        }
    }

    fun onAttributeRemoved(attributeId: String) {
        updateState { it.copy(selectedAttributeIds = it.selectedAttributeIds - attributeId) }
    }

    fun onCustomAttributeChanged(value: String) {
        updateState { it.copy(customAttribute = value) }
    }

    fun onCustomAttributesAdded() {
        updateState { current ->
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
        updateState { it.copy(label = value) }
        labelFromEntity = false
    }

    fun onTextSizeChanged(value: String) {
        updateState { it.copy(textSize = value.filter(Char::isDigit)) }
    }

    fun onStateSeparatorChanged(value: String) {
        updateState { it.copy(stateSeparator = value) }
    }

    fun onAttributeSeparatorChanged(value: String) {
        updateState { it.copy(attributeSeparator = value) }
    }

    fun onTapActionSelected(action: WidgetTapAction) {
        updateState { it.copy(selectedTapAction = action) }
    }

    fun onTextColorSelected(colorHex: String) {
        updateState { it.copy(textColorHex = colorHex) }
    }

    override val widgetProviderClass: Class<*> = EntityWidget::class.java

    override fun reload() {
        super.reload()
        loadAttributes(currentState().selectedEntityId)
    }

    override fun withEntityDisplayState(
        state: EntityWidgetConfigureState,
        displayState: EntityDisplayState<EntityDisplayWithContext>,
    ) = state.copy(entityDisplayState = displayState)

    /**
     * The selection can be set before the entities resolve (preselected entity or restored widget),
     * so the generated label is refreshed once they are available.
     */
    override fun onEntitiesLoaded(displayState: EntityDisplayState<EntityDisplayWithContext>) {
        syncLabelWithSelectedEntity()
    }

    override fun restoreState(state: EntityWidgetConfigureState, widget: StaticWidgetEntity) = state.copy(
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

    /**
     * Loads the attributes the selected entity exposes, offered as suggestions when the user
     * appends attributes to the widget.
     */
    private fun loadAttributes(entityId: String?) {
        loadAttributesJob?.cancel()
        loadAttributesJob = viewModelScope.launch {
            val attributes = entityId?.let { readAttributes(it) }.orEmpty()
            updateState { it.copy(availableAttributes = attributes) }
        }
    }

    private suspend fun readAttributes(entityId: String): List<String> = try {
        serverManager.integrationRepository(currentState().selectedServerId).getEntity(entityId)
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
        val current = currentState()
        val entity = current.selectedEntity ?: return
        val name = entity.name.takeIf { it != entity.entityId }.orEmpty()

        if (current.label == name) {
            labelFromEntity = name.isNotEmpty()
        } else if (current.label.isBlank() || labelFromEntity) {
            updateState { it.copy(label = name) }
            labelFromEntity = name.isNotEmpty()
        }
    }

    override suspend fun buildPendingEntity(context: Context): StaticWidgetEntity? {
        val current = currentState()
        // The entity must still exist on a known server, and the configuration must be complete.
        val entity = current.selectedEntity?.takeIf {
            current.isActionEnabled && serverManager.getServer(current.selectedServerId) != null
        }
        if (entity == null) {
            Timber.e("Cannot build the widget, the current configuration is invalid")
        }

        return entity?.let {
            StaticWidgetEntity(
                id = widgetId,
                serverId = current.selectedServerId,
                entityId = it.entityId,
                attributeIds = current.selectedAttributeIds.takeIf { ids -> ids.isNotEmpty() }?.joinToString(","),
                label = current.label,
                textSize = current.textSizeOrDefault,
                stateSeparator = current.stateSeparator,
                attributeSeparator = current.attributeSeparator
                    .takeIf { current.selectedAttributeIds.isNotEmpty() }
                    .orEmpty(),
                tapAction = if (current.isToggleable) current.selectedTapAction else WidgetTapAction.REFRESH,
                lastUpdate = dao.get(widgetId)?.lastUpdate.orEmpty(),
                backgroundType = current.selectedBackgroundType,
                textColor = current.textColorHex.takeIf {
                    current.selectedBackgroundType == WidgetBackgroundType.TRANSPARENT
                },
            )
        }
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
