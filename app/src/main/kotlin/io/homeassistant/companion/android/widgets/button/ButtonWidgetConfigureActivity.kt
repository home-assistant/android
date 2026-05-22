package io.homeassistant.companion.android.widgets.button

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.theme.HATextStyle
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.integration.Action
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.compose.MdcAlertDialog
import io.homeassistant.companion.android.util.getHexForColor
import io.homeassistant.companion.android.util.icondialog.IconDialog
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import io.homeassistant.companion.android.util.safeTopWindowInsets
import io.homeassistant.companion.android.widgets.button.ButtonWidgetViewModel.ButtonWidgetUiState
import io.homeassistant.companion.android.widgets.common.ActionFieldBinder
import io.homeassistant.companion.android.widgets.common.WidgetUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

// TODO Migrate to compose https://github.com/home-assistant/android/issues/6305
@AndroidEntryPoint
class ButtonWidgetConfigureActivity : BaseActivity() {
    private val viewModel: ButtonWidgetViewModel by viewModels()

    private val supportedTextColors: List<String>
        get() = listOf(
            application.getHexForColor(commonR.color.colorWidgetButtonLabelBlack),
            application.getHexForColor(android.R.color.white),
        )

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        // Find the widget id from the intent.
        val appWidgetId =
            intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val requestLauncherSetup = intent.extras?.getBoolean(
            ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER,
            false,
        ) ?: false

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        viewModel.onSetup(appWidgetId, requestLauncherSetup, supportedTextColors)

