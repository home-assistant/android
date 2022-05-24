package io.homeassistant.companion.android.widgets.media_player_controls

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.MediaPlayerControlsWidgetDao
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.databinding.WidgetMediaControlsConfigureBinding
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.widgets.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.common.SingleItemArrayAdapter
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class MediaPlayerControlsWidgetConfigureActivity : BaseWidgetConfigureActivity() {

    companion object {
        private const val TAG: String = "MediaWidgetConfigAct"
        private const val PIN_WIDGET_CALLBACK = "io.homeassistant.companion.android.widgets.media_player_controls.MediaPlayerControlsWidgetConfigureActivity.PIN_WIDGET_CALLBACK"
    }

    private var requestLauncherSetup = false

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private lateinit var mediaPlayerControlsWidgetDao: MediaPlayerControlsWidgetDao
    override val dao get() = mediaPlayerControlsWidgetDao

    private lateinit var binding: WidgetMediaControlsConfigureBinding

    private var entities = LinkedHashMap<String, Entity<Any>>()
    private var selectedEntity: Entity<Any>? = null

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = WidgetMediaControlsConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addButton.setOnClickListener {
            if (requestLauncherSetup) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && selectedEntity != null) {
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
                } else showAddWidgetError()
            } else {
                onAddWidget()
            }
        }

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
            requestLauncherSetup = extras.getBoolean(
                ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, false
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !requestLauncherSetup) {
            finish()
            return
        }

        mediaPlayerControlsWidgetDao = AppDatabase.getInstance(applicationContext).mediaPlayCtrlWidgetDao()
        val mediaPlayerWidget = mediaPlayerControlsWidgetDao.get(appWidgetId)

        val backgroundTypeValues = mutableListOf(
            getString(commonR.string.widget_background_type_dynamiccolor),
            getString(commonR.string.widget_background_type_daynight)
        )
        if (DynamicColors.isDynamicColorAvailable()) {
            binding.backgroundType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, backgroundTypeValues)
            binding.backgroundType.setSelection(0)
            binding.backgroundTypeParent.visibility = View.VISIBLE
        } else {
            binding.backgroundTypeParent.visibility = View.GONE
        }

        if (mediaPlayerWidget != null) {
            binding.label.setText(mediaPlayerWidget.label)
            binding.widgetTextConfigEntityId.setText(mediaPlayerWidget.entityId)
            binding.widgetShowVolumeButtonCheckbox.isChecked = mediaPlayerWidget.showVolume
            binding.widgetShowSeekButtonsCheckbox.isChecked = mediaPlayerWidget.showSeek
            binding.widgetShowSkipButtonsCheckbox.isChecked = mediaPlayerWidget.showSkip
            val entity = runBlocking {
                try {
                    integrationUseCase.getEntity(mediaPlayerWidget.entityId)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to get entity information", e)
                    Toast.makeText(applicationContext, commonR.string.widget_entity_fetch_error, Toast.LENGTH_LONG)
                        .show()
                    null
                }
            }
            binding.backgroundType.setSelection(
                when {
                    mediaPlayerWidget.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable() ->
                        backgroundTypeValues.indexOf(getString(commonR.string.widget_background_type_dynamiccolor))
                    else ->
                        backgroundTypeValues.indexOf(getString(commonR.string.widget_background_type_daynight))
                }
            )
            if (entity != null)
                selectedEntity = entity as Entity<Any>?
            binding.addButton.setText(commonR.string.update_widget)
            binding.deleteButton.visibility = View.VISIBLE
            binding.deleteButton.setOnClickListener(onDeleteWidget)
        }
        val entityAdapter = SingleItemArrayAdapter<Entity<Any>>(this) { it?.entityId ?: "" }

        binding.widgetTextConfigEntityId.setAdapter(entityAdapter)
        binding.widgetTextConfigEntityId.onFocusChangeListener = dropDownOnFocus
        binding.widgetTextConfigEntityId.onItemClickListener = entityDropDownOnItemClick

        lifecycleScope.launch {
            try {
                // Fetch entities
                val fetchedEntities = integrationUseCase.getEntities()
                fetchedEntities?.forEach {
                    if (it.domain == "media_player") {
                        entities[it.entityId] = it
                    }
                }
                entityAdapter.addAll(entities.values)
                entityAdapter.sort()

                runOnUiThread {
                    entityAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                // If entities fail to load, it's okay to pass
                // an empty map to the dynamicFieldAdapter
                Log.e(TAG, "Failed to query entities", e)
            }
        }
    }

    private val dropDownOnFocus = View.OnFocusChangeListener { view, hasFocus ->
        if (hasFocus && view is AutoCompleteTextView) {
            view.showDropDown()
        }
    }

    private val entityDropDownOnItemClick =
        AdapterView.OnItemClickListener { parent, _, position, _ ->
            selectedEntity = parent.getItemAtPosition(position) as Entity<Any>?
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
                MediaPlayerControlsWidget.EXTRA_ENTITY_ID,
                selectedEntity!!.entityId
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
                MediaPlayerControlsWidget.EXTRA_BACKGROUND_TYPE,
                when (binding.backgroundType.selectedItem as String?) {
                    getString(commonR.string.widget_background_type_dynamiccolor) -> WidgetBackgroundType.DYNAMICCOLOR
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null && intent.extras != null && intent.hasExtra(PIN_WIDGET_CALLBACK)) {
            appWidgetId = intent.extras!!.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
            onAddWidget()
        }
    }
}
