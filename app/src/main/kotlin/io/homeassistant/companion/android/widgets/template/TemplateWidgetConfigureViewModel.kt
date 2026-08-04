package io.homeassistant.companion.android.widgets.template

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.RemoteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.color.DynamicColors
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import timber.log.Timber

@HiltViewModel(assistedFactory = TemplateWidgetConfigureViewModel.Factory::class)
class TemplateWidgetConfigureViewModel @AssistedInject constructor(
    private val templateWidgetDao: TemplateWidgetDao,
    private val serverManager: ServerManager,
    @Assisted private val widgetId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(
        TemplateWidgetConfigureState(
            dynamicColorAvailable = DynamicColors.isDynamicColorAvailable(),
            selectedBackgroundType = if (DynamicColors.isDynamicColorAvailable()) {
                WidgetBackgroundType.DYNAMICCOLOR
            } else {
                WidgetBackgroundType.DAYNIGHT
            },
        ),
    )
    internal val state: StateFlow<TemplateWidgetConfigureState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<Int>(replay = 1)

    /** Errors to surface to the user, as string resources. */
    val errors = _errors.asSharedFlow()

    private var renderJob: Job? = null

    init {
        viewModelScope.launch { restoreConfiguration() }

        viewModelScope.launch {
            serverManager.serversFlow.collect { servers ->
                _state.update { current ->
                    current.copy(
                        serversDropdownItems = servers.map { server ->
                            HADropdownItem(key = server.id, label = server.friendlyName)
                        },
                    )
                }
            }
        }
    }

    fun onServerSelected(serverId: Int) {
        if (serverId == _state.value.selectedServerId) return

        _state.update { it.copy(selectedServerId = serverId) }
        val template = _state.value.template
        if (template.isNotBlank()) renderTemplate(template, serverId)
    }

    fun onTemplateChanged(value: String) {
        _state.update { it.copy(template = value) }

        // A whitespace-only template isn't worth rendering either
        if (value.isBlank()) {
            renderJob?.cancel()
            _state.update { it.copy(preview = TemplatePreview.Empty, isRenderingPreview = false) }
        } else {
            renderTemplate(value, _state.value.selectedServerId)
        }
    }

    fun onTextSizeChanged(value: String) {
        _state.update { it.copy(textSize = value.filter(Char::isDigit)) }
    }

    fun onBackgroundTypeSelected(backgroundType: WidgetBackgroundType) {
        _state.update { it.copy(selectedBackgroundType = backgroundType) }
    }

    fun onTextColorSelected(colorHex: String) {
        _state.update { it.copy(textColorHex = colorHex) }
    }

    /**
     * Persists the current configuration, reporting through [errors] and returning false when it
     * cannot be saved.
     */
    suspend fun updateWidgetConfiguration(): Boolean {
        val widget = getPendingDaoEntity()
        if (widget == null) {
            _errors.emit(commonR.string.widget_update_error)
            return false
        }

        templateWidgetDao.add(widget)
        return true
    }

    /** Asks the already placed widgets to redraw with the configuration that was just saved. */
    fun updateWidget(context: Context) {
        context.sendBroadcast(
            Intent(context, TemplateWidget::class.java).apply {
                action = BaseWidgetProvider.UPDATE_WIDGETS
            },
        )
    }

    /**
     * Builds the widget to persist from the current configuration, or null when it is incomplete.
     */
    internal suspend fun getPendingDaoEntity(): TemplateWidgetEntity? {
        val current = _state.value
        if (!current.isActionEnabled) {
            Timber.e("Cannot build the widget, the current configuration is invalid")
            return null
        }

        return TemplateWidgetEntity(
            id = widgetId,
            serverId = current.selectedServerId,
            template = current.template,
            textSize = current.textSizeOrDefault,
            lastUpdate = templateWidgetDao.get(widgetId)?.lastUpdate ?: "Loading",
            backgroundType = current.selectedBackgroundType,
            textColor = current.textColorHex.takeIf {
                current.selectedBackgroundType == WidgetBackgroundType.TRANSPARENT
            },
        )
    }

    /**
     * Asks the launcher to pin the configured widget and suspends until it is added, reporting
     * through [errors] and returning false when the widget cannot be requested at all.
     */
    @SuppressLint("NewApi") // The API 26 requirement is checked below before touching the pinning APIs.
    suspend fun requestWidgetCreation(context: Context): Boolean {
        if (!SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
            Timber.e("Cannot pin the widget, pinning requires API ${Build.VERSION_CODES.O}")
            _errors.emit(commonR.string.widget_creation_error)
            return false
        }

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val pinningSupported = try {
            appWidgetManager.isRequestPinAppWidgetSupported
        } catch (e: RemoteException) {
            Timber.e(e, "Unable to read isRequestPinAppWidgetSupported")
            false
        }
        if (!pinningSupported) {
            Timber.e("Cannot pin the widget, the launcher does not support it")
            _errors.emit(commonR.string.widget_creation_error)
            return false
        }

        val widget = getPendingDaoEntity()
        if (widget == null) {
            _errors.emit(commonR.string.widget_creation_error)
            return false
        }

        var requestAccepted = false
        templateWidgetDao.getWidgetCountFlow()
            // We drop the first value since we only care about knowing when the widget is actually added
            .drop(1)
            .onStart {
                requestAccepted = appWidgetManager.requestPinAppWidget(
                    ComponentName(context, TemplateWidget::class.java),
                    null,
                    PendingIntent.getBroadcast(
                        context,
                        System.currentTimeMillis().toInt(),
                        Intent(context, TemplateWidget::class.java).apply {
                            action = ACTION_APPWIDGET_CREATED
                            putExtra(EXTRA_WIDGET_ENTITY, widget)
                        },
                        PendingIntent.FLAG_MUTABLE,
                    ),
                )
                // A rejected request never adds a widget, so emit to stop waiting for one
                if (!requestAccepted) emit(0)
            }.first()

        if (!requestAccepted) {
            Timber.e("The launcher rejected the widget pin request")
            _errors.emit(commonR.string.widget_creation_error)
        }
        return requestAccepted
    }

    /**
     * Restores the configuration of an existing widget, or falls back to the active server for a new one.
     */
    private suspend fun restoreConfiguration() {
        val widget = templateWidgetDao.get(widgetId)

        if (widget == null) {
            _state.update {
                it.copy(selectedServerId = serverManager.getServer()?.id ?: ServerManager.SERVER_ID_ACTIVE)
            }
            return
        }

        _state.update {
            it.copy(
                selectedServerId = widget.serverId,
                template = widget.template,
                textSize = widget.textSize.toInt().toString(),
                selectedBackgroundType = widget.backgroundType,
                textColorHex = widget.textColor,
                isUpdateWidget = true,
            )
        }

        if (widget.template.isNotBlank()) {
            renderTemplate(widget.template, widget.serverId)
        }
    }

    /** Renders [template] against [serverId], cancelling any render already in flight. */
    private fun renderTemplate(template: String, serverId: Int) {
        renderJob?.cancel()
        // Marked before launching so `isActionEnabled` can't stay true on a stale render while
        // this one is in flight (see `TemplateWidgetConfigureState.isActionEnabled`).
        _state.update { it.copy(isRenderingPreview = true) }
        renderJob = viewModelScope.launch {
            val preview = try {
                val rendered = serverManager.integrationRepository(serverId).renderTemplate(template, mapOf())
                TemplatePreview.Rendered(rendered.toString())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Exception while rendering template")
                // A SerializationException suggests that the rendered result is not a String (= error)
                TemplatePreview.Error(
                    if (e.cause is SerializationException) {
                        commonR.string.template_error
                    } else {
                        commonR.string.template_render_error
                    },
                )
            }
            _state.update { it.copy(preview = preview, isRenderingPreview = false) }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(widgetId: Int): TemplateWidgetConfigureViewModel
    }
}
