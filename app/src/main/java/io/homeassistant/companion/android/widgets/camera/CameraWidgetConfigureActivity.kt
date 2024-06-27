package io.homeassistant.companion.android.widgets.camera

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.database.widget.CameraWidgetDao
import io.homeassistant.companion.android.database.widget.CameraWidgetEntity
import io.homeassistant.companion.android.database.widget.CameraWidgetTapAction
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.widgets.BaseWidgetConfigureNoUiActivity
import io.homeassistant.companion.android.widgets.camera.compose.CameraWidgetConfigureScreenUi
import io.homeassistant.companion.android.widgets.camera.compose.CameraWidgetConfigureScreenUiState
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CameraWidgetConfigureActivity : BaseWidgetConfigureNoUiActivity() {

    companion object {
        private const val TAG: String = "CameraWidgetConfigAct"
        private const val PIN_WIDGET_CALLBACK = "io.homeassistant.companion.android.widgets.camera.CameraWidgetConfigureActivity.PIN_WIDGET_CALLBACK"
    }

    private var requestLauncherSetup = false

    private var entities = mutableMapOf<Int, List<Entity<Any>>>()

    @Inject
    lateinit var cameraWidgetDao: CameraWidgetDao
    override val dao get() = cameraWidgetDao

    private var state by mutableStateOf(CameraWidgetConfigureScreenUiState())

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fetchEntitiesForServers()

        setContent {
            HomeAssistantAppTheme {
                CameraWidgetConfigureScreenUi(state = state)
            }
        }
        setupListeners()

        parseIntentArgs()
        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        val cameraWidget = cameraWidgetDao.get(appWidgetId)
        cameraWidget?.let { validateCameraWidgetData(cameraWidget) }
        state = state.copy(
            isNewWidget = cameraWidget == null,
            selectedEntityId = cameraWidget?.entityId,
            tapAction = cameraWidget?.tapAction ?: CameraWidgetTapAction.UPDATE_IMAGE
        )

        onServerIdProvided(cameraWidget?.serverId)
    }

    override fun onServerChanged(serverId: Int) {
        state = state.copy(selectedEntityId = null)
        showServerEntities(serverId)
    }

    override fun onServerPickerDataReceived(serverNames: List<String>, activeServerPosition: Int) {
        state = state.copy(serverNames = serverNames, selectedServerPosition = activeServerPosition)
    }

    override fun onServerPickerVisible(isShow: Boolean) {
        state = state.copy(isServerPickerVisible = isShow)
    }

    private fun setupListeners() {
        state = state.copy(
            onEntityChange = {
                state = state.copy(selectedEntityId = it)
            },
            onServerSelect = {
                state = state.copy(selectedServerPosition = it)
                selectItem(it)
            },
            onTapActionSelect = {
                state = state.copy(tapAction = it)
            },
            onApplyChangesClick = {
                if (requestLauncherSetup) {
                    installWidget()
                } else {
                    updateWidget()
                }
            }
        )
    }

    private fun fetchEntitiesForServers() = lifecycleScope.launch {
        val serverJobs = serverManager.defaultServers.map { server ->
            async {
                try {
                    val fetchedEntities = serverManager.integrationRepository(server.id).getEntities().orEmpty()
                        .filter { it.domain == "camera" || it.domain == "image" }
                    entities[server.id] = fetchedEntities.sortedBy { it.entityId }
                    if (server.id == selectedServerId) {
                        showServerEntities(server.id)
                    }

                    Unit
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to query entities for server: ${server.id}", e)
                }
            }
        }

        serverJobs.awaitAll()
    }

    private fun showServerEntities(serverId: Int) {
        val availableEntities = entities[serverId].orEmpty()
        state = state.copy(entities = availableEntities)
    }

    private fun updateWidget() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            showAddWidgetError()
            return
        }
        try {
            val context = this@CameraWidgetConfigureActivity

            // Set up a broadcast intent and pass the service call data as extras
            val intent = Intent()
                .apply {
                    action = CameraWidget.RECEIVE_DATA
                    component = ComponentName(context, CameraWidget::class.java)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(CameraWidget.EXTRA_SERVER_ID, selectedServerId!!)
                    putExtra(CameraWidget.EXTRA_ENTITY_ID, state.selectedEntityId!!)
                    putExtra(CameraWidget.EXTRA_TAP_ACTION_ORDINAL, state.tapAction.ordinal)
                }

            context.sendBroadcast(intent)

            // Make sure we pass back the original appWidgetId
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Issue configuring widget", e)
            showAddWidgetError()
        }
    }

    private fun validateCameraWidgetData(cameraWidget: CameraWidgetEntity) = lifecycleScope.launch {
        try {
            serverManager.integrationRepository(cameraWidget.serverId).getEntity(cameraWidget.entityId)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to get entity information", e)
            Toast.makeText(applicationContext, commonR.string.widget_entity_fetch_error, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun installWidget() {
        val isAtLeastOreo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val canPinWidget =
            isAtLeastOreo && isValidServerId() && state.selectedEntityId != null

        if (canPinWidget) {
            pinCameraWidget()
        } else {
            showAddWidgetError()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pinCameraWidget() {
        val appWidgetManager = getSystemService<AppWidgetManager>()

        if (appWidgetManager?.isRequestPinAppWidgetSupported == true) {
            val targetWidgetComponentName = ComponentName(this, CameraWidget::class.java)
            val onSuccess = PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                Intent(this, CameraWidgetConfigureActivity::class.java).putExtra(PIN_WIDGET_CALLBACK, true).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            appWidgetManager.requestPinAppWidget(targetWidgetComponentName, null, onSuccess)
        } else {
            showAddWidgetError()
        }
    }

    private fun parseIntentArgs() {
        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            requestLauncherSetup = extras.getBoolean(
                ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER,
                false
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.extras != null && intent.hasExtra(PIN_WIDGET_CALLBACK)) {
            appWidgetId = intent.extras!!.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            updateWidget()
        }
    }
}
