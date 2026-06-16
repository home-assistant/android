package io.homeassistant.companion.android.widgets.mediaplayer

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.compose.composable.HADropdownItem
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

                MediaPlayerControlsWidgetConfigureScreen(
                    servers = serversList.map { HADropdownItem(it.id, it.friendlyName) },
                    selectedServerId = composeSelectedServerId,
                    onServerSelected = {
                        composeSelectedServerId = it
                        selectedServerId = it
                        onServerSelected(it)
                    },
                    entityId = entityIdInput,
                    onEntityIdChange = { entityIdInput = it },
                    showVolume = showVolume,
                    onShowVolumeChange = { showVolume = it },
                    showSkip = showSkip,
                    onShowSkipChange = { showSkip = it },
                    showSeek = showSeek,
                    onShowSeekChange = { showSeek = it },
                    showSource = showSource,
                    onShowSourceChange = { showSource = it },
                    widgetLabel = widgetLabel,
                    onWidgetLabelChange = { widgetLabel = it },
                    backgroundOptions = backgroundItems,
                    selectedBackgroundKey = backgroundKey,
                    onBackgroundSelected = { selection ->
                        backgroundType = when (selection) {
                            dynamicColorLabel -> WidgetBackgroundType.DYNAMICCOLOR
                            transparentLabel -> WidgetBackgroundType.TRANSPARENT
                            else -> WidgetBackgroundType.DAYNIGHT
                        }
                    },
                    isUpdate = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup,
                    onAddWidgetClick = { onAddWidgetClicked() },
                )
            }
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
