package io.homeassistant.companion.android.widgets.button

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.generated.Flash
import io.github.timoptr.mdiicons.toBitmap
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Action
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.SdkVersion
import io.homeassistant.companion.android.common.util.fromHaName
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.common.util.mdiName
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.databinding.WidgetButtonConfigureBinding
import io.homeassistant.companion.android.settings.widgets.ManageWidgetsViewModel
import io.homeassistant.companion.android.util.applySafeDrawingInsets
import io.homeassistant.companion.android.util.getHexForColor
import io.homeassistant.companion.android.util.icondialog.IconDialogFragment
import io.homeassistant.companion.android.widgets.BaseWidgetConfigureActivity
import io.homeassistant.companion.android.widgets.common.ActionFieldBinder
import io.homeassistant.companion.android.widgets.common.SingleItemArrayAdapter
import io.homeassistant.companion.android.widgets.common.WidgetDynamicFieldAdapter
import io.homeassistant.companion.android.widgets.common.WidgetUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

private const val ICON_PREVIEW_SIZE_DP = 24

/** Actions to prefill for an entity, most preferred first. */
private val PREFERRED_DEFAULT_ACTIONS = listOf("press", "toggle", "turn_on")

// TODO Migrate to compose https://github.com/home-assistant/android/issues/6305
@AndroidEntryPoint
class ButtonWidgetConfigureActivity : BaseWidgetConfigureActivity<ButtonWidgetEntity, ButtonWidgetDao>() {

