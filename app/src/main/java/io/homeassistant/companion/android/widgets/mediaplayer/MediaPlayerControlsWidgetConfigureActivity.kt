package io.homeassistant.companion.android.widgets.mediaplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.MultiAutoCompleteTextView
import android.widget.Spinner
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.databinding.WidgetMediaControlsConfigureBinding
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.widgets.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.common.SingleItemArrayAdapter
import io.homeassistant.companion.android.widgets.common.WidgetUtils
import java.util.LinkedList
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MediaPlayerControlsWidgetConfigureActivity : BaseWidgetConfigureActivity() {

    companion object {
        private const val TAG: String = "MediaWidgetConfigAct"
        private const val PIN_WIDGET_CALLBACK = "io.homeassistant.companion.android.widgets.media_player_controls.MediaPlayerControlsWidgetConfigureActivity.PIN_WIDGET_CALLBACK"
    }

    private var requestLauncherSetup = false

    @Inject
    lateinit var mediaPlayerControlsWidgetDao: MediaPlayerControlsWidgetDao
    override val dao get() = mediaPlayerControlsWidgetDao

    private lateinit var binding: WidgetMediaControlsConfigureBinding

    override val serverSelect: View
        get() = binding.serverSelect

    override val serverSelectList: Spinner
        get() = binding.serverSelectList

    private var entities = mutableMapOf<Int, List<Entity<Any>>>()
    private var selectedEntities: LinkedList<Entity<*>?> = LinkedList()

    private var entityAdapter: SingleItemArrayAdapter<Entity<Any>>? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = WidgetMediaControlsConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addButton.setOnClickListener {
            if (requestLauncherSetup) {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    isValidServerId() &&
                    binding.widgetTextConfigEntityId.text.split(",").any {
                        entities[selectedServerId!!].orEmpty().any { e -> e.entityId == it.trim() }
                    }
                ) {
                    getSystemService<AppWidgetManager>()?.requestPinAppWidget(
                        ComponentName(this, MediaPlayerControlsWidget::class.java),
                        null,
                        PendingIntent.getActivity(
                            this,
                            System.currentTimeMillis().toInt(),
                            Intent(this, MediaPlayerControlsWidgetConfigureActivity::class.java).putExtra(PIN_WIDGET_CALLBACK, true).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )
                    )
                } else {
                    showAddWidgetError()
                }
            } else {
                onAddWidget()
            }
        }

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

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        val mediaPlayerWidget = mediaPlayerControlsWidgetDao.get(appWidgetId)

        val backgroundTypeValues = WidgetUtils.getBackgroundOptionList(this)
        binding.backgroundType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, backgroundTypeValues)
        if (mediaPlayerWidget != null) {
            binding.label.setText(mediaPlayerWidget.label)
            binding.widgetTextConfigEntityId.setText(mediaPlayerWidget.entityId)
            binding.widgetShowVolumeButtonCheckbox.isChecked = mediaPlayerWidget.showVolume
            binding.widgetShowSeekButtonsCheckbox.isChecked = mediaPlayerWidget.showSeek
            binding.widgetShowSkipButtonsCheckbox.isChecked = mediaPlayerWidget.showSkip
            binding.widgetShowMediaPlayerSource.isChecked = mediaPlayerWidget.showSource
            binding.backgroundType.setSelection(
                WidgetUtils.getSelectedBackgroundOption(
                    this,
                    mediaPlayerWidget.backgroundType,
                    backgroundTypeValues
                )
            )
            val entities = runBlocking {
                try {
                    mediaPlayerWidget.entityId.split(",").map { s ->
                        serverManager.integrationRepository(mediaPlayerWidget.serverId).getEntity(s.trim())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to get entity information", e)
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
        entityAdapter = SingleItemArrayAdapter(this) { it?.entityId ?: "" }

        setupServerSelect(mediaPlayerWidget?.serverId)

        binding.widgetTextConfigEntityId.setAdapter(entityAdapter)
        binding.widgetTextConfigEntityId.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
        binding.widgetTextConfigEntityId.onFocusChangeListener = dropDownOnFocus

        serverManager.defaultServers.forEach { server ->
            lifecycleScope.launch {
                try {
                    val fetchedEntities = serverManager.integrationRepository(server.id).getEntities().orEmpty()
                        .filter { it.domain == "media_player" }
                    entities[server.id] = fetchedEntities
                    if (server.id == selectedServerId) setAdapterEntities(server.id)
                } catch (e: Exception) {
                    // If entities fail to load, it's okay to pass
                    // an empty map to the dynamicFieldAdapter
                    Log.e(TAG, "Failed to query entities", e)
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

    private fun onAddWidget() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            showAddWidgetError()
            return
        }
        try {
            val context = this@MediaPlayerControlsWidgetConfigureActivity

            // Set up a broadcast intent and pass the service call data as extras
            val intent = Intent()
            intent.action = MediaPlayerControlsWidget.RECEIVE_DATA
            intent.component = ComponentName(context, MediaPlayerControlsWidget::class.java)

            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

            intent.putExtra(
                MediaPlayerControlsWidget.EXTRA_SERVER_ID,
                selectedServerId!!
            )

            selectedEntities = LinkedList()
            val se = binding.widgetTextConfigEntityId.text.split(",")
            se.forEach {
                val entity = entities[selectedServerId]!!.firstOrNull { e -> e.entityId == it.trim() }
                if (entity != null) selectedEntities.add(entity)
            }
            intent.putExtra(
                MediaPlayerControlsWidget.EXTRA_ENTITY_ID,
                selectedEntities.map { e -> e?.entityId }.reduce { a, b -> "$a,$b" }
            )
            intent.putExtra(
                MediaPlayerControlsWidget.EXTRA_LABEL,
                binding.label.text.toString()
            )
            intent.putExtra(
                MediaPlayerControlsWidget.EXTRA_SHOW_VOLUME,
                binding.widgetShowVolumeButtonCheckbox.isChecked
            )
            intent.putExtra(
                MediaPlayerControlsWidget.EXTRA_SHOW_SKIP,
                binding.widgetShowSkipButtonsCheckbox.isChecked
            )
            intent.putExtra(
                MediaPlayerControlsWidget.EXTRA_SHOW_SEEK,
                binding.widgetShowSeekButtonsCheckbox.isChecked
            )
            intent.putExtra(
                MediaPlayerControlsWidget.EXTRA_SHOW_SOURCE,
                binding.widgetShowMediaPlayerSource.isChecked
            )
            intent.putExtra(
                MediaPlayerControlsWidget.EXTRA_BACKGROUND_TYPE,
                when (binding.backgroundType.selectedItem as String?) {
                    getString(commonR.string.widget_background_type_dynamiccolor) -> WidgetBackgroundType.DYNAMICCOLOR
                    getString(commonR.string.widget_background_type_transparent) -> WidgetBackgroundType.TRANSPARENT
                    else -> WidgetBackgroundType.DAYNIGHT
                }
            )

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.extras != null && intent.hasExtra(PIN_WIDGET_CALLBACK)) {
            appWidgetId = intent.extras!!.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            onAddWidget()
        }
    }
}
