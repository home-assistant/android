package io.homeassistant.companion.android.widgets.mediaplayer

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HAAccentButton
import io.homeassistant.companion.android.common.compose.composable.HACheckbox
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
import io.homeassistant.companion.android.common.compose.composable.HADropdownMenu
import io.homeassistant.companion.android.common.compose.composable.HATextField
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.database.server.Server
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme
import io.homeassistant.companion.android.widgets.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.common.WidgetUtils
import java.util.LinkedList
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MediaPlayerControlsWidgetConfigureActivity :
    BaseWidgetConfigureActivity<MediaPlayerControlsWidgetEntity, MediaPlayerControlsWidgetDao>() {

    companion object {
        fun newInstance(context: Context, entityId: String): Intent {
            return Intent(context, MediaPlayerControlsWidgetConfigureActivity::class.java).apply {
                putExtra(FOR_ENTITY, entityId)
                putExtra(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    private var requestLauncherSetup = false

    private var entities = mutableMapOf<Int, List<Entity>>()
    private var selectedEntities: LinkedList<Entity?> = LinkedList()

    private var serversList by mutableStateOf<List<Server>>(emptyList())
    private var composeSelectedServerId by mutableStateOf<Int?>(null)

    private var widgetLabel by mutableStateOf("")
    private var entityIdInput by mutableStateOf("")
    private var showVolume by mutableStateOf(true)
    private var showSeek by mutableStateOf(true)
    private var showSkip by mutableStateOf(true)
    private var showSource by mutableStateOf(true)
    private var backgroundType by mutableStateOf(WidgetBackgroundType.DAYNIGHT)

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            if (extras.containsKey(FOR_ENTITY)) {
                entityIdInput = extras.getString(FOR_ENTITY) ?: ""
            }
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            requestLauncherSetup = extras.getBoolean(
                ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER,
                false,
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        lifecycleScope.launch {
            serversList = serverManager.servers()

            val mediaPlayerWidget = dao.get(appWidgetId)

            if (mediaPlayerWidget != null) {
                widgetLabel = mediaPlayerWidget.label ?: ""
                entityIdInput = mediaPlayerWidget.entityId
                showVolume = mediaPlayerWidget.showVolume
                showSeek = mediaPlayerWidget.showSeek
                showSkip = mediaPlayerWidget.showSkip
                showSource = mediaPlayerWidget.showSource
                backgroundType = mediaPlayerWidget.backgroundType

                val entitiesList = try {
                    mediaPlayerWidget.entityId.split(",").map { s ->
                        serverManager.integrationRepository(mediaPlayerWidget.serverId).getEntity(s.trim())
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Unable to get entity information")
                    Toast.makeText(
                        applicationContext,
                        commonR.string.widget_entity_fetch_error,
                        Toast.LENGTH_LONG,
                    ).show()
                    null
                }
                if (entitiesList != null) {
                    selectedEntities.addAll(entitiesList)
                }
            }
            // The Compose UI (HADropdownMenu) drives server selection, so we resolve the initial
            // selection directly here instead of relying on the View-based Spinner wiring.
            selectedServerId = mediaPlayerWidget?.serverId ?: serverManager.getServer()?.id
            composeSelectedServerId = selectedServerId
        }

        lifecycleScope.launch {
            serverManager.servers().forEach { server ->
                launch {
                    try {
                        val fetchedEntities = serverManager.integrationRepository(server.id).getEntities().orEmpty()
                            .filter { it.domain == MEDIA_PLAYER_DOMAIN }
                        entities[server.id] = fetchedEntities
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to query entities")
                    }
                }
            }
        }

        setContent {
            HomeAssistantAppTheme {
                Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Text(
                            text = stringResource(commonR.string.select_entity_to_display),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(bottom = 20.dp),
                        )

                        if (serversList.size > 1) {
                            val serverItems = serversList.map { HADropdownItem(it.id, it.friendlyName) }
                            HADropdownMenu(
                                items = serverItems,
                                selectedKey = composeSelectedServerId,
                                onItemSelected = {
                                    composeSelectedServerId = it
                                    selectedServerId = it
                                    onServerSelected(it)
                                },
                                label = stringResource(commonR.string.widget_spinner_server),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                            )
                        }

                        HATextField(
                            value = entityIdInput,
                            onValueChange = { entityIdInput = it },
                            label = { Text(stringResource(commonR.string.label_entity_ids)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                        )

                        LabeledCheckbox(
                            checked = showVolume,
                            onCheckedChange = { showVolume = it },
                            label = stringResource(commonR.string.widget_media_show_volume),
                        )

                        LabeledCheckbox(
                            checked = showSkip,
                            onCheckedChange = { showSkip = it },
                            label = stringResource(commonR.string.widget_media_show_skip),
                        )

                        LabeledCheckbox(
                            checked = showSeek,
                            onCheckedChange = { showSeek = it },
                            label = stringResource(commonR.string.widget_media_show_seek),
                        )

                        LabeledCheckbox(
                            checked = showSource,
                            onCheckedChange = { showSource = it },
                            label = stringResource(commonR.string.widget_media_show_source),
                            modifier = Modifier.padding(bottom = 16.dp),
                        )

                        HATextField(
                            value = widgetLabel,
                            onValueChange = { widgetLabel = it },
                            label = { Text(stringResource(commonR.string.label_label)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                        )

                        val backgroundItems = remember {
                            WidgetUtils.getBackgroundOptionList(this@MediaPlayerControlsWidgetConfigureActivity)
                                .map { HADropdownItem(it, it) }
                        }

                        val dynamicColorLabel = getString(commonR.string.widget_background_type_dynamiccolor)
                        val transparentLabel = getString(commonR.string.widget_background_type_transparent)
                        val dayNightLabel = getString(commonR.string.widget_background_type_daynight)

                        val backgroundKey = when (backgroundType) {
                            WidgetBackgroundType.DYNAMICCOLOR -> dynamicColorLabel
                            WidgetBackgroundType.TRANSPARENT -> transparentLabel
                            else -> dayNightLabel
                        }

                        HADropdownMenu(
                            items = backgroundItems,
                            selectedKey = backgroundKey,
                            onItemSelected = { selection ->
                                backgroundType = when (selection) {
                                    dynamicColorLabel -> WidgetBackgroundType.DYNAMICCOLOR
                                    transparentLabel -> WidgetBackgroundType.TRANSPARENT
                                    else -> WidgetBackgroundType.DAYNIGHT
                                }
                            },
                            label = stringResource(commonR.string.widget_background_type_label),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                        )

                        HAAccentButton(
                            text = stringResource(
                                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
                                    !requestLauncherSetup
                                ) {
                                    commonR.string.update_widget
                                } else {
                                    commonR.string.add_widget
                                },
                            ),
                            onClick = { onAddWidgetClicked() },
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun LabeledCheckbox(
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        label: String,
        modifier: Modifier = Modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                )
                .padding(vertical = 8.dp),
        ) {
            Text(text = label)
            Spacer(Modifier.weight(1f))
            HACheckbox(
                checked = checked,
                onCheckedChange = null, // we handle click on row
            )
        }
    }

    private fun onAddWidgetClicked() {
        lifecycleScope.launch {
            if (requestLauncherSetup) {
                if (
                    SdkVersion.isAtLeast(Build.VERSION_CODES.O) &&
                    isValidServerId() &&
                    entityIdInput.split(",").any {
                        entities[selectedServerId!!].orEmpty().any { e -> e.entityId == it.trim() }
                    }
                ) {
                    requestWidgetCreation()
                } else {
                    showAddWidgetError()
                }
            } else {
                updateWidget()
            }
        }
    }

    override fun onServerSelected(serverId: Int) {
        selectedEntities.clear()
        entityIdInput = ""
        composeSelectedServerId = serverId
    }

    override suspend fun getPendingDaoEntity(): MediaPlayerControlsWidgetEntity {
        val serverId = checkNotNull(selectedServerId) { "Selected server ID is null" }
        selectedEntities = LinkedList()
        val se = entityIdInput.split(",")
        se.forEach {
            val entity = entities[serverId]?.firstOrNull { e -> e.entityId == it.trim() }
            if (entity != null) selectedEntities.add(entity)
        }

        val entitySelection = selectedEntities.map { e -> e?.entityId }.reduceOrNull { a, b -> "$a,$b" }

        if (entitySelection == null) {
            throw IllegalStateException("No valid entities selected")
        }

        return MediaPlayerControlsWidgetEntity(
            id = appWidgetId,
            serverId = serverId,
            entityId = entitySelection,
            label = widgetLabel,
            showVolume = showVolume,
            showSkip = showSkip,
            showSeek = showSeek,
            showSource = showSource,
            backgroundType = backgroundType,
        )
    }

    override val widgetClass: Class<*> = MediaPlayerControlsWidget::class.java
}