        setContent {
            HATheme {
                ButtonWidgetConfigureScreen(viewModel, { onAddWidgetClicked() })
            }
        }
    }

    private fun onAddWidgetClicked() {
        lifecycleScope.launch {
            if (intent.extras?.getBoolean(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, false) == true) {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
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
        val context = this@ButtonWidgetConfigureActivity
        lifecycleScope.launch {
            viewModel.requestWidgetCreation(context)
            finish()
        }
    }

    private suspend fun onUpdateWidget() {
        try {
            viewModel.updateWidgetConfiguration()
            setResult(RESULT_OK)
            viewModel.updateWidget(this@ButtonWidgetConfigureActivity)
            finish()
        } catch (_: Exception) {
            showUpdateWidgetError()
        }
    }

    private fun showAddWidgetError() {
        Toast.makeText(applicationContext, commonR.string.widget_creation_error, Toast.LENGTH_LONG).show()
    }

    private fun showUpdateWidgetError() {
        Toast.makeText(applicationContext, commonR.string.widget_update_error, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun ButtonWidgetConfigureScreen(viewModel: ButtonWidgetViewModel, onAddWidgetClicked: () -> Unit) {
//    val state = remember (viewModel) { viewModel.uiState.value }
    val state by viewModel.uiState.collectAsStateWithLifecycle(ButtonWidgetUiState())
    LaunchedEffect(viewModel.actionFieldState) {
        snapshotFlow { viewModel.actionFieldState.text.toString() }.collectLatest {
            viewModel.updateActionText(it)
        }
    }
    ButtonWidgetConfigureView(
        actionFieldState = viewModel.actionFieldState,
        servers = state.servers,
        selectedServerId = state.selectedServerId,
        onServerSelected = viewModel::setServer,
        serverActions = state.serverActions,
        dynamicFields = state.dynamicFields,
        onDynamicFieldUpdated = viewModel::updateDynamicField,
        icon = state.selectedIcon,
        onIconSelected = viewModel::selectIcon,
        onAddFieldDialogOkClicked = viewModel::addDynamicField,
        label = state.label,
        onLabelUpdated = viewModel::updateLabel,
        textColorIndex = state.textColorIndex,
        onTextColorSelected = viewModel::updateTextColorIndex,
        selectedBackgroundType = state.selectedBackgroundType,
        onBackgroundTypeSelected = viewModel::updateSelectedBackgroundType,
        isRequireAuthenticationChecked = state.requiresAuthentication,
        onRequireAuthenticationChecked = viewModel::setRequiresAuthentication,
        onAddWidgetClicked = onAddWidgetClicked,
    )
}

@Composable
private fun ButtonWidgetConfigureView(
    actionFieldState: TextFieldState,
    servers: List<Server>,
    selectedServerId: Int?,
    onServerSelected: (Int) -> Unit,
    serverActions: List<Action>,
    dynamicFields: List<ActionFieldBinder>,
    onDynamicFieldUpdated: (Int, String) -> Unit,
    icon: IIcon,
    onIconSelected: (IIcon) -> Unit,
    onAddFieldDialogOkClicked: (Int, ActionFieldBinder) -> Unit,
    label: String,
    onLabelUpdated: (String) -> Unit,
    selectedBackgroundType: WidgetBackgroundType,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    textColorIndex: Int,
    onTextColorSelected: (Int) -> Unit,
    isRequireAuthenticationChecked: Boolean,
    onRequireAuthenticationChecked: (Boolean) -> Unit,
    onAddWidgetClicked: () -> Unit,
) {
    var showAddFieldDialog by remember { mutableStateOf(false) }
    var showIconDialog by remember { mutableStateOf(false) }
    Timber.i("Selected Server: $selectedServerId")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(commonR.string.configure_action)) },
                windowInsets = safeTopWindowInsets(),
                backgroundColor = colorResource(commonR.color.colorBackground),
                contentColor = colorResource(commonR.color.colorOnBackground),
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(safeBottomWindowInsets())
                .padding(contentPadding)
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showAddFieldDialog) {
                val actionText = actionFieldState.text as String
                AddFieldDialog(
                    action = actionText,
                    onCancel = {
                        showAddFieldDialog = false
                    },
                    onOk = { actionField ->
                        if (dynamicFields.any {
                                it.field == actionText
                            }
                        ) {
                            showAddFieldDialog = false
                            return@AddFieldDialog
                        }

                        onAddFieldDialogOkClicked(dynamicFields.size, actionField)
                        showAddFieldDialog = false
                    },
                    modifier = Modifier,
                )
            }

            if (showIconDialog) {
                IconDialog(
                    onSelect = {
                        showIconDialog = false
                        onIconSelected(it)
                    },
                    onDismissRequest = { showIconDialog = false },
                )
            }

            if (servers.size > 1) {
                ServerSelector(
                    servers = servers,
                    selectedServerId = selectedServerId,
                    onServerSelected = onServerSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ActionTextFieldInput(actionFieldState, serverActions)

            if (dynamicFields.isNotEmpty()) {
                dynamicFields.forEachIndexed { index, fieldBinder ->
                    DynamicFieldInput(index, fieldBinder, onDynamicFieldUpdated)
                }
            }

            HAAccentButton(
                text = stringResource(commonR.string.add_action_data_field),
                onClick = { showAddFieldDialog = true },
                modifier = Modifier.align(Alignment.End),
            )

            IconSelector(
                icon,
                {
                    showIconDialog = true
                },
            )

            OutlinedTextField(
                label = { Text(text = stringResource(commonR.string.label)) },
                value = label,
                onValueChange = onLabelUpdated,
                textStyle = HATextStyle.UserInput,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth(),
            )

            BackgroundTypeSelector(selectedBackgroundType, onBackgroundTypeSelected)

            if (selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
                WidgetTextColorSelector(textColorIndex, onTextColorSelected)
            }

            RequireAuthCheckbox(isChecked = isRequireAuthenticationChecked, onChecked = onRequireAuthenticationChecked)

            HAAccentButton(
                text = stringResource(commonR.string.add_widget),
                onClick = onAddWidgetClicked,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionTextFieldInput(
    actionFieldState: TextFieldState,
    serverActions: List<Action>,
    modifier: Modifier = Modifier,
) {
    var isActionDropdownExpanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            label = { Text(text = stringResource(commonR.string.label_action)) },
            state = actionFieldState,
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = HATextStyle.UserInput,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    isActionDropdownExpanded = state.hasFocus
                },
        )
        if (serverActions.isNotEmpty()) {
            DropdownMenu(
                expanded = isActionDropdownExpanded,
                onDismissRequest = {
                    isActionDropdownExpanded = false
                },
                properties = PopupProperties(focusable = false),
            ) {
                serverActions.forEach { action ->
                    val text = "${action.domain}.${action.action}"
                    DropdownMenuItem(
                        text = { Text(text = text) },
                        onClick = {
                            actionFieldState.setTextAndPlaceCursorAtEnd(text)
                            isActionDropdownExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@Composable
fun DynamicFieldInput(
    index: Int,
    field: ActionFieldBinder,
    onDynamicFieldUpdated: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialText = field.value as? String
    val fieldInputState = rememberTextFieldState(initialText = initialText ?: "")
    LaunchedEffect(fieldInputState) {
        snapshotFlow { fieldInputState.text.toString() }.collectLatest {
            onDynamicFieldUpdated(index, it)
        }
    }
    OutlinedTextField(
        label = { Text(text = field.field) },
        state = fieldInputState,
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = HATextStyle.UserInput,
        modifier = modifier
            .fillMaxWidth(),
    )
}

@Composable
fun IconSelector(icon: IIcon, onIconSelectorClicked: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(commonR.string.label_icon))
        IconButton(
            onClick = onIconSelectorClicked,
            modifier = Modifier.size(24.dp),
        ) {
            com.mikepenz.iconics.compose.Image(
                asset = icon,
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorResource(commonR.color.colorIcon)),
            )
        }
    }
}

@Composable
fun RequireAuthCheckbox(isChecked: Boolean, onChecked: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onChecked(it) },
        )
        Text(text = stringResource(commonR.string.widget_checkbox_require_authentication))
    }
}

@Composable
fun AddFieldDialog(
    action: String,
    onCancel: (() -> Unit)?,
    onOk: ((ActionFieldBinder) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val inputValue = remember { mutableStateOf("") }

    MdcAlertDialog(
        modifier = modifier,
        onDismissRequest = { },
        title = { Text(text = "Field") },
        content = {
            TextField(
                value = inputValue.value,
                onValueChange = { input: String ->
                    inputValue.value = input
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth(),
            )
        },
        onCancel = onCancel,
        onSave = null,
        onOK = {
            val field = ActionFieldBinder(action, inputValue.value)
            onOk?.invoke(field)
        },
    )
}

@Composable
fun ServerSelector(
    servers: List<Server>,
    selectedServerId: Int?,
    onServerSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modelsByKey = remember(servers) {
        servers.associateBy { it.id }
    }

    val items = remember(modelsByKey) {
        modelsByKey.map { (id, server) ->
            HADropdownItem(key = id, label = server.friendlyName)
        }
    }

    HADropdownMenu(
        items = items,
        selectedKey = selectedServerId,
        onItemSelected = { onServerSelected(it) },
        modifier = modifier,
        label = "Select Server",
    )
}

@Composable
fun BackgroundTypeSelector(
    selectedBackgroundType: WidgetBackgroundType,
    onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keys = remember { WidgetUtils.getBackgroundOptionList(context) }
    val modelsByKey = remember(keys) {
        keys.withIndex().associateBy { it.index }
    }
    val items = remember(modelsByKey) {
        modelsByKey.map { (id, backgroundType) ->
            HADropdownItem(key = id, label = backgroundType.value)
        }
    }
    HADropdownMenu(
        items = items,
        selectedKey = WidgetUtils.getSelectedBackgroundOption(context, selectedBackgroundType, keys),
        onItemSelected = { onBackgroundTypeSelected(WidgetUtils.getWidgetBackgroundType(context, keys[it])) },
        label = "Select background type",
        modifier = modifier,
    )
}

@Composable
fun WidgetTextColorSelector(textColorIndex: Int, onTextColorSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    HADropdownMenu(
        items = listOf(
            HADropdownItem(0, stringResource(commonR.string.widget_text_color_black)),
            HADropdownItem(1, stringResource(commonR.string.widget_text_color_white)),
        ),
        selectedKey = textColorIndex,
        onItemSelected = { onTextColorSelected(it) },
        label = stringResource(commonR.string.widget_text_color_title),
        modifier = modifier,
    )
}

@Composable
@Preview(name = "Light Mode")
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun ButtonWidgetConfigureScreenPreview() {
    HATheme {
        ButtonWidgetConfigureView(
            actionFieldState = rememberTextFieldState(),
            servers = listOf(
                previewServer1,
                previewServer2,
            ),
            selectedServerId = 0,
            onServerSelected = {},
            serverActions = emptyList(),
            dynamicFields = listOf(ActionFieldBinder("Test Action", "Test", 1)),
            onDynamicFieldUpdated = { i: Int, value: String -> },
            icon = CommunityMaterial.Icon2.cmd_flash,
            onIconSelected = {},
            label = "",
            onLabelUpdated = {},
            textColorIndex = 0,
            onTextColorSelected = {},
            onAddFieldDialogOkClicked = { i: Int, binder: ActionFieldBinder -> },
            selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
            onBackgroundTypeSelected = {},
            isRequireAuthenticationChecked = false,
            onRequireAuthenticationChecked = {},
            onAddWidgetClicked = {},
        )
    }
}

@Composable
@Preview(name = "Light Mode")
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun AddFieldDialogPreview() {
    HATheme {
        AddFieldDialog("", {}, {}, modifier = Modifier)
    }
}
