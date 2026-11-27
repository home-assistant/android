package io.homeassistant.companion.android.widgets.button

import android.content.Context
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.generated.Flash
import io.homeassistant.companion.android.common.data.integration.Action
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.fromHaName
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.common.util.mdiName
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.common.ActionFieldBinder
import io.homeassistant.companion.android.widgets.configure.BaseWidgetConfigureViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel(assistedFactory = ButtonWidgetViewModel.Factory::class)
internal class ButtonWidgetViewModel @AssistedInject constructor(
    @Assisted widgetId: Int,
    @Assisted val preselectedEntityId: String?,
    serverManager: ServerManager,
    buttonWidgetDao: ButtonWidgetDao,
) : BaseWidgetConfigureViewModel<ButtonWidgetConfigureState, ButtonWidgetEntity>(
    serverManager = serverManager,
    widgetId = widgetId,
    dao = buttonWidgetDao,
    initialState = ButtonWidgetConfigureState(),
) {

    @AssistedFactory
    interface Factory {
        fun create(widgetId: Int, preselectedEntityId: String?): ButtonWidgetViewModel
    }

    override val widgetProviderClass: Class<*> = ButtonWidget::class.java

    private val actions = mutableMapOf<Int, MutableMap<String, Action>>()
    private val entities = mutableMapOf<Int, MutableMap<String, Entity>>()
    private var ongoingJob: Job? = null

    override fun restoreState(
        state: ButtonWidgetConfigureState,
        widget: ButtonWidgetEntity,
    ): ButtonWidgetConfigureState {
        val icon = Mdi.fromHaName(widget.iconName) ?: Mdi.Flash
        return state.copy(
            selectedServerId = widget.serverId,
            actionText = "${widget.domain}.${widget.service}",
            label = widget.label.orEmpty(),
            selectedIcon = icon,
            selectedIconId = icon.mdiName,
            selectedBackgroundType = widget.backgroundType,
            textColorHex = widget.textColor,
            requiresAuthentication = widget.requireAuthentication,
            isUpdateWidget = true,
        )
    }

    override fun reload() {
        val selectedServerId = currentState().selectedServerId
        viewModelScope.launch {
            try {
                val serverActionMap = mutableMapOf<String, Action>()
                actions[selectedServerId] = serverActionMap
                serverManager.integrationRepository(selectedServerId).getServices()?.forEach {
                    serverActionMap["${it.domain}.${it.action}"] = it
                }
                val serverEntityMap = mutableMapOf<String, Entity>()
                entities[selectedServerId] = serverEntityMap
                serverManager.integrationRepository(selectedServerId).getEntities()?.forEach {
                    serverEntityMap[it.entityId] = it
                }
                val actionsList = serverActionMap.values.sortedBy { "${it.domain}.${it.action}" }
                updateState { it.copy(serverActions = actionsList) }
                if (currentState().actionText.isNotBlank()) {
                    updateActionFields(currentState().actionText)
                }
            } catch (e: Exception) {
                Timber.w(e, "Unable to load services/entities from Home Assistant server $selectedServerId")
            }
        }
    }

    fun updateActionText(newAction: String) {
        val selectedServerId = currentState().selectedServerId
        val allActions = actions[selectedServerId]?.values.orEmpty().toList()
        val filteredActions = if (newAction.isBlank()) {
            allActions
        } else {
            allActions.filter { action ->
                "${action.domain}.${action.action}".contains(newAction, ignoreCase = true)
            }
        }
        updateState { it.copy(actionText = newAction, serverActions = filteredActions) }

        updateActionFields(newAction)
    }

    fun addDynamicField(position: Int, field: ActionFieldBinder) {
        val dynamicFields = currentState().dynamicFields.toMutableList()
        dynamicFields.add(position, field)
        updateState { it.copy(dynamicFields = dynamicFields) }
    }

    fun updateDynamicField(index: Int, value: String) {
        val updatedFields = currentState().dynamicFields.toMutableList()
        updatedFields[index] = updatedFields[index].copy(value = value)
        updateState { it.copy(dynamicFields = updatedFields) }
    }

    fun selectIcon(icon: MdiIcon?) {
        val resolvedIcon = icon ?: Mdi.Flash
        updateState { it.copy(selectedIcon = resolvedIcon, selectedIconId = resolvedIcon.mdiName) }
    }

    fun updateOption(
        label: String? = null,
        selectedBackgroundType: WidgetBackgroundType? = null,
        textColorHex: String? = null,
        requiresAuthentication: Boolean? = null,
    ) {
        updateState { currentState ->
            currentState.copy(
                label = label ?: currentState.label,
                selectedBackgroundType = selectedBackgroundType ?: currentState.selectedBackgroundType,
                textColorHex = textColorHex,
                requiresAuthentication = requiresAuthentication ?: currentState.requiresAuthentication,
            )
        }
    }

    fun updateActionFields(actionText: String) {
        val dynamicFields = currentState().dynamicFields.toMutableList()
        val selectedServerId = currentState().selectedServerId
        ongoingJob?.cancel()
        ongoingJob = viewModelScope.launch {
            val action = actions[selectedServerId]?.get(actionText)
            if (action != null) {
                dynamicFields.clear()
                val actionData = action.actionData
                val existingActionData = mutableMapOf<String, Any?>()
                dao.get(widgetId)?.let { buttonWidget ->
                    if (buttonWidget.serverId == selectedServerId &&
                        "${buttonWidget.domain}.${buttonWidget.service}" == actionText
                    ) {
                        val dbMap: Map<String, Any?> =
                            kotlinJsonMapper.decodeFromString(MapAnySerializer, buttonWidget.serviceData)
                        existingActionData.putAll(dbMap)
                    }
                }
                if (actionData.target != false) {
                    dynamicFields.add(0, ActionFieldBinder(actionText, "entity_id", existingActionData["entity_id"]))
                    val entityMap = entities[selectedServerId]
                    val domainEntities = mutableListOf("all")
                    val actionDomain = action.domain
                    if (entityMap != null) {
                        if (actionDomain == "homeassistant") {
                            domainEntities.addAll(entityMap.keys)
                        } else {
                            entityMap.keys.filterTo(domainEntities) {
                                it.startsWith(actionDomain) || it.startsWith("group")
                            }
                        }
                    }
                    updateState { it.copy(domainEntities = domainEntities) }
                }
                actionData.fields.keys.sorted().forEach { fieldKey ->
                    val binder = ActionFieldBinder(actionText, fieldKey, existingActionData[fieldKey])
                    if (fieldKey.contains("_id")) dynamicFields.add(0, binder) else dynamicFields.add(binder)
                }
                existingActionData.keys.minus("entity_id").minus(actionData.fields.keys).forEach { extraKey ->
                    dynamicFields.add(ActionFieldBinder(actionText, extraKey, existingActionData[extraKey]))
                }
            } else {
                dynamicFields.clear()
            }
            updateState { it.copy(dynamicFields = dynamicFields) }
        }
    }

    override suspend fun buildPendingEntity(context: Context): ButtonWidgetEntity? {
        val state = currentState()
        val serverId = state.selectedServerId
        val actionsForServer = actions[serverId].orEmpty()
        val actionTextParts = state.actionText.split(".", limit = 2)
        val domain = actionsForServer[state.actionText]?.domain ?: actionTextParts.getOrElse(0) { "" }
        val action = actionsForServer[state.actionText]?.action ?: actionTextParts.getOrElse(1) { "" }
        val actionDataMap = state.dynamicFields.mapNotNull { field ->
            cleanDynamicFieldValue(field.value)?.let { cleaned -> field.field to cleaned }
        }.toMap()

        return ButtonWidgetEntity(
            id = widgetId,
            serverId = serverId,
            domain = domain,
            service = action,
            label = state.label,
            iconName = state.selectedIcon.mdiName,
            serviceData = kotlinJsonMapper.encodeToString(MapAnySerializer, actionDataMap),
            backgroundType = state.selectedBackgroundType,
            textColor = state.textColorHex,
            requireAuthentication = state.requiresAuthentication,
        )
    }
}

private fun cleanDynamicFieldValue(value: Any?): Any? {
    if (value !is String) return value
    return if (value.contains(",")) {
        value.split(",").map(String::trim).filter(String::isNotEmpty).takeIf(List<String>::isNotEmpty)
    } else {
        value.trim().takeIf(String::isNotBlank)
    }
}