    companion object {
        /**
         * Creates the intent that opens this screen to add a new widget to the home screen, for
         * [entityId] when one is given.
         */
        fun newInstance(context: Context, entityId: String? = null): Intent {
            return Intent(context, ButtonWidgetConfigureActivity::class.java).apply {
                entityId?.let { putExtra(FOR_ENTITY, it) }
                putExtra(ManageWidgetsViewModel.CONFIGURE_REQUEST_LAUNCHER, true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    private var actions = mutableMapOf<Int, HashMap<String, Action>>()
    private var entities = mutableMapOf<Int, HashMap<String, Entity>>()
    private var dynamicFields = ArrayList<ActionFieldBinder>()
    private lateinit var dynamicFieldAdapter: WidgetDynamicFieldAdapter

    private lateinit var binding: WidgetButtonConfigureBinding

    override val serverSelect: View
        get() = binding.serverSelect

    override val serverSelectList: Spinner
        get() = binding.serverSelectList

    private var requestLauncherSetup = false

    private var actionAdapter: SingleItemArrayAdapter<Action>? = null

    private var selectedEntity: String? = null
    private var prefilledAction: String? = null

    private val onAddFieldListener = View.OnClickListener {
        val context = this@ButtonWidgetConfigureActivity
        val fieldKeyInput = EditText(context)
        fieldKeyInput.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        AlertDialog.Builder(context)
            .setTitle("Field")
            .setView(fieldKeyInput)
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (dynamicFields.any {
                        it.field == binding.widgetTextConfigService.text.toString()
                    }
                ) {
                    return@setPositiveButton
                }

                val position = dynamicFields.size
                dynamicFields.add(
                    position,
                    ActionFieldBinder(
                        binding.widgetTextConfigService.text.toString(),
                        fieldKeyInput.text.toString(),
                    ),
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
            private var ongoingJob: Job? = null

            @SuppressLint("NotifyDataSetChanged")
            override fun afterTextChanged(p0: Editable?) {
                val actionText: String = p0.toString()
                // To make sure only one job at the time is updating the data we keep a reference to the job and cancel
                // it if it gets call again.
                ongoingJob?.cancel()
                ongoingJob = lifecycleScope.launch {
                    if (actions[selectedServerId].orEmpty().keys.contains(actionText)) {
                        Timber.d("Valid domain and action--processing dynamic fields")

                        // Make sure there are not already any dynamic fields created
                        // This can happen if selecting the drop-down twice or pasting
                        dynamicFields.clear()

                        // We only call this if servicesAvailable was fetched and is not null,
                        // so we can safely assume that it is not null here
                        val actionData = actions[selectedServerId]!![actionText]!!.actionData
                        val target = actionData.target
                        val fields = actionData.fields

                        val fieldKeys = fields.keys
                        Timber.d("Fields applicable to this action: $fields")

                        val existingActionData = mutableMapOf<String, Any?>()
                        val addedFields = mutableListOf<String>()
                        getSavedWidget()?.let { buttonWidget ->
                            if (
                                buttonWidget.serverId != selectedServerId ||
                                "${buttonWidget.domain}.${buttonWidget.service}" != actionText
                            ) {
                                return@let
                            }

                            val dbMap: Map<String, Any?> = kotlinJsonMapper.decodeFromString(
                                MapAnySerializer,
                                buttonWidget.serviceData,
                            )
                            for (item in dbMap) {
                                val value =
                                    item.value.toString().replace("[", "").replace("]", "") +
                                        if (item.key == "entity_id") ", " else ""
                                existingActionData[item.key] = value.ifEmpty { null }
                                addedFields.add(item.key)
                            }
                        }

                        if (target != false) {
                            dynamicFields.add(
                                0,
                                ActionFieldBinder(
                                    actionText,
                                    "entity_id",
                                    existingActionData["entity_id"] ?: prefilledEntityFor(actionText),
                                ),
                            )
                        }

                        fieldKeys.sorted().forEach { fieldKey ->
                            Timber.d("Creating a text input box for $fieldKey")

                            // Insert a dynamic layout
                            // IDs get priority and go at the top, since the other fields
                            // are usually optional but the ID is required
                            if (fieldKey.contains("_id")) {
                                dynamicFields.add(
                                    0,
                                    ActionFieldBinder(actionText, fieldKey, existingActionData[fieldKey]),
                                )
                            } else {
                                dynamicFields.add(ActionFieldBinder(actionText, fieldKey, existingActionData[fieldKey]))
                            }
                        }
                        addedFields.minus("entity_id").minus(fieldKeys).forEach { extraFieldKey ->
                            Timber.d("Creating a text input box for extra $extraFieldKey")
                            dynamicFields.add(
                                ActionFieldBinder(actionText, extraFieldKey, existingActionData[extraFieldKey]),
                            )
                        }
                        dynamicFieldAdapter.notifyDataSetChanged()
                    } else {
                        if (dynamicFields.isNotEmpty()) {
                            dynamicFields.clear()
                            dynamicFieldAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        }
        )

    /** Returns the saved widget being edited. A widget being added has no ID yet, so there's nothing to look up. */
    private suspend fun getSavedWidget(): ButtonWidgetEntity? =
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) null else dao.get(appWidgetId)

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
        binding.root.applySafeDrawingInsets()

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            selectedEntity = extras.getString(FOR_ENTITY)
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
            val buttonWidget = getSavedWidget()
            if (buttonWidget != null) {
                val actionText = "${buttonWidget.domain}.${buttonWidget.service}"
                binding.widgetTextConfigService.setText(actionText)
                binding.label.setText(buttonWidget.label)
                binding.backgroundType.setSelection(
                    WidgetUtils.getSelectedBackgroundOption(
                        this@ButtonWidgetConfigureActivity,
                        buttonWidget.backgroundType,
                        backgroundTypeValues,
                    ),
                )
                binding.textColor.isVisible = buttonWidget.backgroundType == WidgetBackgroundType.TRANSPARENT
                binding.textColorWhite.isChecked =
                    buttonWidget.textColor?.let {
                        it.toColorInt() == ContextCompat.getColor(
                            this@ButtonWidgetConfigureActivity,
                            android.R.color.white,
                        )
                    }
                        ?: true
                binding.textColorBlack.isChecked =
                    buttonWidget.textColor?.let {
                        it.toColorInt() ==
                            ContextCompat.getColor(
                                this@ButtonWidgetConfigureActivity,
                                commonR.color.colorWidgetButtonLabelBlack,
                            )
                    }
                        ?: false

                binding.addButton.setText(commonR.string.update_widget)

                binding.widgetCheckboxRequireAuthentication.isChecked = buttonWidget.requireAuthentication
            } else {
                binding.backgroundType.setSelection(0)
            }

            setupServerSelect(buttonWidget?.serverId)

            // Do this off the main thread, takes a second or two...
            runOnUiThread {
                // Create an icon pack and load all drawables.
                val iconName = buttonWidget?.iconName ?: "mdi:flash"
                val icon = Mdi.fromHaName(iconName) ?: Mdi.Flash
                onIconDialogIconsSelected(icon)
                binding.widgetConfigIconSelector.setOnClickListener {
                    var alertDialog: DialogFragment? = null

                    alertDialog = IconDialogFragment(
                        callback = {
                            onIconDialogIconsSelected(it)
                            alertDialog?.dismiss()
                        },
                    )

                    alertDialog.show(supportFragmentManager, IconDialogFragment.TAG)
                }
            }
        }

        actionAdapter = SingleItemArrayAdapter(this) {
            if (it != null) getActionString(it) else ""
        }
        binding.widgetTextConfigService.setAdapter(actionAdapter)
        binding.widgetTextConfigService.onFocusChangeListener = dropDownOnFocus

        lifecycleScope.launch {
            // Prefilling needs the server before actions finish loading, which may be before setupServerSelect sets it
            if (selectedEntity != null && selectedServerId == null) {
                selectedServerId = serverManager.getServer()?.id
            }
            serverManager.servers().forEach { server ->
                launch {
                    try {
                        actions[server.id] = HashMap()
                        serverManager.integrationRepository(server.id).getServices()?.forEach {
                            actions[server.id]!![getActionString(it)] = it
                        }
                        if (server.id == selectedServerId) setAdapterActions(server.id)
                    } catch (e: Exception) {
                        // Custom components can cause actions to not load
                        // Display error text
                        Timber.e(e, "Unable to load actions from Home Assistant")
                        if (server.id == selectedServerId) binding.widgetConfigServiceError.visibility = View.VISIBLE
                    }
                }
                launch {
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
        }

        binding.widgetTextConfigService.addTextChangedListener(actionTextWatcher)

        binding.backgroundType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.textColor.isVisible =
                    parent?.adapter?.getItem(position) == getString(commonR.string.widget_background_type_transparent)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                binding.textColor.isVisible = false
            }
        }

        binding.addFieldButton.setOnClickListener(onAddFieldListener)
        binding.addButton.setOnClickListener {
            if (requestLauncherSetup) {
                val widgetConfigAction = binding.widgetTextConfigService.text.toString()
                if (SdkVersion.isAtLeast(Build.VERSION_CODES.O) &&
                    selectedServerId != null &&
                    (
                        widgetConfigAction in actions[selectedServerId].orEmpty().keys ||
                            widgetConfigAction.split(".", limit = 2).size == 2
                        )
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

        dynamicFieldAdapter = WidgetDynamicFieldAdapter(HashMap(), HashMap(), dynamicFields)
        binding.widgetConfigFieldsLayout.adapter = dynamicFieldAdapter
        binding.widgetConfigFieldsLayout.layoutManager = LinearLayoutManager(this)
    }

    override fun onServerSelected(serverId: Int) {
        selectedEntity = null
        binding.widgetTextConfigService.setText("")
        setAdapterActions(serverId)
    }

    override suspend fun getPendingDaoEntity(): ButtonWidgetEntity {
        val serverId = checkNotNull(selectedServerId) { "Selected server ID is null" }
        val actionText = binding.widgetTextConfigService.text.toString()
        val actions = actions[serverId].orEmpty()
        val actionTextParts = actionText.split(".", limit = 2)
        val domain = actions[actionText]?.domain ?: actionTextParts.getOrElse(0) { "" }
        val action = actions[actionText]?.action ?: actionTextParts.getOrElse(1) { "" }
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

        return ButtonWidgetEntity(
            id = appWidgetId,
            serverId = serverId,
            domain = domain,
            service = action,
            label = binding.label.text.toString(),
            iconName = binding.widgetConfigIconSelector.tag as String,
            serviceData = kotlinJsonMapper.encodeToString(MapAnySerializer, actionDataMap),
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
            requireAuthentication = binding.widgetCheckboxRequireAuthentication.isChecked,
        )
    }

    override val widgetClass: Class<*> = ButtonWidget::class.java

    @SuppressLint("NotifyDataSetChanged")
    private fun setAdapterActions(serverId: Int) {
        Timber.d("Actions found: $actions")
        actionAdapter?.clearAll()
        if (actions[serverId] != null) {
            actionAdapter?.addAll(actions[serverId]?.values.orEmpty().toMutableList())
            actionAdapter?.sort()
        }
        dynamicFieldAdapter.replaceValues(
            actions[serverId].orEmpty() as HashMap<String, Action>,
            entities[serverId].orEmpty() as HashMap<String, Entity>,
        )

        prefillDefaultAction(serverId)
        actionTextWatcher.afterTextChanged(binding.widgetTextConfigService.text)

        // Update action adapter
        runOnUiThread {
            val actionText = binding.widgetTextConfigService.text.toString()
            // Keep the other actions of the domain listed while the box only holds the default we picked
            val isPrefilled = actionText == prefilledAction
            actionAdapter?.filter?.filter(if (isPrefilled) "${actionText.substringBefore('.')}." else actionText)
        }
    }

    /**
     * Fills in a default action for [selectedEntity], because its entity ID field only exists once an action is
     * set. Does nothing if the action box already has text, so it never overwrites what the user typed.
     */
    private fun prefillDefaultAction(serverId: Int) {
        val entityId = selectedEntity
        if (entityId == null || prefilledAction != null || binding.widgetTextConfigService.text.isNotEmpty()) return
        val domain = entityId.substringBefore('.')

        val actionsWithTarget = actions[serverId].orEmpty().values
            .filter { it.domain == domain && it.actionData.target != false }
        val defaultAction = PREFERRED_DEFAULT_ACTIONS.firstNotNullOfOrNull { preferred ->
            actionsWithTarget.find { it.action == preferred }
        } ?: actionsWithTarget.minByOrNull { it.action } ?: return

        prefilledAction = getActionString(defaultAction)
        binding.widgetTextConfigService.setText(prefilledAction, false)
    }

    /**
     * Returns the entity ID field's value for [actionText], in the format the field uses for saved widgets, when
     * the action is in the same domain as [selectedEntity].
     */
    private fun prefilledEntityFor(actionText: String): String? = selectedEntity
        ?.takeIf { it.substringBefore('.') == actionText.substringBefore('.') }
        ?.let { "$it, " }

    private fun onIconDialogIconsSelected(selectedIcon: MdiIcon) {
        binding.widgetConfigIconSelector.tag = selectedIcon.mdiName
        binding.widgetConfigIconSelector.setImageBitmap(
            selectedIcon.toBitmap(this, ICON_PREVIEW_SIZE_DP, ContextCompat.getColor(this, commonR.color.colorIcon)),
        )
    }
}
