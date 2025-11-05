package io.homeassistant.companion.android.widgets.mediaplayer

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.MultiAutoCompleteTextView
import android.widget.Spinner
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.MEDIA_PLAYER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.databinding.WidgetMediaControlsConfigureBinding
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.applySafeDrawingInsets
import io.homeassistant.companion.android.widgets.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.common.SingleItemArrayAdapter
import io.homeassistant.companion.android.widgets.common.WidgetUtils
import java.util.LinkedList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    private lateinit var binding: WidgetMediaControlsConfigureBinding

    override val serverSelect: View
        get() = binding.serverSelect

    override val serverSelectList: Spinner
        get() = binding.serverSelectList

    private var entities = mutableMapOf<Int, List<Entity>>()
    private var selectedEntities: LinkedList<Entity?> = LinkedList()

    private var entityAdapter: SingleItemArrayAdapter<Entity>? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = WidgetMediaControlsConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySafeDrawingInsets()

        binding.addButton.setOnClickListener {
            if (requestLauncherSetup) {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    isValidServerId() &&
                    binding.widgetTextConfigEntityId.text.split(",").any {
                        entities[selectedServerId!!].orEmpty().any { e -> e.entityId == it.trim() }
                    }
                ) {
                    lifecycleScope.launch {
                        requestWidgetCreation()
                    }
                } else {
                    showAddWidgetError()
                }
            } else {
                lifecycleScope.launch {
                    updateWidget()
                }
            }
        }

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            if (extras.containsKey(FOR_ENTITY)) {
                binding.widgetTextConfigEntityId.setText(extras.getString(FOR_ENTITY))
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

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        val backgroundTypeValues = WidgetUtils.getBackgroundOptionList(this)
        binding.backgroundType.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, backgroundTypeValues)

        lifecycleScope.launch {
            val mediaPlayerWidget = dao.get(appWidgetId)

            if (mediaPlayerWidget != null) {
                binding.label.setText(mediaPlayerWidget.label)
                binding.widgetTextConfigEntityId.setText(mediaPlayerWidget.entityId)
                binding.widgetShowVolumeButtonCheckbox.isChecked = mediaPlayerWidget.showVolume
                binding.widgetShowSeekButtonsCheckbox.isChecked = mediaPlayerWidget.showSeek
                binding.widgetShowSkipButtonsCheckbox.isChecked = mediaPlayerWidget.showSkip
                binding.widgetShowMediaPlayerSource.isChecked = mediaPlayerWidget.showSource
                binding.backgroundType.setSelection(
                    WidgetUtils.getSelectedBackgroundOption(
                        this@MediaPlayerControlsWidgetConfigureActivity,
                        mediaPlayerWidget.backgroundType,
                        backgroundTypeValues,
                    ),
                )
                val entities = runBlocking {
                    try {
                        mediaPlayerWidget.entityId.split(",").map { s ->
                            serverManager.integrationRepository(mediaPlayerWidget.serverId).getEntity(s.trim())
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Unable to get entity information")
                        Toast.makeText(applicationContext, commonR.string.widget_entity_fetch_error, Toast.LENGTH_LONG)
                            .show()
                        null
                    }
                }
                if (entities != null) {
                    selectedEntities.addAll(entities)
                }
                binding.addButton.setText(commonR.string.update_widget)
            }
            setupServerSelect(mediaPlayerWidget?.serverId)
        }

        entityAdapter = SingleItemArrayAdapter(this) { it?.entityId ?: "" }

        binding.widgetTextConfigEntityId.setAdapter(entityAdapter)
        binding.widgetTextConfigEntityId.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
        binding.widgetTextConfigEntityId.onFocusChangeListener = dropDownOnFocus

        serverManager.defaultServers.forEach { server ->
            lifecycleScope.launch {
                try {
                    val fetchedEntities = serverManager.integrationRepository(server.id).getEntities().orEmpty()
                        .filter { it.domain == MEDIA_PLAYER_DOMAIN }
                    entities[server.id] = fetchedEntities
                    if (server.id == selectedServerId) setAdapterEntities(server.id)
                } catch (e: Exception) {
                    // If entities fail to load, it's okay to pass
                    // an empty map to the dynamicFieldAdapter
                    Timber.e(e, "Failed to query entities")
                }
            }
        }
    }

    private val dropDownOnFocus = View.OnFocusChangeListener { view, hasFocus ->
        if (hasFocus && view is AutoCompleteTextView) {
            view.showDropDown()
        }
    }

    override fun onServerSelected(serverId: Int) {
        selectedEntities.clear()
        binding.widgetTextConfigEntityId.setText("")
        setAdapterEntities(serverId)
    }

    private fun setAdapterEntities(serverId: Int) {
        entityAdapter?.let { adapter ->
            adapter.clearAll()
            if (entities[serverId] != null) {
                adapter.addAll(entities[serverId].orEmpty().toMutableList())
                adapter.sort()
            }
            runOnUiThread { adapter.notifyDataSetChanged() }
        }
    }

    override suspend fun getPendingDaoEntity(): MediaPlayerControlsWidgetEntity {
        val serverId = checkNotNull(selectedServerId) { "Selected server ID is null" }
        selectedEntities = LinkedList()
        val se = binding.widgetTextConfigEntityId.text.split(",")
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
            label = binding.label.text.toString(),
            showVolume = binding.widgetShowVolumeButtonCheckbox.isChecked,
            showSkip = binding.widgetShowSkipButtonsCheckbox.isChecked,
            showSeek = binding.widgetShowSeekButtonsCheckbox.isChecked,
            showSource = binding.widgetShowMediaPlayerSource.isChecked,
            backgroundType = when (binding.backgroundType.selectedItem as String?) {
                getString(commonR.string.widget_background_type_dynamiccolor) -> WidgetBackgroundType.DYNAMICCOLOR
                getString(commonR.string.widget_background_type_transparent) -> WidgetBackgroundType.TRANSPARENT
                else -> WidgetBackgroundType.DAYNIGHT
            },
        )
    }

    override val widgetClass: Class<*> = MediaPlayerControlsWidget::class.java
}
