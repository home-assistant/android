package io.homeassistant.companion.android.widgets.button

import android.appwidget.AppWidgetManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.currentState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.color.DynamicColors
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.integration.Action
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.util.icondialog.mdiName
import io.homeassistant.companion.android.widgets.common.ActionFieldBinder
import javax.inject.Inject
import kotlin.collections.indexOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
        val action: String = "",
        val selectedServerId: Int? = ServerManager.SERVER_ID_ACTIVE,
        val servers: List<Server> = emptyList(),
        val serverActions: List<Action> = emptyList(),
        val dynamicFields: List<ActionFieldBinder> = emptyList(),
        val selectedIcon: IIcon = CommunityMaterial.Icon2.cmd_flash,
        val selectedIconId: String? = null,
        val label: String = "",
        val selectedBackgroundType: WidgetBackgroundType = if (DynamicColors.isDynamicColorAvailable()) {
            WidgetBackgroundType.DYNAMICCOLOR
        } else {
            WidgetBackgroundType.DAYNIGHT
        },
        val textColorIndex: Int = 0,
        val requiresAuthentication: Boolean = false,
    )

    private val _uiState: MutableStateFlow<ButtonWidgetUiState> = MutableStateFlow(ButtonWidgetUiState())
    val uiState: StateFlow<ButtonWidgetUiState> = _uiState.asStateFlow()

    private var supportedTextColors: List<String> = emptyList()

    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var requestLauncherSetup: Boolean = false

    private var actions = mutableMapOf<Int, HashMap<String, Action>>()
    private var entities = mutableMapOf<Int, HashMap<String, Entity>>()
    private val selectedServerMutex = Mutex()
    private var selectedServerActions: List<Action> = emptyList()

    private var ongoingJob: Job? = null

    init {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    servers = serverManager.servers(),
                    selectedServerId = serverManager.getServer()?.id,
                )
            }
            for (server in serverManager.servers()) {
                launch {
                    getActionsFromServer(server)
                }
                launch {
                    getEntitiesFromServer(server)
                }
            }
        }
    }

    fun onSetup(widgetId: Int, requestLauncherSetup: Boolean, supportedTextColors: List<String>) {
        this.requestLauncherSetup = requestLauncherSetup
        this.supportedTextColors = supportedTextColors
        this.widgetId = widgetId
        maybeLoadPreviousState(widgetId)
    }

    private fun maybeLoadPreviousState(widgetId: Int) {
        viewModelScope.launch {
            buttonWidgetDao.get(widgetId)?.let { widget ->
                _uiState.update { currentState ->
                    val icon = CommunityMaterial.getIconByMdiName(widget.iconName)
                    val colorIndex = supportedTextColors.indexOf(widget.textColor)
                    currentState.copy(
                        action = "${widget.domain}.${widget.service}",
                        selectedServerId = widget.serverId,
                        label = widget.label ?: "",
                        selectedBackgroundType = widget.backgroundType,
                        selectedIcon = icon ?: CommunityMaterial.Icon2.cmd_flash,
                        selectedIconId = widget.iconName,
                        textColorIndex = if (colorIndex == -1) 0 else colorIndex,
                        requiresAuthentication = widget.requireAuthentication,
                    )
                }
            }
        }
    }

    private suspend fun getActionsFromServer(server: Server) {
        val selectedServerId = _uiState.value.selectedServerId
        try {
            actions[server.id] = HashMap()
            serverManager.integrationRepository(server.id).getServices()?.forEach {
                actions[server.id]?.set(getActionString(it), it)
            }
            if (server.id == selectedServerId) setAdapterActions(server.id)
        } catch (e: Exception) {
            // Custom components can cause actions to not load
            // Display error text
            Timber.e(e, "Unable to load actions from Home Assistant")
        }
    }

    private suspend fun getEntitiesFromServer(server: Server) {
        val selectedServerId = _uiState.value.selectedServerId
        try {
            entities[server.id] = HashMap()
            serverManager.integrationRepository(server.id).getEntities()?.forEach {
                entities[server.id]?.set(it.entityId, it)
            }
            if (server.id == selectedServerId) setAdapterActions(server.id)
        } catch (e: Exception) {
            // If entities fail to load, it's okay to pass
            // an empty map to the dynamicFieldAdapter
        }
    }

    fun updateActionText(newAction: String) {
        _uiState.update { currentState ->
            currentState.copy(
                action = newAction,
            )
        }
        updateActionFields(newAction)
        filterAdapterActions(newAction)
    }

    private fun getActionString(action: Action): String {
        return "${action.domain}.${action.action}"
    }

    fun updateLabel(newLabel: String) {
        _uiState.update { currentState ->
            currentState.copy(
                label = newLabel,
            )
        }
    }

    fun setServer(serverId: Int) {
        val selectedServerId = _uiState.value.selectedServerId
        if (selectedServerId == serverId) return
        _uiState.update { currentState ->
            currentState.copy(
                action = "",
                selectedServerId = serverId,
            )
        }
        viewModelScope.launch {
            selectedServerMutex.withLock {
                setAdapterActions(serverId)
            }
        }
    }

    fun addDynamicField(position: Int, field: ActionFieldBinder) {
        _uiState.update { currentState ->
            val dynamicFields = currentState.dynamicFields.toMutableList()
            dynamicFields.add(position, field)
            currentState.copy(
                dynamicFields = dynamicFields,
            )
        }
    }

    private fun updateDynamicFields(dynamicFields: List<ActionFieldBinder>) {
        _uiState.update { currentState ->
            currentState.copy(
                dynamicFields = dynamicFields,
            )
        }
    }

    fun selectIcon(icon: IIcon) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedIcon = icon,
                selectedIconId = icon.mdiName,
            )
        }
    }

    fun updateSelectedBackgroundType(backgroundType: WidgetBackgroundType) {
        _uiState.update { currentState ->
            currentState.copy(
                selectedBackgroundType = backgroundType,
            )
        }
    }

    fun updateTextColorIndex(textColorIndex: Int) {
        _uiState.update { currentState ->
            currentState.copy(
                textColorIndex = textColorIndex,
            )
        }
    }

    fun setRequiresAuthentication(authenticationRequired: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(
                requiresAuthentication = authenticationRequired,
            )
        }
    }

    fun setServerActions(actions: List<Action>) {
        _uiState.update { currentState ->
            currentState.copy(
                serverActions = actions
            )
        }
    }

    fun updateActionFields(actionText: String) {
        val dynamicFields = _uiState.value.dynamicFields.toMutableList()
        val selectedServerId = _uiState.value.selectedServerId
        ongoingJob?.cancel()
        ongoingJob = viewModelScope.launch {
            if (actions[selectedServerId].orEmpty().keys.contains(actionText)) {
                Timber.d("Valid domain and action--processing dynamic fields")

                // Make sure there are not already any dynamic fields created
                // This can happen if selecting the drop-down twice or pasting
                dynamicFields.clear()

                // We only call this if servicesAvailable was fetched and is not null,
                // so we can safely assume that it is not null here
                val actionData = actions[selectedServerId]!![actionText]!!.actionData
                val target = actionData.target
                val fields = actionData.fields

                val fieldKeys = fields.keys
                Timber.d("Fields applicable to this action: $fields")

                val existingActionData = mutableMapOf<String, Any?>()
                val addedFields = mutableListOf<String>()
                buttonWidgetDao.get(widgetId)?.let { buttonWidget ->
                    if (
                        buttonWidget.serverId != selectedServerId ||
                        "${buttonWidget.domain}.${buttonWidget.service}" != actionText
                    ) {
                        return@let
                    }

                    val dbMap: Map<String, Any?> = kotlinJsonMapper.decodeFromString(
                        MapAnySerializer,
                        buttonWidget.serviceData,
                    )
                    for (item in dbMap) {
                        val value =
                            item.value.toString().replace("[", "").replace("]", "") +
                                if (item.key == "entity_id") ", " else ""
                        existingActionData[item.key] = value.ifEmpty { null }
                        addedFields.add(item.key)
                    }
                }

                if (target != false) {
                    dynamicFields.add(
                        0,
                        ActionFieldBinder(actionText, "entity_id", existingActionData["entity_id"]),
                    )
                }

                fieldKeys.sorted().forEach { fieldKey ->
                    Timber.d("Creating a text input box for $fieldKey")

                    // Insert a dynamic layout
                    // IDs get priority and go at the top, since the other fields
                    // are usually optional but the ID is required
                    if (fieldKey.contains("_id")) {
                        dynamicFields.add(
                            0,
                            ActionFieldBinder(actionText, fieldKey, existingActionData[fieldKey]),
                        )
                    } else {
                        dynamicFields.add(ActionFieldBinder(actionText, fieldKey, existingActionData[fieldKey]))
                    }
                }
                addedFields.minus("entity_id").minus(fieldKeys).forEach { extraFieldKey ->
                    Timber.d("Creating a text input box for extra $extraFieldKey")
                    dynamicFields.add(
                        ActionFieldBinder(actionText, extraFieldKey, existingActionData[extraFieldKey]),
                    )
                }
            } else {
                if (dynamicFields.isNotEmpty()) {
                    dynamicFields.clear()
                }
            }
            Timber.i(dynamicFields.toString())
            updateDynamicFields(dynamicFields)
        }
    }

    private fun setAdapterActions(serverId: Int) {
        Timber.i("Setting Adapter Actions")
        var selectedServerActions: List<Action> = emptyList()
        if (actions[serverId] != null) {
            selectedServerActions = actions[serverId]?.values.orEmpty().toMutableList()
            val comparator = Comparator { t1: Action, t2: Action ->
                getActionString(t1).compareTo(getActionString(t2))
            }
            this.selectedServerActions = selectedServerActions.sortedWith(comparator)
            setServerActions(this.selectedServerActions)
        }
    }

    private fun filterAdapterActions(constraint: CharSequence) {
        val validItems = ArrayList<Action>()
        for (i in 0 until selectedServerActions.size) {
            val item = selectedServerActions[i]
            if (getActionString(item).startsWith(constraint)) {
                validItems.add(item)
            }
        }
        setServerActions(validItems)
    }
//        binding.addButton.setOnClickListener {
//            if (requestLauncherSetup) {
//                val widgetConfigAction = binding.widgetTextConfigService.text.toString()
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
//                    selectedServerId != null &&
//                    (
//                        widgetConfigAction in actions[selectedServerId].orEmpty().keys ||
//                            widgetConfigAction.split(".", limit = 2).size == 2
//                        )
//                ) {
//                    lifecycleScope.launch {
//                        requestWidgetCreation()
//                    }
//                } else {
//                    showAddWidgetError()
//                }
//            } else {
//                lifecycleScope.launch {
//                    updateWidget()
//                }
//            }
//        }
}
