package io.homeassistant.companion.android.widgets.todo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.compose.ExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.SingleEntityPicker
import io.homeassistant.companion.android.util.compose.WidgetBackgroundTypeExposedDropdownMenu
import io.homeassistant.companion.android.widgets.mediaplayer.MediaPlayerControlsWidget

@AndroidEntryPoint
class TodoWidgetConfigureActivity : BaseActivity() {
    companion object {
        private const val PIN_WIDGET_CALLBACK = "io.homeassistant.companion.android.widgets.todo.TodoWidgetConfigureActivity.PIN_WIDGET_CALLBACK"
    }

    private val viewModel: TodoWidgetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
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
                TodoWidgetConfigureScreen(
                    viewModel = viewModel,
                    onAddWidget = { onSetupWidget() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val extras = intent.extras ?: return
        val widgetId = extras.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (extras.getBoolean(PIN_WIDGET_CALLBACK, false)) {
            viewModel.onSetup(widgetId)
            onAddWidget()
        }
    }

    private fun onSetupWidget() {
        if (intent.extras?.getBoolean(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, false) == true) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                viewModel.isValidSelection()
            ) {
                requestPinWidget()
            } else {
                showAddWidgetError()
            }
        } else {
            onAddWidget()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestPinWidget() {
        getSystemService<AppWidgetManager>()?.requestPinAppWidget(
            ComponentName(this, MediaPlayerControlsWidget::class.java),
            null,
            PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                Intent(this, TodoWidgetConfigureActivity::class.java)
                    .putExtra(PIN_WIDGET_CALLBACK, true)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        )
    }

    private fun onAddWidget() {
        val intent = viewModel.prepareData()
        if (intent == null) {
            showAddWidgetError()
            return
        }

        sendBroadcast(intent)
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun showAddWidgetError() {
        Toast.makeText(applicationContext, R.string.widget_creation_error, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun TodoWidgetConfigureScreen(
    viewModel: TodoWidgetViewModel,
    onAddWidget: () -> Unit
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val entities by viewModel.entities.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_todo_label)) },
                backgroundColor = colorResource(R.color.colorBackground),
                contentColor = colorResource(R.color.colorOnBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (servers.size > 1) {
                ServerExposedDropdownMenu(
                    servers = servers,
                    current = viewModel.selectedServerId,
                    onSelected = { viewModel.setServer(it) },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            SingleEntityPicker(
                entities = entities,
                currentEntity = viewModel.selectedEntityId,
                onEntityCleared = { viewModel.setEntity(null) },
                onEntitySelected = {
                    viewModel.setEntity(it)
                    true
                }
            )

            WidgetBackgroundTypeExposedDropdownMenu(
                current = viewModel.selectedBackgroundType,
                onSelected = { viewModel.setBackgroundType(it) },
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (viewModel.selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
                ExposedDropdownMenu(
                    label = stringResource(R.string.widget_text_color_title),
                    keys = listOf(
                        stringResource(R.string.widget_text_color_black),
                        stringResource(R.string.widget_text_color_white)
                    ),
                    currentIndex = viewModel.textColorIndex,
                    onSelected = { viewModel.setTextColor(it) },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onAddWidget() }
            ) {
                Text(stringResource(R.string.add_widget))
            }
        }
    }
}
