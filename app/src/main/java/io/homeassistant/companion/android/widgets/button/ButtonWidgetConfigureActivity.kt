package io.homeassistant.companion.android.widgets.button

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Action
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.databinding.WidgetButtonConfigureBinding
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.getHexForColor
import io.homeassistant.companion.android.util.icondialog.IconDialogFragment
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.util.icondialog.mdiName
import io.homeassistant.companion.android.widgets.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.common.ActionFieldBinder
import io.homeassistant.companion.android.widgets.common.SingleItemArrayAdapter
import io.homeassistant.companion.android.widgets.common.WidgetDynamicFieldAdapter
import io.homeassistant.companion.android.widgets.common.WidgetUtils
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ButtonWidgetConfigureActivity : BaseWidgetConfigureActivity() {
    companion object {
        private const val TAG: String = "ButtonWidgetConfigAct"
        private const val PIN_WIDGET_CALLBACK = "io.homeassistant.companion.android.widgets.button.ButtonWidgetConfigureActivity.PIN_WIDGET_CALLBACK"
    }

    @Inject
    lateinit var buttonWidgetDao: ButtonWidgetDao
    override val dao get() = buttonWidgetDao

    private var actions = mutableMapOf<Int, HashMap<String, Action>>()
    private var entities = mutableMapOf<Int, HashMap<String, Entity<Any>>>()
    private var dynamicFields = ArrayList<ActionFieldBinder>()
    private lateinit var dynamicFieldAdapter: WidgetDynamicFieldAdapter

    private lateinit var binding: WidgetButtonConfigureBinding

    override val serverSelect: View
        get() = binding.serverSelect

    override val serverSelectList: Spinner
        get() = binding.serverSelectList

    private var requestLauncherSetup = false

    private var actionAdapter: SingleItemArrayAdapter<Action>? = null

    private val onAddFieldListener = View.OnClickListener {
        val context = this@ButtonWidgetConfigureActivity
        val fieldKeyInput = EditText(context)
        fieldKeyInput.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        AlertDialog.Builder(context)
            .setTitle("Field")
            .setView(fieldKeyInput)
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (dynamicFields.any { it.field == binding.widgetTextConfigService.text.toString() }) return@setPositiveButton

                val position = dynamicFields.size
                dynamicFields.add(
                    position,
                    ActionFieldBinder(
                        binding.widgetTextConfigService.text.toString(),
                        fieldKeyInput.text.toString()
                    )
                )

                dynamicFieldAdapter.notifyItemInserted(position)
            }
            .show()
    }

    private val dropDownOnFocus = View.OnFocusChangeListener { view, hasFocus ->
        if (hasFocus && view is AutoCompleteTextView) {
            view.showDropDown()
        }
    }

    private val actionTextWatcher: TextWatcher = (
        object : TextWatcher {
            @SuppressLint("NotifyDataSetChanged")
            override fun afterTextChanged(p0: Editable?) {
                val actionText: String = p0.toString()

                if (actions[selectedServerId].orEmpty().keys.contains(actionText)) {
                    Log.d(TAG, "Valid domain and action--processing dynamic fields")

                    // Make sure there are not already any dynamic fields created
                    // This can happen if selecting the drop-down twice or pasting
                    dynamicFields.clear()

                    // We only call this if servicesAvailable was fetched and is not null,
                    // so we can safely assume that it is not null here
                    val actionData = actions[selectedServerId]!![actionText]!!.actionData
                    val target = actionData.target
                    val fields = actionData.fields

                    val fieldKeys = fields.keys
                    Log.d(TAG, "Fields applicable to this action: $fields")

                    val existingActionData = mutableMapOf<String, Any?>()
                    val addedFields = mutableListOf<String>()
                    buttonWidgetDao.get(appWidgetId)?.let { buttonWidget ->
                        if (
                            buttonWidget.serverId != selectedServerId ||
                            "${buttonWidget.domain}.${buttonWidget.service}" != actionText
                        ) {
                            return@let
                        }

                        val dbMap: HashMap<String, Any?> = jacksonObjectMapper().readValue(buttonWidget.serviceData)
                        for (item in dbMap) {
                            val value = item.value.toString().replace("[", "").replace("]", "") + if (item.key == "entity_id") ", " else ""
                            existingActionData[item.key] = value.ifEmpty { null }
                            addedFields.add(item.key)
                        }
                    }

                    if (target != false) {
                        dynamicFields.add(0, ActionFieldBinder(actionText, "entity_id", existingActionData["entity_id"]))
                    }

                    fieldKeys.sorted().forEach { fieldKey ->
                        Log.d(TAG, "Creating a text input box for $fieldKey")

                        // Insert a dynamic layout
                        // IDs get priority and go at the top, since the other fields
                        // are usually optional but the ID is required
                        if (fieldKey.contains("_id")) {
                            dynamicFields.add(0, ActionFieldBinder(actionText, fieldKey, existingActionData[fieldKey]))
                        } else {
                            dynamicFields.add(ActionFieldBinder(actionText, fieldKey, existingActionData[fieldKey]))
                        }
                    }
                    addedFields.minus("entity_id").minus(fieldKeys).forEach { extraFieldKey ->
                        Log.d(TAG, "Creating a text input box for extra $extraFieldKey")
                        dynamicFields.add(ActionFieldBinder(actionText, extraFieldKey, existingActionData[extraFieldKey]))
                    }

                    dynamicFieldAdapter.notifyDataSetChanged()
                } else {
                    if (dynamicFields.size > 0) {
                        dynamicFields.clear()
                        dynamicFieldAdapter.notifyDataSetChanged()
                    }
                }
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        }
        )

    private fun getActionString(action: Action): String {
        return "${action.domain}.${action.action}"
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        binding = WidgetButtonConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        val buttonWidget = buttonWidgetDao.get(appWidgetId)
        val backgroundTypeValues = WidgetUtils.getBackgroundOptionList(this)
        binding.backgroundType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, backgroundTypeValues)

        if (buttonWidget != null) {
            val actionText = "${buttonWidget.domain}.${buttonWidget.service}"
            binding.widgetTextConfigService.setText(actionText)
            binding.label.setText(buttonWidget.label)
            binding.backgroundType.setSelection(
                WidgetUtils.getSelectedBackgroundOption(
                    this,
                    buttonWidget.backgroundType,
                    backgroundTypeValues
                )
            )
            binding.textColor.isVisible = buttonWidget.backgroundType == WidgetBackgroundType.TRANSPARENT
            binding.textColorWhite.isChecked =
                buttonWidget.textColor?.let { it.toColorInt() == ContextCompat.getColor(this, android.R.color.white) } ?: true
            binding.textColorBlack.isChecked =
                buttonWidget.textColor?.let { it.toColorInt() == ContextCompat.getColor(this, commonR.color.colorWidgetButtonLabelBlack) } ?: false

            binding.addButton.setText(commonR.string.update_widget)

            binding.widgetCheckboxRequireAuthentication.isChecked = buttonWidget.requireAuthentication
        } else {
            binding.backgroundType.setSelection(0)
        }

        setupServerSelect(buttonWidget?.serverId)

        actionAdapter = SingleItemArrayAdapter(this) {
            if (it != null) getActionString(it) else ""
        }
        binding.widgetTextConfigService.setAdapter(actionAdapter)
        binding.widgetTextConfigService.onFocusChangeListener = dropDownOnFocus

        serverManager.defaultServers.forEach { server ->
            lifecycleScope.launch {
                try {
                    actions[server.id] = HashMap()
                    serverManager.integrationRepository(server.id).getServices()?.forEach {
                        actions[server.id]!![getActionString(it)] = it
                    }
                    if (server.id == selectedServerId) setAdapterActions(server.id)
                } catch (e: Exception) {
                    // Custom components can cause actions to not load
                    // Display error text
                    Log.e(TAG, "Unable to load actions from Home Assistant", e)
                    if (server.id == selectedServerId) binding.widgetConfigServiceError.visibility = View.VISIBLE
                }
            }
            lifecycleScope.launch {
                try {
                    entities[server.id] = HashMap()
                    serverManager.integrationRepository(server.id).getEntities()?.forEach {
                        entities[server.id]!![it.entityId] = it
                    }
                    if (server.id == selectedServerId) setAdapterActions(server.id)
                } catch (e: Exception) {
                    // If entities fail to load, it's okay to pass
                    // an empty map to the dynamicFieldAdapter
                }
            }
        }

        binding.widgetTextConfigService.addTextChangedListener(actionTextWatcher)

        binding.backgroundType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.textColor.isVisible = parent?.adapter?.getItem(position) == getString(commonR.string.widget_background_type_transparent)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                binding.textColor.isVisible = false
            }
        }

        binding.addFieldButton.setOnClickListener(onAddFieldListener)
        binding.addButton.setOnClickListener {
            if (requestLauncherSetup) {
                val widgetConfigAction = binding.widgetTextConfigService.text.toString()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    selectedServerId != null &&
                    (
                        widgetConfigAction in actions[selectedServerId].orEmpty().keys ||
                            widgetConfigAction.split(".", limit = 2).size == 2
                        )
                ) {
                    getSystemService<AppWidgetManager>()?.requestPinAppWidget(
                        ComponentName(this, ButtonWidget::class.java),
                        null,
                        PendingIntent.getActivity(
                            this,
                            System.currentTimeMillis().toInt(),
                            Intent(this, ButtonWidgetConfigureActivity::class.java).putExtra(PIN_WIDGET_CALLBACK, true).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
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

        dynamicFieldAdapter = WidgetDynamicFieldAdapter(HashMap(), HashMap(), dynamicFields)
        binding.widgetConfigFieldsLayout.adapter = dynamicFieldAdapter
        binding.widgetConfigFieldsLayout.layoutManager = LinearLayoutManager(this)

        // Do this off the main thread, takes a second or two...
        runOnUiThread {
            // Create an icon pack and load all drawables.
            val iconName = buttonWidget?.iconName ?: "mdi:flash"
            val icon = CommunityMaterial.getIconByMdiName(iconName) ?: CommunityMaterial.Icon2.cmd_flash
            onIconDialogIconsSelected(icon)
            binding.widgetConfigIconSelector.setOnClickListener {
                var alertDialog: DialogFragment? = null

                alertDialog = IconDialogFragment(
                    callback = {
                        onIconDialogIconsSelected(it)
                        alertDialog?.dismiss()
                    }
                )

                alertDialog.show(supportFragmentManager, IconDialogFragment.TAG)
            }
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

    override fun onServerSelected(serverId: Int) {
        binding.widgetTextConfigService.setText("")
        setAdapterActions(serverId)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setAdapterActions(serverId: Int) {
        Log.d(TAG, "Actions found: $actions")
        actionAdapter?.clearAll()
        if (actions[serverId] != null) {
            actionAdapter?.addAll(actions[serverId]?.values.orEmpty().toMutableList())
            actionAdapter?.sort()
        }
        dynamicFieldAdapter.replaceValues(
            actions[serverId].orEmpty() as HashMap<String, Action>,
            entities[serverId].orEmpty() as HashMap<String, Entity<Any>>
        )

        actionTextWatcher.afterTextChanged(binding.widgetTextConfigService.text)

        // Update action adapter
        runOnUiThread {
            actionAdapter?.filter?.filter(binding.widgetTextConfigService.text)
        }
    }

    private fun onIconDialogIconsSelected(selectedIcon: IIcon) {
        binding.widgetConfigIconSelector.tag = selectedIcon.mdiName
        val iconDrawable = IconicsDrawable(this, selectedIcon)
        iconDrawable.colorFilter = PorterDuffColorFilter(ContextCompat.getColor(this, commonR.color.colorIcon), PorterDuff.Mode.SRC_IN)

        binding.widgetConfigIconSelector.setImageBitmap(iconDrawable.toBitmap())
    }

    private fun onAddWidget() {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            showAddWidgetError()
            return
        }
        try {
            val context = this@ButtonWidgetConfigureActivity

            // Set up a broadcast intent and pass the action data as extras
            val intent = Intent()
            intent.action = ButtonWidget.RECEIVE_DATA
            intent.component = ComponentName(context, ButtonWidget::class.java)

            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

            intent.putExtra(
                ButtonWidget.EXTRA_SERVER_ID,
                selectedServerId!!
            )

            // Analyze and send action and domain
            val actionText = binding.widgetTextConfigService.text.toString()
            val actions = actions[selectedServerId].orEmpty()
            val domain = actions[actionText]?.domain ?: actionText.split(".", limit = 2)[0]
            val action = actions[actionText]?.action ?: actionText.split(".", limit = 2)[1]
            intent.putExtra(
                ButtonWidget.EXTRA_DOMAIN,
                domain
            )
            intent.putExtra(
                ButtonWidget.EXTRA_ACTION,
                action
            )

            // Fetch and send label and icon
            intent.putExtra(
                ButtonWidget.EXTRA_LABEL,
                binding.label.text.toString()
            )
            intent.putExtra(
                ButtonWidget.EXTRA_ICON_NAME,
                binding.widgetConfigIconSelector.tag as String
            )

            // Analyze and send action data
            val actionDataMap = HashMap<String, Any>()
            dynamicFields.forEach {
                var value = it.value
                if (value != null) {
                    if (it.field == "entity_id" && value is String) {
                        // Remove trailing commas and spaces
                        val trailingRegex = "[, ]+$".toRegex()
                        value = value.replace(trailingRegex, "")
                    }
                    actionDataMap[it.field] = value
                }
            }

            intent.putExtra(
                ButtonWidget.EXTRA_ACTION_DATA,
                jacksonObjectMapper().writeValueAsString(actionDataMap)
            )

            intent.putExtra(
                ButtonWidget.EXTRA_BACKGROUND_TYPE,
                when (binding.backgroundType.selectedItem as String?) {
                    getString(commonR.string.widget_background_type_dynamiccolor) -> WidgetBackgroundType.DYNAMICCOLOR
                    getString(commonR.string.widget_background_type_transparent) -> WidgetBackgroundType.TRANSPARENT
                    else -> WidgetBackgroundType.DAYNIGHT
                }
            )

            intent.putExtra(
                ButtonWidget.EXTRA_TEXT_COLOR,
                if (binding.backgroundType.selectedItem as String? == getString(commonR.string.widget_background_type_transparent)) {
                    getHexForColor(if (binding.textColorWhite.isChecked) android.R.color.white else commonR.color.colorWidgetButtonLabelBlack)
                } else {
                    null
                }
            )

            intent.putExtra(ButtonWidget.EXTRA_REQUIRE_AUTHENTICATION, binding.widgetCheckboxRequireAuthentication.isChecked)

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
}
