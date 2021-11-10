package io.homeassistant.companion.android.widgets.media_player_controls

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AutoCompleteTextView
import android.widget.Toast
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.databinding.WidgetMediaControlsConfigureBinding
import io.homeassistant.companion.android.widgets.DaggerProviderComponent
import io.homeassistant.companion.android.widgets.common.SingleItemArrayAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class MediaPlayerControlsWidgetConfigureActivity : BaseActivity() {

    companion object {
        private const val TAG: String = "MediaWidgetConfigAct"
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private lateinit var binding: WidgetMediaControlsConfigureBinding

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var entities = LinkedHashMap<String, Entity<Any>>()
    private var selectedEntity: Entity<Any>? = null

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = WidgetMediaControlsConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addButton.setOnClickListener(addWidgetButtonClickListener)

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Inject components
        DaggerProviderComponent.builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        val mediaPlayerControlsWidgetDao = AppDatabase.getInstance(applicationContext).mediaPlayCtrlWidgetDao()
        val mediaPlayerWidget = mediaPlayerControlsWidgetDao.get(appWidgetId)
        if (mediaPlayerWidget != null) {
            binding.label.setText(mediaPlayerWidget.label)
            binding.widgetTextConfigEntityId.setText(mediaPlayerWidget.entityId)
            binding.widgetShowSeekButtonsCheckbox.isChecked = mediaPlayerWidget.showSeek
            binding.widgetShowSkipButtonsCheckbox.isChecked = mediaPlayerWidget.showSkip
            val entity = runBlocking {
                try {
                    integrationUseCase.getEntity(mediaPlayerWidget.entityId)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to get entity information", e)
                    Toast.makeText(applicationContext, R.string.widget_entity_fetch_error, Toast.LENGTH_LONG)
                        .show()
                    null
                }
            }
            if (entity != null)
                selectedEntity = entity as Entity<Any>?
            binding.addButton.setText(R.string.update_widget)
            binding.deleteButton.visibility = View.VISIBLE
            binding.deleteButton.setOnClickListener(onDeleteWidget)
        }
        val entityAdapter = SingleItemArrayAdapter<Entity<Any>>(this) { it?.entityId ?: "" }

        binding.widgetTextConfigEntityId.setAdapter(entityAdapter)
        binding.widgetTextConfigEntityId.onFocusChangeListener = dropDownOnFocus
        binding.widgetTextConfigEntityId.onItemClickListener = entityDropDownOnItemClick

        mainScope.launch {
            try {
                // Fetch entities
                val fetchedEntities = integrationUseCase.getEntities()
                fetchedEntities.forEach {
                    val entityId = it.entityId
                    val domain = entityId.split(".")[0]

                    if (domain == "media_player") {
                        entities[entityId] = it
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

    private var addWidgetButtonClickListener = View.OnClickListener {
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
                MediaPlayerControlsWidget.EXTRA_SHOW_SKIP,
                binding.widgetShowSkipButtonsCheckbox.isChecked
            )
            intent.putExtra(
                MediaPlayerControlsWidget.EXTRA_SHOW_SEEK,
                binding.widgetShowSeekButtonsCheckbox.isChecked
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
            Toast.makeText(applicationContext, R.string.widget_creation_error, Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }

    private var onDeleteWidget = View.OnClickListener {
        val context = this@MediaPlayerControlsWidgetConfigureActivity
        deleteConfirmation(context)
    }

    private fun deleteConfirmation(context: Context) {
        val mediaPlayerControlsWidgetDao = AppDatabase.getInstance(context).mediaPlayCtrlWidgetDao()

        val builder: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(context)

        builder.setTitle(R.string.confirm_delete_this_widget_title)
        builder.setMessage(R.string.confirm_delete_this_widget_message)

        builder.setPositiveButton(
            R.string.confirm_positive
        ) { dialog, _ ->
            mediaPlayerControlsWidgetDao.delete(appWidgetId)
            dialog.dismiss()
            finish()
        }

        builder.setNegativeButton(
            R.string.confirm_negative
        ) { dialog, _ -> // Do nothing
            dialog.dismiss()
        }

        val alert: android.app.AlertDialog? = builder.create()
        alert?.show()
    }
}
