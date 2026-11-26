package io.homeassistant.companion.android.widgets.button

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.generated.Flash
import io.github.timoptr.mdiicons.rememberImageVector
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.integration.Action
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.compose.MdcAlertDialog
import io.homeassistant.companion.android.util.getHexForColor
import io.homeassistant.companion.android.util.icondialog.IconDialog
import io.homeassistant.companion.android.util.previewServer1
import io.homeassistant.companion.android.util.previewServer2
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import io.homeassistant.companion.android.widgets.common.ActionFieldBinder
import io.homeassistant.companion.android.widgets.common.WidgetUtils
import kotlinx.coroutines.launch
import timber.log.Timber

internal data class ButtonWidgetConfigureCallbacks(
    val onActionTextUpdated: (String) -> Unit,
    val onServerSelected: (Int) -> Unit,
    val onDynamicFieldUpdated: (Int, String) -> Unit,
    val onAddFieldDialogOkClicked: (Int, ActionFieldBinder) -> Unit,
    val onIconSelected: (MdiIcon) -> Unit,
    val onLabelUpdated: (String) -> Unit,
    val onBackgroundTypeSelected: (WidgetBackgroundType) -> Unit,
    val onTextColorSelected: (Int) -> Unit,
    val onRequireAuthenticationChecked: (Boolean) -> Unit,
)

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

        setResult(RESULT_CANCELED)

        val appWidgetId =
            intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val requestLauncherSetup = intent.extras?.getBoolean(
            ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER,
            false,
        ) ?: false

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        viewModel.onSetup(appWidgetId, requestLauncherSetup, supportedTextColors)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val callbacks = remember(viewModel) {
                ButtonWidgetConfigureCallbacks(
                    onActionTextUpdated = viewModel::updateActionText,
                    onServerSelected = viewModel::setServer,
                    onDynamicFieldUpdated = viewModel::updateDynamicField,
                    onAddFieldDialogOkClicked = viewModel::addDynamicField,
                    onIconSelected = viewModel::selectIcon,
                    onLabelUpdated = { viewModel.updateOption(label = it) },
                    onBackgroundTypeSelected = { viewModel.updateOption(selectedBackgroundType = it) },
                    onTextColorSelected = { viewModel.updateOption(textColorIndex = it) },
                    onRequireAuthenticationChecked = { viewModel.updateOption(requiresAuthentication = it) },
                )
            }
            HATheme {
                ButtonWidgetConfigureView(
                    state = state,
                    callbacks = callbacks,
                    onAddWidgetClicked = { onAddWidgetClicked() },
                )
            }
        }
    }

    private fun onAddWidgetClicked() {
        lifecycleScope.launch {
            if (intent.extras?.getBoolean(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, false) == true) {
                if (SdkVersion.isAtLeast(Build.VERSION_CODES.O)) {
                    requestPinWidget()
                } else {
                    Toast.makeText(applicationContext, commonR.string.widget_creation_error, Toast.LENGTH_LONG).show()
                }
            } else {
                try {
                    viewModel.updateWidgetConfiguration()
                    setResult(RESULT_OK)
                    viewModel.updateWidget(this@ButtonWidgetConfigureActivity)
                    finish()
                } catch (_: Exception) {
                    Toast.makeText(applicationContext, commonR.string.widget_update_error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestPinWidget() {
        lifecycleScope.launch {
            viewModel.requestWidgetCreation(this@ButtonWidgetConfigureActivity)
            finish()
        }
    }
}

@Composable
private fun ButtonWidgetConfigureView(
    state: ButtonWidgetViewModel.ButtonWidgetUiState,
    callbacks: ButtonWidgetConfigureCallbacks,
    onAddWidgetClicked: () -> Unit,
) {
    var showAddFieldDialog by remember { mutableStateOf(false) }
    var showIconDialog by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    Timber.i("Selected Server: ${state.selectedServerId}")
    Scaffold(
        topBar = {
            HATopBar(
                title = { Text(stringResource(commonR.string.configure_action)) },
                onCloseClick = { activity?.finish() },
            )
        },
    ) { contentPadding ->
        ConfigureFormContent(
            state = state,
            callbacks = callbacks,
            showAddFieldDialog = showAddFieldDialog,
            onShowAddFieldDialogChange = { showAddFieldDialog = it },
            showIconDialog = showIconDialog,
            onShowIconDialogChange = { showIconDialog = it },
            onAddWidgetClicked = onAddWidgetClicked,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(safeBottomWindowInsets())
                .padding(contentPadding)
                .padding(all = 16.dp),
        )
    }
}

@Composable
private fun ConfigureDialogsSection(
    state: ButtonWidgetViewModel.ButtonWidgetUiState,
    callbacks: ButtonWidgetConfigureCallbacks,
    showAddFieldDialog: Boolean,
    onShowAddFieldDialogChange: (Boolean) -> Unit,
    showIconDialog: Boolean,
    onShowIconDialogChange: (Boolean) -> Unit,
) {
    if (showAddFieldDialog) {
        AddFieldDialog(
            action = state.actionText,
            onCancel = { onShowAddFieldDialogChange(false) },
            onOk = { actionField ->
                if (!state.dynamicFields.any { it.field == state.actionText }) {
                    callbacks.onAddFieldDialogOkClicked(state.dynamicFields.size, actionField)
                }
                onShowAddFieldDialogChange(false)
            },
        )
    }

    if (showIconDialog) {
        IconDialog(
            onSelect = {
                onShowIconDialogChange(false)
                callbacks.onIconSelected(it)
            },
            onDismissRequest = { onShowIconDialogChange(false) },
        )
    }
}

@Composable
private fun ConfigureActionFieldsSection(
    state: ButtonWidgetViewModel.ButtonWidgetUiState,
    callbacks: ButtonWidgetConfigureCallbacks,
    onShowAddFieldDialogChange: (Boolean) -> Unit,
) {
    ActionTextFieldInput(state.actionText, callbacks.onActionTextUpdated, state.serverActions)

    if (state.dynamicFields.isNotEmpty()) {
        state.dynamicFields.forEachIndexed { index, fieldBinder ->
            DynamicFieldInput(index, fieldBinder, state.domainEntities, callbacks.onDynamicFieldUpdated)
        }
    }

    HAAccentButton(
        text = stringResource(commonR.string.add_action_data_field),
        onClick = { onShowAddFieldDialogChange(true) },
    )
}

@Composable
private fun ConfigureFormContent(
    state: ButtonWidgetViewModel.ButtonWidgetUiState,
    callbacks: ButtonWidgetConfigureCallbacks,
    showAddFieldDialog: Boolean,
    onShowAddFieldDialogChange: (Boolean) -> Unit,
    showIconDialog: Boolean,
    onShowIconDialogChange: (Boolean) -> Unit,
    onAddWidgetClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConfigureDialogsSection(
            state = state,
            callbacks = callbacks,
            showAddFieldDialog = showAddFieldDialog,
            onShowAddFieldDialogChange = onShowAddFieldDialogChange,
            showIconDialog = showIconDialog,
            onShowIconDialogChange = onShowIconDialogChange,
        )

        if (state.servers.size > 1) {
            ServerSelector(
                servers = state.servers,
                selectedServerId = state.selectedServerId,
                onServerSelected = callbacks.onServerSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ConfigureActionFieldsSection(state, callbacks, onShowAddFieldDialogChange)

        IconSelector(icon = state.selectedIcon, onIconSelectorClicked = { onShowIconDialogChange(true) })

        HATextField(
            label = { Text(text = stringResource(commonR.string.label)) },
            value = state.label,
            onValueChange = callbacks.onLabelUpdated,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        BackgroundTypeSelector(state.selectedBackgroundType, callbacks.onBackgroundTypeSelected)

        if (state.selectedBackgroundType == WidgetBackgroundType.TRANSPARENT) {
            HADropdownMenu(
                items = listOf(
                    HADropdownItem(0, stringResource(commonR.string.widget_text_color_black)),
                    HADropdownItem(1, stringResource(commonR.string.widget_text_color_white)),
                ),
                selectedKey = state.textColorIndex,
                onItemSelected = callbacks.onTextColorSelected,
                label = stringResource(commonR.string.widget_text_color_title),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            HACheckbox(
                checked = state.requiresAuthentication,
                onCheckedChange = callbacks.onRequireAuthenticationChecked,
            )
            Text(text = stringResource(commonR.string.widget_checkbox_require_authentication))
        }

        val addButtonString = if (state.isUpdating == true) commonR.string.update_widget else commonR.string.add_widget
        HAAccentButton(text = stringResource(addButtonString), onClick = onAddWidgetClicked)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionTextFieldInput(
    value: String,
    onValueChange: (String) -> Unit,
    serverActions: List<Action>,
    modifier: Modifier = Modifier,
) {
    var isActionDropdownExpanded by remember { mutableStateOf(false) }
    Box {
        HATextField(
            label = { Text(text = stringResource(commonR.string.label_action)) },
            value = value,
            onValueChange = onValueChange,
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
                            onValueChange(text)
                            isActionDropdownExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicFieldInput(
    index: Int,
    field: ActionFieldBinder,
    domainEntities: List<String>,
    onDynamicFieldUpdated: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEntityDropdownExpanded by remember { mutableStateOf(false) }
    val initialValue = when (val value = field.value) {
        is List<*> -> value.joinToString(", ")
        is String -> value
        else -> ""
    }
    Box {
        HATextField(
            label = { Text(text = field.field) },
            value = initialValue,
            onValueChange = { onDynamicFieldUpdated(index, it) },
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (field.field == "entity_id") {
                        isEntityDropdownExpanded = state.hasFocus
                    }
                },
        )
        if (field.field == "entity_id" && domainEntities.isNotEmpty()) {
            DropdownMenu(
                expanded = isEntityDropdownExpanded,
                onDismissRequest = {
                    isEntityDropdownExpanded = false
                },
                properties = PopupProperties(focusable = false),
            ) {
                domainEntities.forEach { entity ->
                    DropdownMenuItem(
                        text = { Text(text = entity) },
                        onClick = {
                            val current = initialValue.trim().removeSuffix(",")
                            val newText = if (current.isEmpty()) "$entity, " else "$current, $entity, "
                            onDynamicFieldUpdated(index, newText)
                            isEntityDropdownExpanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@Composable
fun IconSelector(icon: MdiIcon, onIconSelectorClicked: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(commonR.string.label_icon))
        IconButton(
            onClick = onIconSelectorClicked,
            modifier = Modifier.size(24.dp),
        ) {
            Image(
                imageVector = icon.rememberImageVector(),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorResource(commonR.color.colorIcon)),
            )
        }
    }
}

@Composable
fun AddFieldDialog(
    action: String,
    onCancel: (() -> Unit)?,
    onOk: ((ActionFieldBinder) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var inputValue by remember { mutableStateOf("") }

    MdcAlertDialog(
        modifier = modifier,
        onDismissRequest = { },
        title = { Text(text = "Field") },
        content = {
            HATextField(
                value = inputValue,
                onValueChange = { input: String ->
                    inputValue = input
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth(),
            )
        },
        onCancel = onCancel,
        onSave = null,
        onOK = {
            val field = ActionFieldBinder(action, inputValue)
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
@Preview(name = "Light Mode")
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun ButtonWidgetConfigureScreenPreview() {
    val sampleState = ButtonWidgetViewModel.ButtonWidgetUiState(
        servers = listOf(previewServer1, previewServer2),
        selectedServerId = 0,
        dynamicFields = listOf(ActionFieldBinder("Test Action", "Test", 1)),
        selectedIcon = Mdi.Flash,
        selectedBackgroundType = WidgetBackgroundType.TRANSPARENT,
    )
    val dummyCallbacks = ButtonWidgetConfigureCallbacks(
        onActionTextUpdated = {},
        onServerSelected = {},
        onDynamicFieldUpdated = { _, _ -> },
        onAddFieldDialogOkClicked = { _, _ -> },
        onIconSelected = {},
        onLabelUpdated = {},
        onBackgroundTypeSelected = {},
        onTextColorSelected = {},
        onRequireAuthenticationChecked = {},
    )
    HATheme {
        ButtonWidgetConfigureView(
            state = sampleState,
            callbacks = dummyCallbacks,
            onAddWidgetClicked = {},
        )
    }
}
