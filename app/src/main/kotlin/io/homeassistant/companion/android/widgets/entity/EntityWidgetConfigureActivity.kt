package io.homeassistant.companion.android.widgets.entity

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout.VISIBLE
import android.widget.MultiAutoCompleteTextView.CommaTokenizer
import android.widget.Spinner
import android.widget.Toast
import androidx.core.content.ContextCompat
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
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.databinding.WidgetStaticConfigureBinding
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.applySafeDrawingInsets
import io.homeassistant.companion.android.util.getHexForColor
import io.homeassistant.companion.android.widgets.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.common.SingleItemArrayAdapter
import io.homeassistant.companion.android.widgets.common.WidgetUtils
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class EntityWidgetConfigureActivity : BaseWidgetConfigureActivity<StaticWidgetEntity, StaticWidgetDao>() {

    companion object {
        fun newInstance(context: Context, entityId: String): Intent {
            return Intent(context, EntityWidgetConfigureActivity::class.java).apply {
                putExtra(FOR_ENTITY, entityId)
                putExtra(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    private var entities = mutableMapOf<Int, List<Entity>>()

    private var selectedEntity: Entity? = null
    private var appendAttributes: Boolean = false
    private var selectedAttributeIds: ArrayList<String> = ArrayList()
    private var labelFromEntity = false

    private lateinit var binding: WidgetStaticConfigureBinding

    override val serverSelect: View
        get() = binding.serverSelect

    override val serverSelectList: Spinner
        get() = binding.serverSelectList

    private var requestLauncherSetup = false

    private var entityAdapter: SingleItemArrayAdapter<Entity>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = WidgetStaticConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySafeDrawingInsets()

        binding.addButton.setOnClickListener {
            if (requestLauncherSetup) {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    isValidServerId()
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

        lifecycleScope.launch {
            val staticWidget = dao.get(appWidgetId)

            if (staticWidget != null) {
                binding.widgetTextConfigEntityId.setText(staticWidget.entityId)
                binding.label.setText(staticWidget.label)
                binding.textSize.setText(staticWidget.textSize.toInt().toString())
                binding.stateSeparator.setText(staticWidget.stateSeparator)
                val entity = try {
                    serverManager.integrationRepository(staticWidget.serverId).getEntity(staticWidget.entityId)
                } catch (e: Exception) {
                    Timber.e(e, "Unable to get entity information")
                    Toast.makeText(applicationContext, commonR.string.widget_entity_fetch_error, Toast.LENGTH_LONG)
                        .show()
                    null
                }

                val attributeIds = staticWidget.attributeIds
                if (!attributeIds.isNullOrEmpty()) {
                    binding.appendAttributeValueCheckbox.isChecked = true
                    appendAttributes = true
                    for (item in attributeIds.split(',')) {
                        selectedAttributeIds.add(item)
                    }
                    binding.widgetTextConfigAttribute.setText(attributeIds.replace(",", ", "))
                    binding.attributeValueLinearLayout.visibility = VISIBLE
                    binding.attributeSeparator.setText(staticWidget.attributeSeparator)
                }
                if (entity != null) {
                    selectedEntity = entity
                    setupAttributes()
                }

                val toggleable = entity?.domain in EntityExt.APP_PRESS_ACTION_DOMAINS
                binding.tapAction.isVisible = toggleable
                binding.tapActionList.setSelection(
                    if (toggleable &&
                        staticWidget.tapAction == WidgetTapAction.TOGGLE
                    ) {
                        0
                    } else {
                        1
                    },
                )
                binding.textColor.visibility =
                    if (staticWidget.backgroundType == WidgetBackgroundType.TRANSPARENT) View.VISIBLE else View.GONE
                binding.textColorWhite.isChecked =
                    staticWidget.textColor?.let {
                        it.toColorInt() == ContextCompat.getColor(
                            this@EntityWidgetConfigureActivity,
                            android.R.color.white,
                        )
                    }
                        ?: true
                binding.textColorBlack.isChecked =
                    staticWidget.textColor?.let {
                        it.toColorInt() ==
                            ContextCompat.getColor(
                                this@EntityWidgetConfigureActivity,
                                commonR.color.colorWidgetButtonLabelBlack,
                            )
                    }
                        ?: false

                binding.addButton.setText(commonR.string.update_widget)
            } else {
                binding.backgroundType.setSelection(0)
            }

            setupServerSelect(staticWidget?.serverId)
        }

        val tapActionValues =
            listOf(getString(commonR.string.widget_tap_action_toggle), getString(commonR.string.refresh))
        binding.tapActionList.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tapActionValues)
        val backgroundTypeValues = WidgetUtils.getBackgroundOptionList(this)
        binding.backgroundType.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, backgroundTypeValues)

        entityAdapter = SingleItemArrayAdapter(this) { it?.entityId ?: "" }

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
                    if (parent?.adapter?.getItem(position) ==
                        getString(commonR.string.widget_background_type_transparent)
                    ) {
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
                    Timber.e(e, "Failed to query entities")
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

    override val widgetClass: Class<*> = EntityWidget::class.java

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
            selectedEntity = parent.getItemAtPosition(position) as Entity?
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
        val fetchedAttributes = selectedEntity?.attributes
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

    override suspend fun getPendingDaoEntity(): StaticWidgetEntity {
        val serverId = checkNotNull(selectedServerId) { "Selected server ID is null" }

        val entity = if (selectedEntity == null) {
            binding.widgetTextConfigEntityId.text.toString()
        } else {
            checkNotNull(selectedEntity?.entityId) { "Selected entity is null" }
        }

        if (entity !in entities[serverId].orEmpty().map { it.entityId }) {
            throw IllegalStateException("Selected entity is unknown on server")
        }

        return StaticWidgetEntity(
            id = appWidgetId,
            serverId = serverId,
            entityId = entity,
            label = binding.label.text?.toString(),
            textSize = binding.textSize.text?.toString()?.toFloatOrNull() ?: 30F,
            stateSeparator = binding.stateSeparator.text?.toString() ?: "",
            attributeIds = if (appendAttributes) {
                if (selectedAttributeIds.isEmpty()) {
                    binding.widgetTextConfigAttribute.text.toString()
                } else {
                    selectedAttributeIds.joinToString(",")
                }
            } else {
                null
            },
            attributeSeparator = if (appendAttributes) binding.attributeSeparator.text?.toString() ?: "" else "",
            tapAction = when (binding.tapActionList.selectedItemPosition) {
                0 -> WidgetTapAction.TOGGLE
                else -> WidgetTapAction.REFRESH
            },
            backgroundType = when (binding.backgroundType.selectedItem as String?) {
                getString(commonR.string.widget_background_type_dynamiccolor) -> WidgetBackgroundType.DYNAMICCOLOR
                getString(commonR.string.widget_background_type_transparent) -> WidgetBackgroundType.TRANSPARENT
                else -> WidgetBackgroundType.DAYNIGHT
            },
            textColor = if (binding.backgroundType.selectedItem as String? ==
                getString(commonR.string.widget_background_type_transparent)
            ) {
                getHexForColor(
                    if (binding.textColorWhite.isChecked) {
                        android.R.color.white
                    } else {
                        commonR.color.colorWidgetButtonLabelBlack
                    },
                )
            } else {
                null
            },
            lastUpdate = dao.get(appWidgetId)?.lastUpdate ?: "",
        )
    }
}
