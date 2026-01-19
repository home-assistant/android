package io.homeassistant.companion.android.widgets.grid.config

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GridWidgetConfigureActivity : BaseActivity() {
    private val viewModel: GridConfigurationViewModel by viewModels()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)
        val widgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        viewModel.onSetup(widgetId)

        setContent {
            HomeAssistantAppTheme {
                GridWidgetConfigurationScreen(
                    viewModel = viewModel,
                    onSubmit = ::onSubmit,
                )
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun onSubmit(config: GridConfiguration) {
        lifecycleScope.launch {
            if (intent.extras?.getBoolean(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, false) == true) {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    viewModel.isValidConfig(config)
                ) {
                    requestPinWidget(config)
                } else {
                    showAddWidgetError()
                }
            } else {
                onUpdateWidget(config)
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestPinWidget(config: GridConfiguration) {
        val context = this@GridWidgetConfigureActivity
        lifecycleScope.launch {
            viewModel.requestWidgetCreation(context, config)
            finish()
        }
    }

    private suspend fun onUpdateWidget(config: GridConfiguration) {
        val success = viewModel.updateWidgetConfiguration(config)
        if (success) {
            setResult(RESULT_OK)
            viewModel.updateWidget(this@GridWidgetConfigureActivity)
            finish()
        } else {
            showUpdateWidgetError()
        }
    }

    private fun showAddWidgetError() {
        Toast.makeText(applicationContext, R.string.widget_creation_error, Toast.LENGTH_LONG).show()
    }

    private fun showUpdateWidgetError() {
        Toast.makeText(applicationContext, R.string.widget_update_error, Toast.LENGTH_LONG).show()
    }
}
