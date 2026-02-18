package io.homeassistant.companion.android.widgets.template

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.getHexForColor
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TemplateWidgetConfigureActivity : BaseActivity() {

    private val viewModel: TemplateWidgetConfigureViewModel by viewModels()

    private val supportedTextColors: List<String>
        get() = listOf(
            application.getHexForColor(commonR.color.colorWidgetButtonLabelBlack),
            application.getHexForColor(android.R.color.white),
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        val extras = intent.extras
        val widgetId = extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val requestLauncherSetup = extras?.getBoolean(
            ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER,
            false,
        ) ?: false

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        viewModel.onSetup(widgetId = widgetId, supportedTextColors = supportedTextColors)

        setContent {
            HomeAssistantAppTheme {
                TemplateWidgetConfigureScreen(
                    viewModel = viewModel,
                    onActionClick = { onActionClick(requestLauncherSetup) },
                )
            }
        }
    }

    private fun onActionClick(requestLauncherSetup: Boolean) {
        lifecycleScope.launch {
            if (requestLauncherSetup) {
                requestPinWidget()
            } else {
                onUpdateWidget()
            }
        }
    }

    private suspend fun requestPinWidget() {
        try {
            viewModel.requestWidgetCreation(this@TemplateWidgetConfigureActivity)
            finish()
        } catch (_: IllegalStateException) {
            showAddWidgetError()
        }
    }

    private suspend fun onUpdateWidget() {
        try {
            viewModel.updateWidgetConfiguration(this@TemplateWidgetConfigureActivity)
            setResult(RESULT_OK)
            finish()
        } catch (_: IllegalStateException) {
            showAddWidgetError()
        }
    }

    private fun showAddWidgetError() {
        Toast.makeText(applicationContext, commonR.string.widget_creation_error, Toast.LENGTH_LONG).show()
    }
}