package io.homeassistant.companion.android.widgets.todo

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.compose.ExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.SingleEntityPicker
import io.homeassistant.companion.android.util.compose.WidgetBackgroundTypeExposedDropdownMenu
import io.homeassistant.companion.android.util.getHexForColor
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity2
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class TodoWidgetConfigureActivity : BaseActivity() {
    companion object {
        private const val PIN_WIDGET_CALLBACK = "io.homeassistant.companion.android.widgets.todo.TodoWidgetConfigureActivity.PIN_WIDGET_CALLBACK"
    }

    private val viewModel: TodoWidgetConfigureViewModel by viewModels()
    private val supportedTextColors: List<String>
        get() = listOf(
            application.getHexForColor(R.color.colorWidgetButtonLabelBlack),
            application.getHexForColor(android.R.color.white),
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)
        val widgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        viewModel.onSetup(widgetId, supportedTextColors)

        setContent {
            HomeAssistantAppTheme {
                TodoWidgetConfigureScreen(
                    viewModel = viewModel,
                    onAddWidget = { onSetupWidget() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val extras = intent.extras
        if (extras == null) {
            Timber.d("Received new intent without data")
            return
        }
        val widgetId = extras.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (extras.getBoolean(PIN_WIDGET_CALLBACK, false)) {
            viewModel.onSetup(widgetId, supportedTextColors)
            onAddWidget()
        }
    }

    @SuppressLint("ObsoleteSdkInt")
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

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestPinWidget() {
        val context = this@TodoWidgetConfigureActivity
        lifecycleScope.launch {
            GlanceAppWidgetManager(context)
                .requestPinGlanceAppWidget(
                    TodoWidget::class.java,
                    successCallback = PendingIntent.getActivity(
                        context,
                        System.currentTimeMillis().toInt(),
                        Intent(context, TodoWidgetConfigureActivity::class.java)
                            .putExtra(PIN_WIDGET_CALLBACK, true)
                            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    ),
                )
        }
    }

    private fun onAddWidget() {
        try {
            viewModel.addWidgetConfiguration()
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

@Composable
private fun TodoWidgetConfigureScreen(
    viewModel: TodoWidgetConfigureViewModel,
    onAddWidget: () -> Unit,
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val entities by viewModel.entities.collectAsStateWithLifecycle()

    TodoWidgetConfigureView(
        servers = servers,
        selectedServerId = viewModel.selectedServerId,
        onServerSelected = viewModel::setServer,
        entities = entities,
        selectedEntityId = viewModel.selectedEntityId,
        onEntitySelected = { viewModel.selectedEntityId = it },
        showCompleted = viewModel.showCompletedState,
        onShowCompletedChanged = { viewModel.showCompletedState = it },
        selectedBackgroundType = viewModel.selectedBackgroundType,
        onBackgroundTypeSelected = { viewModel.selectedBackgroundType = it },
        textColorIndex = viewModel.textColorIndex,
        onTextColorSelected = { viewModel.textColorIndex = it },
        isUpdateWidget = viewModel.isUpdateWidget,
        onAddWidget = onAddWidget,
    )
}

@Composable
private fun TodoWidgetConfigureView(
    servers: List<Server>,
    selectedServerId: Int,
    onServerSelected: (Int) -> Unit,
    entities: List<Entity<*>>,
    selectedEntityId: String?,
    onEntitySelected: (String?) -> Unit,
    showCompleted: Boolean,
    onShowCompletedChanged: (Boolean) -> Unit,
    selectedBackgroundType: WidgetBackgroundType,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    textColorIndex: Int,
    onTextColorSelected: (Int) -> Unit,
    isUpdateWidget: Boolean,
    onAddWidget: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_todo_label)) },
                windowInsets = WindowInsets.statusBars,
                backgroundColor = colorResource(R.color.colorBackground),
                contentColor = colorResource(R.color.colorOnBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (servers.size > 1) {
                ServerExposedDropdownMenu(
                    servers = servers,
                    current = selectedServerId,
                    onSelected = { onServerSelected(it) },
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            SingleEntityPicker(
                entities = entities,
                currentEntity = selectedEntityId,
                onEntityCleared = { onEntitySelected(null) },
                onEntitySelected = {
                    onEntitySelected(it)
                    true
                },
            )

            Row(
                modifier = Modifier.clickable { onShowCompletedChanged(!showCompleted) },
            ) {
                Text(
                    text = stringResource(R.string.widget_todo_show_completed),
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .weight(1f),
                )

                Switch(
                    checked = showCompleted,
                    onCheckedChange = { onShowCompletedChanged(it) },
                    colors = SwitchDefaults.colors(uncheckedThumbColor = colorResource(R.color.colorSwitchUncheckedThumb)),
                )
            }

            WidgetBackgroundTypeExposedDropdownMenu(
                current = selectedBackgroundType,
                onSelected = { onBackgroundTypeSelected(it) },
                modifier = Modifier.padding(bottom = 16.dp),
            )

            if (selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
                ExposedDropdownMenu(
                    label = stringResource(R.string.widget_text_color_title),
                    keys = listOf(
                        stringResource(R.string.widget_text_color_black),
                        stringResource(R.string.widget_text_color_white),
                    ),
                    currentIndex = textColorIndex,
                    onSelected = { onTextColorSelected(it) },
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onAddWidget() },
            ) {
                Text(stringResource(if (isUpdateWidget) R.string.update_widget else R.string.add_widget))
            }
        }
    }
}

@Preview
@Composable
private fun TodoWidgetConfigureViewPreview() {
    HomeAssistantAppTheme {
        TodoWidgetConfigureView(
            servers = listOf(
                previewServer1,
                previewServer2,
            ),
            selectedServerId = 0,
            onServerSelected = {},
            entities = listOf(
                previewEntity1,
                previewEntity2,
            ),
            selectedEntityId = previewEntity1.entityId,
            onEntitySelected = {},
            showCompleted = true,
            onShowCompletedChanged = {},
            selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
            onBackgroundTypeSelected = {},
            textColorIndex = 0,
            onTextColorSelected = {},
            isUpdateWidget = true,
            onAddWidget = {},
        )
    }
}
