package io.homeassistant.companion.android.widgets.template

import android.content.Context
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.widgets.configure.BaseWidgetConfigureViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import timber.log.Timber

@HiltViewModel(assistedFactory = TemplateWidgetConfigureViewModel.Factory::class)
internal class TemplateWidgetConfigureViewModel @AssistedInject constructor(
    templateWidgetDao: TemplateWidgetDao,
    serverManager: ServerManager,
    @Assisted widgetId: Int,
) : BaseWidgetConfigureViewModel<TemplateWidgetConfigureState, TemplateWidgetEntity>(
    serverManager = serverManager,
    widgetId = widgetId,
    dao = templateWidgetDao,
    initialState = TemplateWidgetConfigureState(),
) {

    private var renderJob: Job? = null

    fun onTemplateChanged(value: String) {
        updateState { it.copy(template = value) }

        // A whitespace-only template isn't worth rendering either
        if (value.isBlank()) {
            renderJob?.cancel()
            updateState { it.copy(preview = TemplatePreview.Empty, isRenderingPreview = false) }
        } else {
            renderTemplate(value, currentState().selectedServerId)
        }
    }

    fun onTextSizeChanged(value: String) {
        updateState { it.copy(textSize = value.filter(Char::isDigit)) }
    }

    fun onTextColorSelected(colorHex: String) {
        updateState { it.copy(textColorHex = colorHex) }
    }

    override val widgetProviderClass: Class<*> = TemplateWidget::class.java

    override fun restoreState(state: TemplateWidgetConfigureState, widget: TemplateWidgetEntity) = state.copy(
        selectedServerId = widget.serverId,
        template = widget.template,
        textSize = widget.textSize.toInt().toString(),
        selectedBackgroundType = widget.backgroundType,
        textColorHex = widget.textColor,
        isUpdateWidget = true,
    )

    /** Renders the current template against the selected server, if there is one to render. */
    override fun reload() {
        val template = currentState().template
        if (template.isNotBlank()) renderTemplate(template, currentState().selectedServerId)
    }

    /** Renders [template] against [serverId], cancelling any render already in flight. */
    private fun renderTemplate(template: String, serverId: Int) {
        renderJob?.cancel()
        // Marked before launching so `isActionEnabled` can't stay true on a stale render while
        // this one is in flight (see `TemplateWidgetConfigureState.isActionEnabled`).
        updateState { it.copy(isRenderingPreview = true) }
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
            updateState { it.copy(preview = preview, isRenderingPreview = false) }
        }
    }

    override suspend fun buildPendingEntity(context: Context): TemplateWidgetEntity? {
        val current = currentState()
        if (!current.isActionEnabled) {
            Timber.e("Cannot build the widget, the current configuration is invalid")
        }

        return current.takeIf { it.isActionEnabled }?.let {
            TemplateWidgetEntity(
                id = widgetId,
                serverId = current.selectedServerId,
                template = current.template,
                textSize = current.textSizeOrDefault,
                lastUpdate = dao.get(widgetId)?.lastUpdate ?: "Loading",
                backgroundType = current.selectedBackgroundType,
                textColor = current.textColorHex.takeIf {
                    current.selectedBackgroundType == WidgetBackgroundType.TRANSPARENT
                },
            )
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(widgetId: Int): TemplateWidgetConfigureViewModel
    }
}
