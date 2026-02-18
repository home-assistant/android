package io.homeassistant.companion.android.widgets.template

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.core.text.HtmlCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider.Companion.UPDATE_WIDGETS
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import timber.log.Timber

private const val RENDER_DEBOUNCE_MS = 500L
private const val DEFAULT_TEXT_SIZE = 12.0f

/**
 * Represents the UI state of the Template Widget configuration screen.
 */
data class TemplateWidgetConfigureUiState(
    val selectedServerId: Int = ServerManager.SERVER_ID_ACTIVE,
    val templateText: String = "",
    val renderedTemplate: String? = null,
    val isTemplateValid: Boolean = false,
    val textSize: String = "14",
    val selectedBackgroundType: WidgetBackgroundType =
        if (DynamicColors.isDynamicColorAvailable()) {
            WidgetBackgroundType.DYNAMICCOLOR
        } else {
            WidgetBackgroundType.DAYNIGHT
        },
    val textColorIndex: Int = 0,
    val isUpdateWidget: Boolean = false,
    val templateRenderError: TemplateRenderError? = null,
)

@HiltViewModel
class TemplateWidgetConfigureViewModel @Inject constructor(
    private val templateWidgetDao: TemplateWidgetDao,
    private val serverManager: ServerManager,
) : ViewModel() {

    @VisibleForTesting
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private var supportedTextColors: List<String> = emptyList()
    private var widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private val _uiState = MutableStateFlow(TemplateWidgetConfigureUiState())
    val uiState: StateFlow<TemplateWidgetConfigureUiState> = _uiState.asStateFlow()

    val servers = serverManager.serversFlow

    private var templateRenderingJob: Job? = null

    /**
     * Initialize the ViewModel with the widget ID and supported text colors.
     * Loads existing widget configuration if editing an existing widget.
     *
     * This guard prevents re-initialization when the Activity is recreated due to
     * configuration changes, since the ViewModel survives those changes.
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
                _uiState.update { state ->
                    state.copy(
                        isUpdateWidget = true,
                        selectedServerId = widget.serverId,
                        templateText = widget.template,
                        textSize = widget.textSize.toInt().toString(),
                        selectedBackgroundType = widget.backgroundType,
                        textColorIndex = if (widget.textColor != null) {
                            val colorIndex = supportedTextColors.indexOf(widget.textColor)
                            if (colorIndex == -1) 0 else colorIndex
                        } else {
                            state.textColorIndex
                        },
                    )
                }
            }
        }
    }

    /**
     * Update the selected server.
     */
    fun setServer(serverId: Int) {
        _uiState.update {
            if (it.selectedServerId == serverId) it else it.copy(selectedServerId = serverId)
        }
    }

    /**
     * Update the template text.
     */
    fun onTemplateTextChanged(text: String) {
        _uiState.update { it.copy(templateText = text) }
    }

    /**
     * Update the text size.
     */
    fun onTextSizeChanged(size: String) {
        _uiState.update { it.copy(textSize = size) }
    }

    /**
     * Update the selected background type.
     */
    fun onBackgroundTypeSelected(type: WidgetBackgroundType) {
        _uiState.update { it.copy(selectedBackgroundType = type) }
    }

    /**
     * Update the selected text color index.
     */
    fun onTextColorSelected(index: Int) {
        _uiState.update { it.copy(textColorIndex = index) }
    }

    @OptIn(FlowPreview::class)
    private fun startTemplateRendering() {
        templateRenderingJob?.cancel()
        templateRenderingJob = viewModelScope.launch {
            combine(
                _uiState.mapField { it.templateText },
                _uiState.mapField { it.selectedServerId },
            ) { template, serverId -> template to serverId }
                .debounce(RENDER_DEBOUNCE_MS)
                .collect { (template, serverId) ->
                    renderTemplate(template, serverId)
                }
        }
    }

    /**
     * Extracts a distinct field from the StateFlow to avoid unnecessary emissions.
     */
    private fun <T> StateFlow<TemplateWidgetConfigureUiState>.mapField(
        selector: (TemplateWidgetConfigureUiState) -> T,
    ): Flow<T> {
        return map(selector).distinctUntilChanged()
    }

    private suspend fun renderTemplate(template: String, serverId: Int) {
        if (template.isEmpty()) {
            _uiState.update { it.copy(renderedTemplate = null, isTemplateValid = false) }
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
                val rendered = HtmlCompat.fromHtml(result, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    ?.toString()
                    ?.trimEnd()
                    ?: result
                _uiState.update {
                    it.copy(renderedTemplate = rendered, isTemplateValid = true, templateRenderError = null)
                }
            } catch (e: Exception) {
                if (e is SerializationException) {
                    Timber.e(e, "Template syntax error")
                } else {
                    Timber.e(e, "Error rendering template")
                }
                val errorRendered = if (e.cause is SerializationException) {
                    TemplateRenderError.TEMPLATE_ERROR
                } else {
                    TemplateRenderError.RENDER_ERROR
                }
                _uiState.update {
                    it.copy(
                        renderedTemplate = null,
                        isTemplateValid = false,
                        templateRenderError = errorRendered,
                    )
                }
            }
        }
    }

    private fun getPendingDaoEntity(): TemplateWidgetEntity {
        val state = _uiState.value
        val textColor = if (state.selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
            supportedTextColors.getOrNull(state.textColorIndex) ?: supportedTextColors.first()
        } else {
            null
        }
        return TemplateWidgetEntity(
            id = widgetId,
            serverId = state.selectedServerId,
            template = state.templateText,
            textSize = state.textSize.toFloatOrNull() ?: DEFAULT_TEXT_SIZE,
            lastUpdate = "Loading",
            backgroundType = state.selectedBackgroundType,
            textColor = textColor,
        )
    }

    /**
     * Save the widget configuration to the database and update the widget.
     *
     * @param context the context used to send a broadcast to trigger widget update
     * @throws IllegalStateException if the widget ID is invalid
     */
    suspend fun updateWidgetConfiguration(context: Context) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            throw IllegalStateException("Widget ID is invalid")
        }

        val entity = getPendingDaoEntity()
        templateWidgetDao.add(entity)

        val intent = Intent(context, TemplateWidget::class.java)
        intent.action = UPDATE_WIDGETS
        context.sendBroadcast(intent)
    }

    /**
     * Requests the system to pin a new Template widget.
     *
     * Saves a pending entity and waits until the widget has been created by monitoring the DAO.
     *
     * **WARNING**: This function does not handle user cancellation. If a user cancels the widget creation,
     * this function will not return. If this function is called again and the user does not cancel,
     * both calls to the function will return. While this behavior could be avoided,
     * it does not cause issues in the current implementation as returning multiple times has no adverse effects.
     *
     * @param context the context used to request the pin widget
     */
    suspend fun requestWidgetCreation(context: Context) {
        val pendingEntity = getPendingDaoEntity()
        templateWidgetDao.getWidgetCountFlow().drop(1).onStart {
            val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
            val flags = PendingIntent.FLAG_MUTABLE
            appWidgetManager?.requestPinAppWidget(
                ComponentName(context, TemplateWidget::class.java),
                null,
                PendingIntent.getBroadcast(
                    context,
                    System.currentTimeMillis().toInt(),
                    Intent(context, TemplateWidget::class.java).apply {
                        action = ACTION_APPWIDGET_CREATED
                        putExtra(EXTRA_WIDGET_ENTITY, pendingEntity)
                    },
                    flags,
                ),
            )
        }.first()
    }
}

/**
 * Represents the type of error encountered when rendering a template.
 */
enum class TemplateRenderError {
    /** Error in the template syntax itself. */
    TEMPLATE_ERROR,

    /** Error communicating with the server or rendering the template. */
    RENDER_ERROR,
}