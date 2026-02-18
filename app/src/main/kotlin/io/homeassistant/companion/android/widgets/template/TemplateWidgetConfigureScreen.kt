package io.homeassistant.companion.android.widgets.template

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
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.compose.ExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.WidgetBackgroundTypeExposedDropdownMenu
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import io.homeassistant.companion.android.util.safeTopWindowInsets

@Composable
internal fun TemplateWidgetConfigureScreen(
    viewModel: TemplateWidgetConfigureViewModel,
    onActionClick: () -> Unit,
) {
    val servers by viewModel.servers.collectAsStateWithLifecycle(emptyList())
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.errorMessage.collect { resId ->
            snackbarHostState.showSnackbar(context.getString(resId))
        }
    }

    TemplateWidgetConfigureView(
        servers = servers,
        selectedServerId = uiState.selectedServerId,
        onServerSelected = viewModel::setServer,
        templateText = uiState.templateText,
        onTemplateTextChanged = viewModel::onTemplateTextChanged,
        renderedTemplate = uiState.renderedTemplate,
        isTemplateValid = uiState.isTemplateValid,
        templateRenderError = uiState.templateRenderError,
        textSize = uiState.textSize,
        onTextSizeChanged = viewModel::onTextSizeChanged,
        selectedBackgroundType = uiState.selectedBackgroundType,
        onBackgroundTypeSelected = viewModel::onBackgroundTypeSelected,
        textColorIndex = uiState.textColorIndex,
        onTextColorSelected = viewModel::onTextColorSelected,
        isUpdateWidget = uiState.isUpdateWidget,
        onActionClick = onActionClick,
        snackbarHostState = snackbarHostState,
    )
}

@Composable
private fun TemplateWidgetConfigureView(
    servers: List<Server>,
    selectedServerId: Int,
    onServerSelected: (Int) -> Unit,
    templateText: String,
    onTemplateTextChanged: (String) -> Unit,
    renderedTemplate: String?,
    isTemplateValid: Boolean,
    templateRenderError: TemplateRenderError?,
    textSize: String,
    onTextSizeChanged: (String) -> Unit,
    selectedBackgroundType: WidgetBackgroundType,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    textColorIndex: Int,
    onTextColorSelected: (Int) -> Unit,
    isUpdateWidget: Boolean,
    onActionClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
            } else if (templateRenderError != null) {
                Text(
                    text = stringResource(
                        when (templateRenderError) {
                            TemplateRenderError.TEMPLATE_ERROR -> commonR.string.template_error
                            TemplateRenderError.RENDER_ERROR -> commonR.string.template_render_error
                        },
                    ),
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
            templateRenderError = null,
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
