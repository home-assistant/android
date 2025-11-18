package io.homeassistant.companion.android.widgets.todo

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
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
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat
import io.homeassistant.companion.android.util.getHexForColor
import io.homeassistant.companion.android.util.previewEntity1
import io.homeassistant.companion.android.util.previewEntity2
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import io.homeassistant.companion.android.util.safeTopWindowInsets
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TodoWidgetConfigureActivity : BaseActivity() {
    companion object {
        private const val FOR_ENTITY = "for_entity"

        fun newInstance(context: Context, entityId: String): Intent {
            return Intent(context, TodoWidgetConfigureActivity::class.java).apply {
                putExtra(FOR_ENTITY, entityId)
                putExtra(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    private val viewModel: TodoWidgetConfigureViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<TodoWidgetConfigureViewModel.Factory> { factory ->
                factory.create(intent.extras?.getString(FOR_ENTITY, null))
            }
        },
    )

    private val supportedTextColors: List<String>
        get() = listOf(
            application.getHexForColor(R.color.colorWidgetButtonLabelBlack),
            application.getHexForColor(android.R.color.white),
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeCompat()
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
                    onActionClick = { onActionClick() },
                )
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun onActionClick() {
        lifecycleScope.launch {
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
                onUpdateWidget()
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestPinWidget() {
        val context = this@TodoWidgetConfigureActivity
        lifecycleScope.launch {
            viewModel.requestWidgetCreation(context)
            finish()
        }
    }

    private suspend fun onUpdateWidget() {
        try {
            viewModel.updateWidgetConfiguration()
            setResult(RESULT_OK)
            viewModel.updateWidget(this@TodoWidgetConfigureActivity)
            finish()
        } catch (_: Exception) {
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

@Composable
private fun TodoWidgetConfigureScreen(viewModel: TodoWidgetConfigureViewModel, onActionClick: () -> Unit) {
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
        onActionClick = onActionClick,
    )
}

@Composable
private fun TodoWidgetConfigureView(
    servers: List<Server>,
    selectedServerId: Int,
    onServerSelected: (Int) -> Unit,
    entities: List<Entity>,
    selectedEntityId: String?,
    onEntitySelected: (String?) -> Unit,
    showCompleted: Boolean,
    onShowCompletedChanged: (Boolean) -> Unit,
    selectedBackgroundType: WidgetBackgroundType,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    textColorIndex: Int,
    onTextColorSelected: (Int) -> Unit,
    isUpdateWidget: Boolean,
    onActionClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_todo_label)) },
                windowInsets = safeTopWindowInsets(),
                backgroundColor = colorResource(R.color.colorBackground),
                contentColor = colorResource(R.color.colorOnBackground),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(safeBottomWindowInsets())
                .padding(padding)
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
                    colors = SwitchDefaults.colors(
                        uncheckedThumbColor = colorResource(R.color.colorSwitchUncheckedThumb),
                    ),
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
                onClick = { onActionClick() },
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
            onActionClick = {},
        )
    }
}
