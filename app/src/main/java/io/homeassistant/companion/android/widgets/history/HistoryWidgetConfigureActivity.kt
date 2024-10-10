package io.homeassistant.companion.android.widgets.history

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.MultiAutoCompleteTextView
import android.widget.Spinner
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.database.widget.HistoryWidgetDao
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.databinding.WidgetHistoryConfigureBinding
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.getHexForColor
import io.homeassistant.companion.android.widgets.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.common.SingleItemArrayAdapter
import java.util.LinkedList
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class HistoryWidgetConfigureActivity : BaseWidgetConfigureActivity() {

    companion object {
        private const val TAG: String = "HistoryWidgetConfigAct"
        private const val PIN_WIDGET_CALLBACK = "io.homeassistant.companion.android.widgets.entity.HistoryWidgetConfigureActivity.PIN_WIDGET_CALLBACK"
    }

    @Inject
    lateinit var historyWidgetDao: HistoryWidgetDao
    override val dao get() = historyWidgetDao

    private var entities = mutableMapOf<Int, List<Entity<Any>>>()
    private var selectedEntities: LinkedList<Entity<*>?> = LinkedList()

    private var labelFromEntity = false

    private lateinit var binding: WidgetHistoryConfigureBinding

    override val serverSelect: View
        get() = binding.serverSelect

    override val serverSelectList: Spinner
        get() = binding.serverSelectList

    private var requestLauncherSetup = false

    private var entityAdapter: SingleItemArrayAdapter<Entity<Any>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = WidgetHistoryConfigureBinding.inflate(layoutInflater)
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
                        ComponentName(this, HistoryWidget::class.java),
                        null,
                        PendingIntent.getActivity(
                            this,
                            System.currentTimeMillis().toInt(),
                            Intent(this, HistoryWidgetConfigureActivity::class.java).putExtra(PIN_WIDGET_CALLBACK, true).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
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

        val historyWidget = historyWidgetDao.get(appWidgetId)

        val backgroundTypeValues = mutableListOf(
            getString(commonR.string.widget_background_type_daynight),
            getString(commonR.string.widget_background_type_transparent)
        )
        if (DynamicColors.isDynamicColorAvailable()) {
            backgroundTypeValues.add(0, getString(commonR.string.widget_background_type_dynamiccolor))
        }
        binding.backgroundType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, backgroundTypeValues)

        if (historyWidget != null) {
            binding.widgetTextConfigEntityId.setText(historyWidget.entityId)
            binding.label.setText(historyWidget.label)
            binding.textSize.setText(historyWidget.textSize.toInt().toString())

            binding.backgroundType.setSelection(
                when {
                    historyWidget.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable() ->
                        backgroundTypeValues.indexOf(getString(commonR.string.widget_background_type_dynamiccolor))
                    historyWidget.backgroundType == WidgetBackgroundType.TRANSPARENT ->
                        backgroundTypeValues.indexOf(getString(commonR.string.widget_background_type_transparent))
                    else ->
                        backgroundTypeValues.indexOf(getString(commonR.string.widget_background_type_daynight))
                }
            )
            binding.textColor.visibility = if (historyWidget.backgroundType == WidgetBackgroundType.TRANSPARENT) View.VISIBLE else View.GONE
            binding.textColorWhite.isChecked =
                historyWidget.textColor?.let { it.toColorInt() == ContextCompat.getColor(this, android.R.color.white) } ?: true
            binding.textColorBlack.isChecked =
                historyWidget.textColor?.let { it.toColorInt() == ContextCompat.getColor(this, commonR.color.colorWidgetButtonLabelBlack) } ?: false

            val entities = runBlocking {
                try {
                    historyWidget.entityId.split(",").map { s ->
                        serverManager.integrationRepository(historyWidget.serverId).getEntity(s.trim())
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
        } else {
            binding.textSize.setText(HistoryWidget.DEFAULT_TEXT_SIZE.toInt().toString())
            binding.backgroundType.setSelection(0)
        }
        entityAdapter = SingleItemArrayAdapter(this) { it?.entityId ?: "" }

        setupServerSelect(historyWidget?.serverId)

        binding.widgetTextConfigEntityId.setAdapter(entityAdapter)
        binding.widgetTextConfigEntityId.setTokenizer(MultiAutoCompleteTextView.CommaTokenizer())
        binding.widgetTextConfigEntityId.onFocusChangeListener = dropDownOnFocus

        binding.label.addTextChangedListener(labelTextChanged)

        binding.backgroundType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.textColor.visibility =
                    if (parent?.adapter?.getItem(position) == getString(commonR.string.widget_background_type_transparent)) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                binding.textColor.visibility = View.GONE
            }
        }

        serverManager.defaultServers.forEach { server ->
            lifecycleScope.launch {
                try {
                    val fetchedEntities = serverManager.integrationRepository(server.id).getEntities().orEmpty()
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

    private val dropDownOnFocus = View.OnFocusChangeListener { view, hasFocus ->
        if (hasFocus && view is AutoCompleteTextView) {
            view.showDropDown()
        }
    }

    private val labelTextChanged = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            // Not implemented
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            // Not implemented
        }

        override fun afterTextChanged(s: Editable?) {
            labelFromEntity = false
        }
    }

    private fun onAddWidget() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            showAddWidgetError()
            return
        }
        try {
            val context = this@HistoryWidgetConfigureActivity

            // Set up a broadcast intent and pass the service call data as extras
            val intent = Intent()
            intent.action = BaseWidgetProvider.RECEIVE_DATA
            intent.component = ComponentName(context, HistoryWidget::class.java)

            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

            intent.putExtra(
                HistoryWidget.EXTRA_SERVER_ID,
                selectedServerId!!
            )

            selectedEntities = LinkedList()
            val se = binding.widgetTextConfigEntityId.text.split(",")
            se.forEach {
                val entity = entities[selectedServerId]!!.firstOrNull { e -> e.entityId == it.trim() }
                if (entity != null) selectedEntities.add(entity)
            }
            intent.putExtra(
                HistoryWidget.EXTRA_ENTITY_ID,
                selectedEntities.map { e -> e?.entityId }.reduce { a, b -> "$a,$b" }
            )

            intent.putExtra(
                HistoryWidget.EXTRA_LABEL,
                binding.label.text.toString()
            )

            intent.putExtra(
                HistoryWidget.EXTRA_TEXT_SIZE,
                binding.textSize.text.toString()
            )

            intent.putExtra(
                HistoryWidget.EXTRA_BACKGROUND_TYPE,
                when (binding.backgroundType.selectedItem as String?) {
                    getString(commonR.string.widget_background_type_dynamiccolor) -> WidgetBackgroundType.DYNAMICCOLOR
                    getString(commonR.string.widget_background_type_transparent) -> WidgetBackgroundType.TRANSPARENT
                    else -> WidgetBackgroundType.DAYNIGHT
                }
            )

            intent.putExtra(
                HistoryWidget.EXTRA_TEXT_COLOR,
                if (binding.backgroundType.selectedItem as String? == getString(commonR.string.widget_background_type_transparent)) {
                    getHexForColor(if (binding.textColorWhite.isChecked) android.R.color.white else commonR.color.colorWidgetButtonLabelBlack)
                } else {
                    null
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
