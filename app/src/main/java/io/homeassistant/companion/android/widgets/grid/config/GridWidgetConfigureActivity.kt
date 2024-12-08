package io.homeassistant.companion.android.widgets.grid.config

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Spinner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.database.widget.GridWidgetDao
import io.homeassistant.companion.android.databinding.WidgetGridConfigureBinding
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.widgets.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.grid.GridWidget
import io.homeassistant.companion.android.widgets.grid.asGridConfiguration
import javax.inject.Inject

@AndroidEntryPoint
class GridWidgetConfigureActivity : BaseWidgetConfigureActivity() {
    private lateinit var binding: WidgetGridConfigureBinding

    @Inject
    lateinit var gridWidgetDao: GridWidgetDao
    override val dao get() = gridWidgetDao

    override val serverSelect: View
        get() = binding.serverSelect

    override val serverSelectList: Spinner
        get() = binding.serverSelectList

    override fun onServerSelected(serverId: Int) {
        // TODO: Populate list of actions
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = WidgetGridConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        val requestLauncherSetup = extras?.getBoolean(
            ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER,
            false
        ) == true

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        val gridWidget = gridWidgetDao.get(appWidgetId)

        setupServerSelect(gridWidget?.gridWidget?.serverId)

        binding.widgetConfiguration.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HomeAssistantAppTheme {
                    var config by remember { mutableStateOf(gridWidget?.asGridConfiguration() ?: GridConfiguration()) }
                    GridConfiguration(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxSize(),
                        config = config,
                        onConfigure = ::onConfigure,
                        onNameChange = { config = config.copy(label = it) },
                        onRequireAuthenticationChange = { config = config.copy(requireAuthentication = it) },
                        onItemAdd = { item -> config = config.let { it.copy(items = it.items + item) } },
                        onItemEdit = { pos, item -> config = config.let { it.copy(items = it.items.mapIndexed { i, it -> if (i == pos) item else it }) } },
                        onItemDelete = { pos -> config = config.let { it.copy(items = it.items.filterIndexed { it, _ -> it != pos }) } }
                    )
                }
            }
        }
    }

    private fun onConfigure(config: GridConfiguration) {
        val config = config.copy(serverId = selectedServerId)

        val intent = Intent().apply {
            action = BaseWidgetProvider.RECEIVE_DATA
            component = ComponentName(applicationContext, GridWidget::class.java)

            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(GridWidget.EXTRA_CONFIG, config)
        }

        sendBroadcast(intent)

        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, result)
        finish()
    }
}
