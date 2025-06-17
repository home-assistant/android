package io.homeassistant.companion.android.widgets.grid.config

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.widgets.grid.GridWidget
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GridWidgetConfigureActivity : BaseActivity() {
    companion object {
        private const val PIN_WIDGET_CALLBACK = "io.homeassistant.companion.android.widgets.grid.GridWidgetConfigureActivity.PIN_WIDGET_CALLBACK"
    }

    private val viewModel: GridConfigurationViewModel by viewModels()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)
        val widgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        viewModel.onSetup(widgetId)

        setContent {
            HomeAssistantAppTheme {
                GridWidgetConfigurationScreen(
                    viewModel = viewModel,
                    onAddWidget = { onSetupWidget(it) },
                )
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun onSetupWidget(config: GridConfiguration) {
        if (intent.extras?.getBoolean(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, false) == true) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                viewModel.isValidConfig(config)
            ) {
                requestPinWidget()
            } else {
                showAddWidgetError()
            }
        } else {
            onAddWidget(config)
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestPinWidget() {
        val context = this@GridWidgetConfigureActivity
        lifecycleScope.launch {
            GlanceAppWidgetManager(context)
                .requestPinGlanceAppWidget(
                    GridWidget::class.java,
                    successCallback = PendingIntent.getActivity(
                        context,
                        System.currentTimeMillis().toInt(),
                        Intent(context, GridWidgetConfigureActivity::class.java)
                            .putExtra(PIN_WIDGET_CALLBACK, true)
                            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    ),
                )
        }
    }

    private fun onAddWidget(config: GridConfiguration) {
        try {
            viewModel.addWidgetConfiguration(config)
            setResult(RESULT_OK)
            viewModel.updateWidget(this)
            finish()
        } catch (_: IllegalStateException) {
            showAddWidgetError()
        }
    }

    private fun showAddWidgetError() {
        Toast.makeText(applicationContext, R.string.widget_creation_error, Toast.LENGTH_LONG).show()
    }
}
