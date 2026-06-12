package io.homeassistant.companion.android.widgets.entity

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
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
import timber.log.Timber

internal data class EntityWidgetTextColors(val white: String, val black: String)

internal enum class EntityWidgetTextColor {
    WHITE,
    BLACK,
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

    var selectedServerId by mutableIntStateOf(ServerManager.SERVER_ID_ACTIVE)
        private set

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

    var selectedEntityId by mutableStateOf(preselectedEntityId)
        private set
    var appendAttributes by mutableStateOf(false)
        private set
    var selectedAttributeIds by mutableStateOf<List<String>>(emptyList())
        private set
    var label by mutableStateOf("")
        private set
    var textSize by mutableStateOf(DEFAULT_TEXT_SIZE)
        private set
    var stateSeparator by mutableStateOf("")
        private set
    var attributeSeparator by mutableStateOf("")
        private set
    var selectedTapAction by mutableStateOf(WidgetTapAction.REFRESH)
        private set
    var selectedBackgroundType by mutableStateOf(WidgetBackgroundType.DAYNIGHT)
        private set
    internal var selectedTextColor by mutableStateOf(EntityWidgetTextColor.WHITE)
        private set
    var isUpdateWidget by mutableStateOf(false)
        private set

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
        selectedBackgroundType = defaultBackgroundType

        initializeState(widgetId)
    }

    private fun initializeState(widgetId: Int) = viewModelScope.launch {
        val widget = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && selectedEntityId == null) {
            staticWidgetDao.get(widgetId)
        } else {
            null
        }

        if (widget != null) {
            isUpdateWidget = true
            selectedServerId = widget.serverId
            selectedEntityId = widget.entityId
            appendAttributes = !widget.attributeIds.isNullOrBlank()
            selectedAttributeIds = widget.attributeIds
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                .orEmpty()
            label = widget.label.orEmpty()
            textSize = widget.textSize.toInt().toString()
            stateSeparator = widget.stateSeparator
            attributeSeparator = widget.attributeSeparator
            selectedTapAction = widget.tapAction
            selectedBackgroundType = widget.backgroundType
            selectedTextColor = if (widget.textColor == textColors.black) {
                EntityWidgetTextColor.BLACK
            } else {
                EntityWidgetTextColor.WHITE
            }
        } else {
            selectedServerId = serverManager.getServer()?.id ?: ServerManager.SERVER_ID_ACTIVE
        }
    }

    fun onServerSelected(serverId: Int) {
        if (serverId == selectedServerId) return

        selectedServerId = serverId
        selectedEntityId = null
        selectedAttributeIds = emptyList()
        selectedTapAction = WidgetTapAction.REFRESH
    }

    fun onEntitySelected(entityId: String?) {
        selectedEntityId = entityId
        selectedAttributeIds = emptyList()

        val entity = selectedEntity()
        selectedTapAction = if (entity?.domain in EntityExt.APP_PRESS_ACTION_DOMAINS) {
            WidgetTapAction.TOGGLE
        } else {
            WidgetTapAction.REFRESH
        }

        if (label.isBlank() || labelFromEntity) {
            val friendlyName = entity?.friendlyName?.takeIf { it != entity.entityId }.orEmpty()
            label = friendlyName
            labelFromEntity = friendlyName.isNotEmpty()
        }
    }

    fun onAppendAttributesChanged(append: Boolean) {
        appendAttributes = append
    }

    fun onAttributeAdded(attributeId: String) {
        if (attributeId !in selectedAttributeIds) {
            selectedAttributeIds = selectedAttributeIds + attributeId
        }
    }

    fun onAttributeRemoved(attributeId: String) {
        selectedAttributeIds = selectedAttributeIds - attributeId
    }

    fun onLabelChanged(value: String) {
        label = value
        labelFromEntity = false
    }

    fun onTextSizeChanged(value: String) {
        textSize = value.filter(Char::isDigit)
    }

    fun onStateSeparatorChanged(value: String) {
        stateSeparator = value
    }

    fun onAttributeSeparatorChanged(value: String) {
        attributeSeparator = value
    }

    fun onTapActionSelected(action: WidgetTapAction) {
        selectedTapAction = action
    }

    fun onBackgroundTypeSelected(backgroundType: WidgetBackgroundType) {
        selectedBackgroundType = backgroundType
    }

    internal fun onTextColorSelected(textColor: EntityWidgetTextColor) {
        selectedTextColor = textColor
    }

    fun selectedEntity(): Entity? = entities.value.firstOrNull { it.entityId == selectedEntityId }

    suspend fun isValidSelection(): Boolean {
        return serverManager.getServer(selectedServerId) != null && selectedEntity() != null
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
        val appWidgetManager = AppWidgetManager.getInstance(context)
        check(appWidgetManager.isRequestPinAppWidgetSupported) { "Widget pinning is not supported" }

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
        context.applicationContext.sendBroadcast(
            Intent(context.applicationContext, EntityWidget::class.java).apply {
                action = BaseWidgetProvider.UPDATE_WIDGETS
            },
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(preselectedEntityId: String?): EntityWidgetConfigureViewModel
    }
}

private const val DEFAULT_TEXT_SIZE = "30"
