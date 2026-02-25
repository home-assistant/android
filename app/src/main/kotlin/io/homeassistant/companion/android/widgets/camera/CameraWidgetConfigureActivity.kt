package io.homeassistant.companion.android.widgets.camera

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import io.homeassistant.companion.android.common.R as commonR
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.common.compose.composable.HATopBar
import io.homeassistant.companion.android.common.compose.theme.HATheme
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.websocket.impl.entities.AreaRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.DeviceRegistryResponse
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.compose.HAExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat
import io.homeassistant.companion.android.util.compose.ServerExposedDropdownMenu
import io.homeassistant.companion.android.util.compose.entity.EntityPicker
import io.homeassistant.companion.android.util.safeBottomWindowInsets
import kotlin.collections.emptyList
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CameraWidgetConfigureActivity : BaseActivity() {

    companion object {
        private const val FOR_ENTITY = "for_entity"

        fun newInstance(context: Context, entityId: String): Intent {
            return Intent(context, CameraWidgetConfigureActivity::class.java).apply {
                putExtra(FOR_ENTITY, entityId)
                putExtra(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    private val viewModel: CameraWidgetConfigureViewModel by viewModels(
        extrasProducer = {
            defaultViewModelCreationExtras.withCreationCallback<CameraWidgetConfigureViewModel.Factory> {  factory ->
                factory.create(intent.extras?.getString(FOR_ENTITY, null))
            }
        },
    )

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        viewModel.onSetup(widgetId)

        setContent {
            HATheme {
                CameraWidgetConfigureScreen(
                    viewModel = viewModel,
                    onActionClick = { onActionClick() }
                )
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun onActionClick() {
        lifecycleScope.launch {
            if(intent.extras?.getBoolean(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, false) ?: false) {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && viewModel.isValidSelection()) {
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
        val context = this@CameraWidgetConfigureActivity
        lifecycleScope.launch {
            viewModel.requestWidgetCreation(context)
            finish()
        }
    }

    private suspend fun onUpdateWidget() {
        try {
            viewModel.updateWidgetConfiguration()
            setResult(RESULT_OK)
            viewModel.updateWidget(this@CameraWidgetConfigureActivity)
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
private fun CameraWidgetConfigureScreen(viewModel: CameraWidgetConfigureViewModel, onActionClick: () -> Unit) {
    val servers by viewModel.servers.collectAsStateWithLifecycle(emptyList())
    val entities by viewModel.entities.collectAsStateWithLifecycle()
    val entityRegistry by viewModel.entityRegistry.collectAsStateWithLifecycle()
    val deviceRegistry by viewModel.deviceRegistry.collectAsStateWithLifecycle()
    val areaRegistry by viewModel.areaRegistry.collectAsStateWithLifecycle()

    CameraWidgetConfigureView(
        servers = servers,
        selectedServerId = viewModel.selectedServerId,
        onServerSelected = viewModel::setServer,
        entities = entities,
        selectedEntityId = viewModel.selectedEntityId,
        onEntitySelected = { viewModel.selectedEntityId = it },
        isUpdateWidget = viewModel.isUpdateWidget,
        onTapSelected = { viewModel.selectedTapAction = WidgetTapAction.entries[it] },
        entityRegistry = entityRegistry,
        deviceRegistry = deviceRegistry,
        areaRegistry = areaRegistry,
        onActionClick = onActionClick,
        selectedTapAction = viewModel.selectedTapAction
    )

}

@Composable
private fun CameraWidgetConfigureView(
    servers: List<Server>,
    selectedServerId: Int,
    onServerSelected: (Int) -> Unit,
    entities: List<Entity>,
    selectedEntityId: String?,
    onEntitySelected: (String?) -> Unit,
    isUpdateWidget: Boolean,
    onTapSelected: (Int) -> Unit,
    entityRegistry: List<EntityRegistryResponse>? = null,
    deviceRegistry: List<DeviceRegistryResponse>? = null,
    areaRegistry: List<AreaRegistryResponse>? = null,
    onActionClick: () -> Unit,
    selectedTapAction: WidgetTapAction? = null,
) {
    Scaffold(
        topBar = {
            HATopBar(
                title = { Text(stringResource(commonR.string.widget_camera_description)) },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing
    )  {padding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(safeBottomWindowInsets())
                .padding(padding)
                .padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if(servers.size > 1) {
                HomeAssistantAppTheme {
                    ServerExposedDropdownMenu(
                        servers = servers,
                        current = selectedServerId,
                        onSelected = onServerSelected,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }

            EntityPicker(
                entities = entities,
                selectedEntityId = selectedEntityId,
                onEntitySelectedId = { onEntitySelected(it) },
                onEntityCleared = { onEntitySelected(null) },
                entityRegistry = entityRegistry,
                deviceRegistry = deviceRegistry,
                areaRegistry = areaRegistry,
                addButtonText = stringResource(commonR.string.add_to_camera_widget),
            )

            HAExposedDropdownMenu(
                label = stringResource(commonR.string.widget_tap_action_label),
                keys = listOf(
                    stringResource(commonR.string.refresh),
                    stringResource(commonR.string.widget_tap_action_open)
                ),
                currentIndex = selectedTapAction?.ordinal ?: 0,
                onSelected =  { onTapSelected(it) },
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onActionClick() },
            ) {
                Text(stringResource(
                if(isUpdateWidget) commonR.string.update_widget else commonR.string.add_widget
                ))
            }
        }
    }
}
