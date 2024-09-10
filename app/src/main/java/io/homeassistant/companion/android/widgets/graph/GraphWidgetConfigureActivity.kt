package io.homeassistant.companion.android.widgets.graph

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
import android.widget.AutoCompleteTextView
import android.widget.Spinner
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.canSupportPrecision
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.widgets.GraphWidgetRepository
import io.homeassistant.companion.android.common.util.DateFormatter
import io.homeassistant.companion.android.databinding.WidgetGraphConfigureBinding
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.widgets.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.common.SingleItemArrayAdapter
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class GraphWidgetConfigureActivity : BaseWidgetConfigureActivity() {

    @Inject
    lateinit var repository: GraphWidgetRepository

    companion object {
        private const val TAG: String = "GraphWidgetConfigAct"
        private const val PIN_WIDGET_CALLBACK = "io.homeassistant.companion.android.widgets.entity.GraphWidgetConfigureActivity.PIN_WIDGET_CALLBACK"
    }

    private var entities = mutableMapOf<Int, List<Entity<Any>>>()

    private var selectedEntity: Entity<Any>? = null
    private var labelFromEntity = false

    private lateinit var binding: WidgetGraphConfigureBinding

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

        binding = WidgetGraphConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addButton.setOnClickListener {
            if (requestLauncherSetup) {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    isValidServerId()
                ) {
                    getSystemService<AppWidgetManager>()?.requestPinAppWidget(
                        ComponentName(this, GraphWidget::class.java),
                        null,
                        PendingIntent.getActivity(
                            this,
                            System.currentTimeMillis().toInt(),
                            Intent(this, GraphWidgetConfigureActivity::class.java).putExtra(PIN_WIDGET_CALLBACK, true).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
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

        val graphWidget = repository.get(appWidgetId)

        if (graphWidget != null) {
            binding.widgetTextConfigEntityId.setText(graphWidget.entityId)

            binding.addButton.setText(commonR.string.update_widget)

            repository.get(appWidgetId)?.let {
                binding.label.setText(it.label)
                binding.timeRange.value = it.timeRange.toFloat()
                binding.timeRangeLabel.text = getRangeHoursLabel(it.timeRange)
            }
        } else {
            binding.timeRangeLabel.text = getRangeHoursLabel(binding.timeRange.value.toInt())
        }

        binding.timeRange.addOnChangeListener { _, value, _ ->
            binding.timeRangeLabel.text = buildString {
                append(getString(commonR.string.time_range))
                append(" ")
                append(DateFormatter.formatHours(this@GraphWidgetConfigureActivity, value.toInt()))
            }
        }

        entityAdapter = SingleItemArrayAdapter(this) { it?.entityId ?: "" }

        setupServerSelect(graphWidget?.serverId)

        binding.widgetTextConfigEntityId.setAdapter(entityAdapter)
        binding.widgetTextConfigEntityId.onFocusChangeListener = dropDownOnFocus
        binding.widgetTextConfigEntityId.onItemClickListener = entityDropDownOnItemClick

        binding.label.addTextChangedListener(labelTextChanged)

        serverManager.defaultServers.forEach { server ->
            lifecycleScope.launch {
                try {
                    val fetchedEntities = serverManager.integrationRepository(server.id).getEntities().orEmpty()
                    entities[server.id] = fetchedEntities
                    if (server.id == selectedServerId) setAdapterEntities(server.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to query entities", e)
                }
            }
        }
    }

    private fun getRangeHoursLabel(timeRange: Int): String {
        return buildString {
            append(getString(commonR.string.time_range))
            append(" ")
            append(DateFormatter.formatHours(this@GraphWidgetConfigureActivity, timeRange))
        }
    }

    override fun onServerSelected(serverId: Int) {
        selectedEntity = null
        binding.widgetTextConfigEntityId.setText("")
        setAdapterEntities(serverId)
    }

    private fun setAdapterEntities(serverId: Int) {
        entityAdapter?.let { adapter ->
            adapter.clearAll()
            if (entities[serverId] != null) {
                adapter.addAll(
                    entities[serverId]?.filter {
                        it.canSupportPrecision()
                    }.orEmpty().toMutableList()
                )
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

    private val entityDropDownOnItemClick =
        AdapterView.OnItemClickListener { parent, _, position, _ ->
            selectedEntity = parent.getItemAtPosition(position) as Entity<Any>?
            if (binding.label.text.isNullOrBlank() || labelFromEntity) {
                selectedEntity?.friendlyName?.takeIf { it != selectedEntity?.entityId }?.let { name ->
                    binding.label.removeTextChangedListener(labelTextChanged)
                    binding.label.setText(name)
                    labelFromEntity = true
                    binding.label.addTextChangedListener(labelTextChanged)
                }
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
            val context = this@GraphWidgetConfigureActivity

            // Set up a broadcast intent and pass the service call data as extras
            val intent = Intent()
            intent.action = BaseWidgetProvider.RECEIVE_DATA
            intent.component = ComponentName(context, GraphWidget::class.java)

            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

            intent.putExtra(
                GraphWidget.EXTRA_SERVER_ID,
                selectedServerId!!
            )

            val entity = if (selectedEntity == null) {
                binding.widgetTextConfigEntityId.text.toString()
            } else {
                selectedEntity!!.entityId
            }
            if (entity !in entities[selectedServerId].orEmpty().map { it.entityId }) {
                showAddWidgetError()
                return
            }
            intent.putExtra(
                GraphWidget.EXTRA_ENTITY_ID,
                entity
            )

            val unitOfMeasurement = entities[selectedServerId].orEmpty().filter { it.entityId == entity }.map { (it.attributes as? Map<*, *>)?.get("unit_of_measurement")?.toString() }.first()

            unitOfMeasurement?.let {
                intent.putExtra(
                    GraphWidget.UNIT_OF_MEASUREMENT,
                    unitOfMeasurement
                )
                repository.updateWidgetSensorUnitOfMeasurement(appWidgetId, it)
            }

            intent.putExtra(
                GraphWidget.EXTRA_LABEL,
                binding.label.text.toString()
            )

            intent.putExtra(GraphWidget.EXTRA_TIME_RANGE, binding.timeRange.value.toInt())

            repository.deleteEntries(appWidgetId)
            repository.updateWidgetSensorEntityId(appWidgetId, binding.widgetTextConfigEntityId.text.toString())
            repository.updateWidgetLastLabel(appWidgetId, binding.label.text.toString())
            repository.updateWidgetTimeRange(appWidgetId, binding.timeRange.value.toInt())

            context.sendBroadcast(intent)

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
