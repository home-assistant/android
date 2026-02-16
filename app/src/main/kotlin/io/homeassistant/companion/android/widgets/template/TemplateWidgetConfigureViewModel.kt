package io.homeassistant.companion.android.widgets.template

import android.appwidget.AppWidgetManager
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class TemplateWidgetConfigureViewModel @Inject constructor(
    private val templateWidgetDao: TemplateWidgetDao,
    private val serverManager: ServerManager,
) : ViewModel() {

    @VisibleForTesting
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private var supportedTextColors: List<String> = emptyList()
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private val _action = MutableSharedFlow<Action>()
    val action = _action.asSharedFlow()

    val servers = serverManager.serversFlow

    var selectedServerId by mutableIntStateOf(ServerManager.SERVER_ID_ACTIVE)
        private set

    var templateText by mutableStateOf("")
    var textSize by mutableStateOf("14")
    var selectedBackgroundType by mutableStateOf(
        if (DynamicColors.isDynamicColorAvailable()) {
            WidgetBackgroundType.DYNAMICCOLOR
        } else {
            WidgetBackgroundType.DAYNIGHT
        },
    )
    var textColorIndex by mutableIntStateOf(0)
    var isUpdateWidget by mutableStateOf(false)
        private set

    var renderedTemplate by mutableStateOf<String?>(null)
        private set
    var isTemplateValid by mutableStateOf(false)
        private set

    @OptIn(FlowPreview::class)
    private fun startTemplateRendering() {
        viewModelScope.launch {
            combine(
                snapshotFlow { templateText },
                snapshotFlow { selectedServerId },
            ) { template, serverId -> template to serverId }
                .debounce(RENDER_DEBOUNCE_MS)
                .collect { (template, serverId) ->
                    renderTemplate(template, serverId)
                }
        }
    }

    private suspend fun renderTemplate(template: String, serverId: Int) {
        if (template.isEmpty()) {
            renderedTemplate = null
            isTemplateValid = false
            return
        }
        if (!serverManager.isRegistered() || serverManager.getServer(serverId) == null) {
            Timber.w("Not rendering template because server is not set")
            return
        }
        withContext(ioDispatcher) {
            try {
                val result = serverManager.integrationRepository(serverId)
                    .renderTemplate(template, mapOf())
                    .toString()
                @Suppress("UNNECESSARY_SAFE_CALL")
                renderedTemplate = HtmlCompat.fromHtml(result, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    ?.toString()
                    ?.trimEnd()
                    ?: result
                isTemplateValid = true
            } catch (e: Exception) {
                Timber.e(e, "Exception while rendering template")
                renderedTemplate = null
                isTemplateValid = false
            }
        }
    }

    /**
     * Initialize the ViewModel with the widget ID and supported text colors.
     * Loads existing widget configuration if editing an existing widget.
     */
    fun onSetup(widgetId: Int, supportedTextColors: List<String>) {
        if (this.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) return
        this.supportedTextColors = supportedTextColors
        this.widgetId = widgetId
        maybeLoadPreviousState(widgetId)
        startTemplateRendering()
    }

    private fun maybeLoadPreviousState(widgetId: Int) {
        viewModelScope.launch {
            templateWidgetDao.get(widgetId)?.let { widget ->
                isUpdateWidget = true
                selectedServerId = widget.serverId
                templateText = widget.template
                textSize = widget.textSize.toInt().toString()
                selectedBackgroundType = widget.backgroundType
                if (widget.textColor != null) {
                    val colorIndex = supportedTextColors.indexOf(widget.textColor)
                    textColorIndex = if (colorIndex == -1) 0 else colorIndex
                }
            }
        }
    }

    /**
     * Update the selected server and trigger template re-rendering.
     */
    fun setServer(serverId: Int) {
        if (selectedServerId == serverId) return
        selectedServerId = serverId
    }

    private fun getPendingDaoEntity(): TemplateWidgetEntity {
        val textColor = if (selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
            supportedTextColors.getOrNull(textColorIndex) ?: supportedTextColors.first()
        } else {
            null
        }
        return TemplateWidgetEntity(
            id = widgetId,
            serverId = selectedServerId,
            template = templateText,
            textSize = textSize.toFloatOrNull() ?: DEFAULT_TEXT_SIZE,
            lastUpdate = "Loading",
            backgroundType = selectedBackgroundType,
            textColor = textColor,
        )
    }

    /**
     * Save the widget configuration to the database and send a broadcast to update the widget.
     *
     * @throws IllegalStateException if the widget ID is invalid or server is not valid
     */
    suspend fun updateWidgetConfiguration() {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            throw IllegalStateException("Widget ID is invalid")
        }

        val entity = getPendingDaoEntity()
        templateWidgetDao.add(entity)
        _action.emit(Action.UpdateWidgetAction)
    }

    /**
     * Requests the widget to be created and waits until it has been saved to the DAO.
     *
     * **WARNING**: This function does not handle user cancellation. If a user cancels the widget creation,
     * this function will not return. If this function is called again and the user does not cancel,
     * both calls to the function will return. While this behavior could be avoided,
     * it does not cause issues in the current implementation as returning multiple times has no adverse effects.
     */
    suspend fun requestWidgetCreation() {
        val pendingEntity = getPendingDaoEntity()
        templateWidgetDao.getWidgetCountFlow().drop(1).onStart {
            _action.emit(Action.RequestWidgetCreationAction(pendingEntity))
        }.first()
    }
}

private const val RENDER_DEBOUNCE_MS = 500L
private const val DEFAULT_TEXT_SIZE = 12.0f

sealed class Action {
    object UpdateWidgetAction : Action()
    data class RequestWidgetCreationAction(val pendingEntity: TemplateWidgetEntity) : Action()
}
