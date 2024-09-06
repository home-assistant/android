package io.homeassistant.companion.android.widgets.entity

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
import android.widget.LinearLayout.VISIBLE
import android.widget.MultiAutoCompleteTextView.CommaTokenizer
import android.widget.Spinner
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.databinding.WidgetStaticConfigureBinding
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.getHexForColor
import io.homeassistant.companion.android.widgets.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.common.SingleItemArrayAdapter
import io.homeassistant.companion.android.widgets.common.WidgetUtils
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class EntityWidgetConfigureActivity : BaseWidgetConfigureActivity() {

    companion object {
        private const val TAG: String = "StaticWidgetConfigAct"
        private const val PIN_WIDGET_CALLBACK = "io.homeassistant.companion.android.widgets.entity.EntityWidgetConfigureActivity.PIN_WIDGET_CALLBACK"
    }

    @Inject
    lateinit var staticWidgetDao: StaticWidgetDao
    override val dao get() = staticWidgetDao

    private var entities = mutableMapOf<Int, List<Entity<Any>>>()

    private var selectedEntity: Entity<Any>? = null
    private var appendAttributes: Boolean = false
    private var selectedAttributeIds: ArrayList<String> = ArrayList()
    private var labelFromEntity = false

    private lateinit var binding: WidgetStaticConfigureBinding

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

        binding = WidgetStaticConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addButton.setOnClickListener {
            if (requestLauncherSetup) {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    isValidServerId()
                ) {
                    getSystemService<AppWidgetManager>()?.requestPinAppWidget(
                        ComponentName(this, EntityWidget::class.java),
                        null,
                        PendingIntent.getActivity(
                            this,
                            System.currentTimeMillis().toInt(),
                            Intent(this, EntityWidgetConfigureActivity::class.java).putExtra(PIN_WIDGET_CALLBACK, true).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
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

        val staticWidget = staticWidgetDao.get(appWidgetId)

        val tapActionValues = listOf(getString(commonR.string.widget_tap_action_toggle), getString(commonR.string.refresh))
        binding.tapActionList.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tapActionValues)
        val backgroundTypeValues = WidgetUtils.getBackgroundOptionList(this)
        binding.backgroundType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, backgroundTypeValues)

        if (staticWidget != null) {
            binding.widgetTextConfigEntityId.setText(staticWidget.entityId)
            binding.label.setText(staticWidget.label)
            binding.textSize.setText(staticWidget.textSize.toInt().toString())
            binding.stateSeparator.setText(staticWidget.stateSeparator)
            val entity = runBlocking {
                try {
                    serverManager.integrationRepository(staticWidget.serverId).getEntity(staticWidget.entityId)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to get entity information", e)
                    Toast.makeText(applicationContext, commonR.string.widget_entity_fetch_error, Toast.LENGTH_LONG)
                        .show()
                    null
                }
            }

            val attributeIds = staticWidget.attributeIds
            if (!attributeIds.isNullOrEmpty()) {
                binding.appendAttributeValueCheckbox.isChecked = true
                appendAttributes = true
                for (item in attributeIds.split(','))
                    selectedAttributeIds.add(item)
                binding.widgetTextConfigAttribute.setText(attributeIds.replace(",", ", "))
                binding.attributeValueLinearLayout.visibility = VISIBLE
                binding.attributeSeparator.setText(staticWidget.attributeSeparator)
            }
            if (entity != null) {
                selectedEntity = entity as Entity<Any>?
                setupAttributes()
            }

            val toggleable = entity?.domain in EntityExt.APP_PRESS_ACTION_DOMAINS
            binding.tapAction.isVisible = toggleable
            binding.tapActionList.setSelection(if (toggleable && staticWidget.tapAction == WidgetTapAction.TOGGLE) 0 else 1)
            binding.textColor.visibility = if (staticWidget.backgroundType == WidgetBackgroundType.TRANSPARENT) View.VISIBLE else View.GONE
            binding.textColorWhite.isChecked =
                staticWidget.textColor?.let { it.toColorInt() == ContextCompat.getColor(this, android.R.color.white) } ?: true
            binding.textColorBlack.isChecked =
                staticWidget.textColor?.let { it.toColorInt() == ContextCompat.getColor(this, commonR.color.colorWidgetButtonLabelBlack) } ?: false

            binding.addButton.setText(commonR.string.update_widget)
        } else {
            binding.backgroundType.setSelection(0)
        }
        entityAdapter = SingleItemArrayAdapter(this) { it?.entityId ?: "" }

        setupServerSelect(staticWidget?.serverId)

        binding.widgetTextConfigEntityId.setAdapter(entityAdapter)
        binding.widgetTextConfigEntityId.onFocusChangeListener = dropDownOnFocus
        binding.widgetTextConfigEntityId.onItemClickListener = entityDropDownOnItemClick
        binding.widgetTextConfigAttribute.onFocusChangeListener = dropDownOnFocus
        binding.widgetTextConfigAttribute.onItemClickListener = attributeDropDownOnItemClick
        binding.widgetTextConfigAttribute.setOnClickListener {
            if (!binding.widgetTextConfigAttribute.isPopupShowing) binding.widgetTextConfigAttribute.showDropDown()
        }

        binding.appendAttributeValueCheckbox.setOnCheckedChangeListener { _, isChecked ->
            binding.attributeValueLinearLayout.isVisible = isChecked
            appendAttributes = isChecked
        }

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
        selectedEntity = null
        binding.widgetTextConfigEntityId.setText("")
        setupAttributes()
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
            setupAttributes()
        }

    private val attributeDropDownOnItemClick =
        AdapterView.OnItemClickListener { parent, _, position, _ ->
            selectedAttributeIds.add(parent.getItemAtPosition(position) as String)
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

    private fun setupAttributes() {
        val fetchedAttributes = selectedEntity?.attributes as? Map<String, String>
        val attributesAdapter = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line)
        binding.widgetTextConfigAttribute.setAdapter(attributesAdapter)
        attributesAdapter.addAll(*fetchedAttributes?.keys.orEmpty().toTypedArray())
        binding.widgetTextConfigAttribute.setTokenizer(CommaTokenizer())
        runOnUiThread {
            val toggleable = selectedEntity?.domain in EntityExt.APP_PRESS_ACTION_DOMAINS
            binding.tapAction.isVisible = toggleable
            binding.tapActionList.setSelection(if (toggleable) 0 else 1)
            attributesAdapter.notifyDataSetChanged()
        }
    }

    private fun onAddWidget() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            showAddWidgetError()
            return
        }
        try {
            val context = this@EntityWidgetConfigureActivity

            // Set up a broadcast intent and pass the service call data as extras
            val intent = Intent()
            intent.action = BaseWidgetProvider.RECEIVE_DATA
            intent.component = ComponentName(context, EntityWidget::class.java)

            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

            intent.putExtra(
                EntityWidget.EXTRA_SERVER_ID,
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
                EntityWidget.EXTRA_ENTITY_ID,
                entity
            )

            intent.putExtra(
                EntityWidget.EXTRA_LABEL,
                binding.label.text.toString()
            )

            intent.putExtra(
                EntityWidget.EXTRA_TEXT_SIZE,
                binding.textSize.text.toString()
            )

            intent.putExtra(
                EntityWidget.EXTRA_STATE_SEPARATOR,
                binding.stateSeparator.text.toString()
            )

            if (appendAttributes) {
                val attributes = if (selectedAttributeIds.isEmpty()) {
                    binding.widgetTextConfigAttribute.text.toString()
                } else {
                    selectedAttributeIds
                }
                intent.putExtra(
                    EntityWidget.EXTRA_ATTRIBUTE_IDS,
                    attributes
                )

                intent.putExtra(
                    EntityWidget.EXTRA_ATTRIBUTE_SEPARATOR,
                    binding.attributeSeparator.text.toString()
                )
            }

            intent.putExtra(
                EntityWidget.EXTRA_TAP_ACTION,
                when (binding.tapActionList.selectedItemPosition) {
                    0 -> WidgetTapAction.TOGGLE
                    else -> WidgetTapAction.REFRESH
                }
            )

            intent.putExtra(
                EntityWidget.EXTRA_BACKGROUND_TYPE,
                when (binding.backgroundType.selectedItem as String?) {
                    getString(commonR.string.widget_background_type_dynamiccolor) -> WidgetBackgroundType.DYNAMICCOLOR
                    getString(commonR.string.widget_background_type_transparent) -> WidgetBackgroundType.TRANSPARENT
                    else -> WidgetBackgroundType.DAYNIGHT
                }
            )

            intent.putExtra(
                EntityWidget.EXTRA_TEXT_COLOR,
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
