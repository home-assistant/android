package io.homeassistant.companion.android.widgets.button

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.color.DynamicColors
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
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import io.homeassistant.companion.android.widgets.common.ActionFieldBinder
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@HiltViewModel
class ButtonWidgetViewModel @Inject constructor(
    val buttonWidgetDao: ButtonWidgetDao,
    val serverManager: ServerManager,
) : ViewModel() {

    data class ButtonWidgetUiState(
        val selectedServerId: Int? = ServerManager.SERVER_ID_ACTIVE,
        val servers: List<Server> = emptyList(),
        val actionText: String = "",
        val serverActions: List<Action> = emptyList(),
        val dynamicFields: List<ActionFieldBinder> = emptyList(),
        val domainEntities: List<String> = emptyList(),
        val selectedIcon: MdiIcon = Mdi.Flash,
        val selectedIconId: String? = null,
        val label: String = "",
        val selectedBackgroundType: WidgetBackgroundType = if (DynamicColors.isDynamicColorAvailable()) {
            WidgetBackgroundType.DYNAMICCOLOR
        } else {
            WidgetBackgroundType.DAYNIGHT
        },
        val textColorIndex: Int = 0,
        val requiresAuthentication: Boolean = false,
        val isUpdating: Boolean? = false,
    )

    private val _uiState: MutableStateFlow<ButtonWidgetUiState> = MutableStateFlow(ButtonWidgetUiState())
    val uiState: StateFlow<ButtonWidgetUiState> = _uiState.asStateFlow()

    private var supportedTextColors: List<String> = emptyList()
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var requestLauncherSetup: Boolean = false

    private val actions = mutableMapOf<Int, MutableMap<String, Action>>()
    private val entities = mutableMapOf<Int, MutableMap<String, Entity>>()
    private val selectedServerMutex = Mutex()
    private var selectedServerActions: List<Action> = emptyList()

    private var ongoingJob: Job? = null

    fun onSetup(widgetId: Int, requestLauncherSetup: Boolean, supportedTextColors: List<String>) {
        this.requestLauncherSetup = requestLauncherSetup
        this.supportedTextColors = supportedTextColors
        this.widgetId = widgetId
        viewModelScope.launch {
            val servers = serverManager.servers()
            val activeServerId = serverManager.getServer()?.id
            _uiState.update { it.copy(servers = servers, selectedServerId = activeServerId) }
            for (server in servers) {
                try {
                    val serverActionMap = mutableMapOf<String, Action>()
                    actions[server.id] = serverActionMap
                    serverManager.integrationRepository(server.id).getServices()?.forEach {
                        serverActionMap["${it.domain}.${it.action}"] = it
                    }
                    val serverEntityMap = mutableMapOf<String, Entity>()
                    entities[server.id] = serverEntityMap
                    serverManager.integrationRepository(server.id).getEntities()?.forEach {
                        serverEntityMap[it.entityId] = it
                    }
                    if (server.id == activeServerId) {
                        val actionsList = serverActionMap.values.sortedBy { "${it.domain}.${it.action}" }
                        selectedServerActions = actionsList
                        _uiState.update { it.copy(serverActions = actionsList) }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Unable to load services/entities from Home Assistant server ${server.id}")
                }
            }
            buttonWidgetDao.get(widgetId)?.let { widget ->
                val icon = Mdi.fromHaName(widget.iconName) ?: Mdi.Flash
                val colorIndex = supportedTextColors.indexOf(widget.textColor)
                updateActionText("${widget.domain}.${widget.service}")
                setServer(widget.serverId)
                _uiState.update { currentState ->
                    currentState.copy(
                        label = widget.label.orEmpty(),
                        selectedBackgroundType = widget.backgroundType,
                        selectedIcon = icon,
                        selectedIconId = icon.mdiName,
                        textColorIndex = if (colorIndex == -1) 0 else colorIndex,
                        requiresAuthentication = widget.requireAuthentication,
                        isUpdating = true,
                    )
                }
            }
        }
    }

    private fun getPendingDaoEntity(): ButtonWidgetEntity {
        val state = _uiState.value
        with(state) {
            val serverId = checkNotNull(selectedServerId) { "Selected server ID is null" }
            val actionsForServer = actions[serverId].orEmpty()
            val actionTextParts = actionText.split(".", limit = 2)
            val domain = actionsForServer[actionText]?.domain ?: actionTextParts.getOrElse(0) { "" }
            val action = actionsForServer[actionText]?.action ?: actionTextParts.getOrElse(1) { "" }
            val actionDataMap = dynamicFields.mapNotNull { field ->
                cleanDynamicFieldValue(field.value)?.let { cleaned -> field.field to cleaned }
            }.toMap()

            return ButtonWidgetEntity(
                id = widgetId,
                serverId = serverId,
                domain = domain,
                service = action,
                label = label,
                iconName = selectedIcon.mdiName,
                serviceData = kotlinJsonMapper.encodeToString(MapAnySerializer, actionDataMap),
                backgroundType = selectedBackgroundType,
                textColor = supportedTextColors[textColorIndex],
                requireAuthentication = requiresAuthentication,
            )
        }
    }

    fun updateWidget(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(widgetId)
            ButtonGlanceAppWidget().update(appContext, glanceId)
        }
    }

    fun updateActionText(newAction: String) {
        _uiState.update { it.copy(actionText = newAction) }
        val domain = newAction.split(".")[0]
        val validItems = selectedServerActions.filter { "${it.domain}.${it.action}".contains(domain) }
        _uiState.update { it.copy(serverActions = validItems) }

        val dynamicFields = _uiState.value.dynamicFields.toMutableList()
        val selectedServerId = _uiState.value.selectedServerId
        ongoingJob?.cancel()
        ongoingJob = viewModelScope.launch {
            val serverActions = actions[selectedServerId].orEmpty()
            val action = serverActions[newAction]
            if (action != null) {
                dynamicFields.clear()
                val actionData = action.actionData
                val existingActionData = mutableMapOf<String, Any?>()
                buttonWidgetDao.get(widgetId)?.let { buttonWidget ->
                    if (buttonWidget.serverId == selectedServerId &&
                        "${buttonWidget.domain}.${buttonWidget.service}" == newAction
                    ) {
                        val dbMap: Map<String, Any?> = kotlinJsonMapper.decodeFromString(
                            MapAnySerializer,
                            buttonWidget.serviceData,
                        )
                        existingActionData.putAll(dbMap)
                    }
                }
                if (actionData.target != false) {
                    dynamicFields.add(0, ActionFieldBinder(newAction, "entity_id", existingActionData["entity_id"]))
                    val entityMap = entities[selectedServerId]
                    val domainEntities = mutableListOf("all")
                    val actionDomain = action.domain
                    if (entityMap != null) {
                        if (actionDomain == "homeassistant") {
                            domainEntities.addAll(entityMap.keys)
                        } else {
                            entityMap.keys.filterTo(domainEntities) {
                                it.startsWith(actionDomain) ||
                                    it.startsWith("group")
                            }
                        }
                    }
                    _uiState.update { it.copy(domainEntities = domainEntities) }
                }
                actionData.fields.keys.sorted().forEach { fieldKey ->
                    val binder = ActionFieldBinder(newAction, fieldKey, existingActionData[fieldKey])
                    if (fieldKey.contains("_id")) dynamicFields.add(0, binder) else dynamicFields.add(binder)
                }
                existingActionData.keys.minus("entity_id").minus(actionData.fields.keys).forEach { extraKey ->
                    dynamicFields.add(ActionFieldBinder(newAction, extraKey, existingActionData[extraKey]))
                }
            } else {
                dynamicFields.clear()
            }
            _uiState.update { it.copy(dynamicFields = dynamicFields) }
        }
    }

    fun setServer(serverId: Int) {
        val selectedServerId = _uiState.value.selectedServerId
        if (selectedServerId == serverId) return
        _uiState.update { it.copy(selectedServerId = serverId, actionText = "") }
        viewModelScope.launch {
            selectedServerMutex.withLock {
                val serverActionsMap = actions[serverId] ?: return@withLock
                val actionsList = serverActionsMap.values.sortedBy { "${it.domain}.${it.action}" }
                selectedServerActions = actionsList
                _uiState.update { it.copy(serverActions = actionsList) }
            }
        }
    }

    fun addDynamicField(position: Int, field: ActionFieldBinder) {
        val dynamicFields = _uiState.value.dynamicFields.toMutableList()
        dynamicFields.add(position, field)
        _uiState.update { it.copy(dynamicFields = dynamicFields) }
    }

    fun updateDynamicField(index: Int, value: String) {
        val currentState = _uiState.value
        val updatedFields = currentState.dynamicFields.toMutableList()
        updatedFields[index] = updatedFields[index].copy(value = value)
        _uiState.update { it.copy(dynamicFields = updatedFields) }
    }

    fun selectIcon(icon: MdiIcon?) {
        val resolvedIcon = icon ?: Mdi.Flash
        _uiState.update { it.copy(selectedIcon = resolvedIcon, selectedIconId = resolvedIcon.mdiName) }
    }

    fun updateOption(
        label: String? = null,
        selectedBackgroundType: WidgetBackgroundType? = null,
        textColorIndex: Int? = null,
        requiresAuthentication: Boolean? = null,
    ) {
        _uiState.update { currentState ->
            currentState.copy(
                label = label ?: currentState.label,
                selectedBackgroundType = selectedBackgroundType ?: currentState.selectedBackgroundType,
                textColorIndex = textColorIndex ?: currentState.textColorIndex,
                requiresAuthentication = requiresAuthentication ?: currentState.requiresAuthentication,
            )
        }
    }

    suspend fun requestWidgetCreation(context: Context) {
        buttonWidgetDao.getWidgetCountFlow().drop(1).onStart {
            GlanceAppWidgetManager(context)
                .requestPinGlanceAppWidget(
                    ButtonWidget::class.java,
                    successCallback = PendingIntent.getBroadcast(
                        context,
                        System.currentTimeMillis().toInt(),
                        Intent(context, ButtonWidget::class.java).apply {
                            action = ACTION_APPWIDGET_CREATED
                            putExtra(EXTRA_WIDGET_ENTITY, getPendingDaoEntity())
                        },
                        // We need the PendingIntent to be mutable so the system inject the EXTRA_APPWIDGET_ID of the created widget
                        PendingIntent.FLAG_MUTABLE,
                    ),
                )
        }.first()
    }

    suspend fun updateWidgetConfiguration() {
        buttonWidgetDao.add(getPendingDaoEntity())
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
