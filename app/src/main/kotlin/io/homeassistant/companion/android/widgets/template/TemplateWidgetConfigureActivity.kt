package io.homeassistant.companion.android.widgets.template

import android.annotation.SuppressLint
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.compose.ExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.WidgetBackgroundTypeExposedDropdownMenu
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat
import io.homeassistant.companion.android.util.getHexForColor
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import io.homeassistant.companion.android.util.safeTopWindowInsets
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider.Companion.UPDATE_WIDGETS
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
        enableEdgeToEdgeCompat()
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

        observeActions()

        setContent {
            HomeAssistantAppTheme {
                TemplateWidgetConfigureScreen(
                    viewModel = viewModel,
                    onActionClick = { onActionClick(requestLauncherSetup) },
                )
            }
        }
    }

    private fun observeActions() = viewModel.action.onEach(::handleActions).launchIn(lifecycleScope)

    private fun handleActions(action: Action) {
        when (action) {
            is Action.RequestWidgetCreationAction -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    handleRequestWidgetCreationAction(action.pendingEntity)
                }
            }

            Action.UpdateWidgetAction -> handleUpdateWidgetAction()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleRequestWidgetCreationAction(pendingEntity: TemplateWidgetEntity) {
        val appWidgetManager = getSystemService(AppWidgetManager::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
        } else {
            PendingIntent.FLAG_MUTABLE
        }
        appWidgetManager?.requestPinAppWidget(
            ComponentName(this, TemplateWidget::class.java),
            null,
            PendingIntent.getBroadcast(
                this,
                System.currentTimeMillis().toInt(),
                Intent(this, TemplateWidget::class.java).apply {
                    action = ACTION_APPWIDGET_CREATED
                    putExtra(EXTRA_WIDGET_ENTITY, pendingEntity)
                },
                flags,
            ),
        )
    }

    private fun handleUpdateWidgetAction() {
        val intent = Intent(this, TemplateWidget::class.java)
        intent.action = UPDATE_WIDGETS
        sendBroadcast(intent)
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun onActionClick(requestLauncherSetup: Boolean) {
        lifecycleScope.launch {
            if (requestLauncherSetup) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requestPinWidget()
                } else {
                    showAddWidgetError()
                }
            } else {
                onUpdateWidget()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun requestPinWidget() {
        try {
            viewModel.requestWidgetCreation()
            finish()
        } catch (_: IllegalStateException) {
            showAddWidgetError()
        }
    }

    private suspend fun onUpdateWidget() {
        try {
            viewModel.updateWidgetConfiguration()
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

@Composable
private fun TemplateWidgetConfigureScreen(
    viewModel: TemplateWidgetConfigureViewModel,
    onActionClick: () -> Unit,
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle(emptyList())

    TemplateWidgetConfigureView(
        servers = servers,
        selectedServerId = viewModel.selectedServerId,
        onServerSelected = viewModel::setServer,
        templateText = viewModel.templateText,
        onTemplateTextChanged = { viewModel.templateText = it },
        renderedTemplate = viewModel.renderedTemplate,
        isTemplateValid = viewModel.isTemplateValid,
        textSize = viewModel.textSize,
        onTextSizeChanged = { viewModel.textSize = it },
        selectedBackgroundType = viewModel.selectedBackgroundType,
        onBackgroundTypeSelected = { viewModel.selectedBackgroundType = it },
        textColorIndex = viewModel.textColorIndex,
        onTextColorSelected = { viewModel.textColorIndex = it },
        isUpdateWidget = viewModel.isUpdateWidget,
        onActionClick = onActionClick,
    )
}

@Suppress("ComposeUnstableCollections") // Matches ServerExposedDropdownMenu signature; same as TodoWidgetConfigureActivity
@Composable
private fun TemplateWidgetConfigureView(
    servers: List<Server>,
    selectedServerId: Int,
    onServerSelected: (Int) -> Unit,
    templateText: String,
    onTemplateTextChanged: (String) -> Unit,
    renderedTemplate: String?,
    isTemplateValid: Boolean,
    textSize: String,
    onTextSizeChanged: (String) -> Unit,
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
                title = { Text(stringResource(commonR.string.create_template)) },
                windowInsets = safeTopWindowInsets(),
                backgroundColor = colorResource(commonR.color.colorBackground),
                contentColor = colorResource(commonR.color.colorOnBackground),
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
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            HATextField(
                value = templateText,
                onValueChange = onTemplateTextChanged,
                placeholder = { Text(stringResource(commonR.string.template_widget_default)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = Int.MAX_VALUE,
                singleLine = false,
            )

            if (renderedTemplate != null) {
                Text(
                    text = renderedTemplate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            } else if (templateText.isEmpty()) {
                Text(
                    text = stringResource(commonR.string.empty_template),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            }

            HATextField(
                value = textSize,
                onValueChange = onTextSizeChanged,
                label = { Text(stringResource(commonR.string.widget_text_size_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            WidgetBackgroundTypeExposedDropdownMenu(
                current = selectedBackgroundType,
                onSelected = { onBackgroundTypeSelected(it) },
            )

            AnimatedVisibility(visible = selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
                ExposedDropdownMenu(
                    label = stringResource(commonR.string.widget_text_color_label),
                    keys = listOf(
                        stringResource(commonR.string.widget_text_color_black),
                        stringResource(commonR.string.widget_text_color_white),
                    ),
                    currentIndex = textColorIndex,
                    onSelected = { onTextColorSelected(it) },
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onActionClick,
                enabled = isTemplateValid,
            ) {
                Text(stringResource(if (isUpdateWidget) commonR.string.update_widget else commonR.string.add_widget))
            }
        }
    }
}

@Preview
@Composable
private fun TemplateWidgetConfigureViewPreview() {
    HomeAssistantAppTheme {
        TemplateWidgetConfigureView(
            servers = listOf(previewServer1, previewServer2),
            selectedServerId = 0,
            onServerSelected = {},
            templateText = "Hello world",
            renderedTemplate = "Hello world",
            isTemplateValid = true,
            textSize = "14",
            onTextSizeChanged = {},
            selectedBackgroundType = WidgetBackgroundType.DAYNIGHT,
            onBackgroundTypeSelected = {},
            textColorIndex = 0,
            onTextColorSelected = {},
            isUpdateWidget = false,
            onActionClick = {},
            onTemplateTextChanged = {},
        )
    }
}
