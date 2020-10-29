package io.homeassistant.companion.android.widgets.entity

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout.GONE
import android.widget.LinearLayout.VISIBLE
import android.widget.MultiAutoCompleteTextView.CommaTokenizer
import android.widget.Toast
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.widgets.DaggerProviderComponent
import io.homeassistant.companion.android.widgets.common.SingleItemArrayAdapter
import javax.inject.Inject
import kotlinx.android.synthetic.main.widget_static_configure.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class EntityWidgetConfigureActivity : BaseActivity() {

    companion object {
        private const val TAG: String = "StaticWidgetConfigAct"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private var entities = LinkedHashMap<String, Entity<Any>>()

    private var selectedEntity: Entity<Any>? = null
    private var appendAttributes: Boolean = false
    private var selectedAttributeIds: ArrayList<String> = ArrayList()

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        setContentView(R.layout.widget_static_configure)

        add_button.setOnClickListener(addWidgetButtonClickListener)

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

        val staticWidgetDao = AppDatabase.getInstance(applicationContext).staticWidgetDao()
        val staticWidget = staticWidgetDao.get(appWidgetId)
        if (staticWidget != null) {
            widget_text_config_entity_id.setText(staticWidget.entityId)
            label.setText(staticWidget.label)
            textSize.setText(staticWidget.textSize.toInt().toString())
            state_separator.setText(staticWidget.stateSeparator)
            val entity = runBlocking {
                try {
                    integrationUseCase.getEntity(staticWidget.entityId)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to get entity information", e)
                    Toast.makeText(applicationContext, R.string.widget_entity_fetch_error, Toast.LENGTH_LONG)
                        .show()
                    null
                }
            }

            if (!staticWidget.attributeIds.isNullOrEmpty()) {
                append_attribute_value_checkbox.isChecked = true
                appendAttributes = true
                for (item in staticWidget.attributeIds.split(','))
                    selectedAttributeIds.add(item)
                widget_text_config_attribute.setText(staticWidget.attributeIds.replace(",", ", "))
                attribute_value_linear_layout.visibility = VISIBLE
                attribute_separator.setText(staticWidget.attributeSeparator)
            }
            if (entity != null) {
                selectedEntity = entity as Entity<Any>?
                setupAttributes()
            }
            add_button.setText(R.string.update_widget)
            delete_button.visibility = VISIBLE
            delete_button.setOnClickListener(onDeleteWidget)
        }
        val entityAdapter = SingleItemArrayAdapter<Entity<Any>>(this) { it?.entityId ?: "" }

        widget_text_config_entity_id.setAdapter(entityAdapter)
        widget_text_config_entity_id.onFocusChangeListener = dropDownOnFocus
        widget_text_config_entity_id.onItemClickListener = entityDropDownOnItemClick
        widget_text_config_attribute.onFocusChangeListener = dropDownOnFocus
        widget_text_config_attribute.onItemClickListener = attributeDropDownOnItemClick
        widget_text_config_attribute.setOnClickListener {
            if (!widget_text_config_attribute.isPopupShowing) widget_text_config_attribute.showDropDown()
        }

        append_attribute_value_checkbox.setOnCheckedChangeListener { _, isChecked ->
            attribute_value_linear_layout.visibility = if (isChecked) VISIBLE else GONE
            appendAttributes = isChecked
        }

        mainScope.launch {
            try {
                // Fetch entities
                val fetchedEntities = integrationUseCase.getEntities()
                fetchedEntities.sortBy { e -> e.entityId }
                fetchedEntities.forEach {
                    entities[it.entityId] = it
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
        AdapterView.OnItemClickListener { parent, view, position, id ->
            selectedEntity = parent.getItemAtPosition(position) as Entity<Any>?
            setupAttributes()
        }

    private val attributeDropDownOnItemClick =
        AdapterView.OnItemClickListener { parent, _, position, _ ->
            selectedAttributeIds.add(parent.getItemAtPosition(position) as String)
        }

    private fun setupAttributes() {
        val fetchedAttributes = selectedEntity?.attributes as Map<String, String>
        val attributesAdapter = ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line)
        widget_text_config_attribute.setAdapter(attributesAdapter)
        attributesAdapter.addAll(*fetchedAttributes.keys.toTypedArray())
        widget_text_config_attribute.setTokenizer(CommaTokenizer())
        runOnUiThread {
            attributesAdapter.notifyDataSetChanged()
        }
    }

    private var addWidgetButtonClickListener = View.OnClickListener {
        try {

            val context = this@EntityWidgetConfigureActivity

            // Set up a broadcast intent and pass the service call data as extras
            val intent = Intent()
            intent.action = EntityWidget.RECEIVE_DATA
            intent.component = ComponentName(context, EntityWidget::class.java)

            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

            val entity: String = if (selectedEntity == null)
                widget_text_config_entity_id.text.toString()
            else
                selectedEntity!!.entityId
            intent.putExtra(
                EntityWidget.EXTRA_ENTITY_ID,
                entity
            )

            intent.putExtra(
                EntityWidget.EXTRA_LABEL,
                label.text.toString()
            )

            intent.putExtra(
                EntityWidget.EXTRA_TEXT_SIZE,
                textSize.text.toString()
            )

            intent.putExtra(
                EntityWidget.EXTRA_STATE_SEPARATOR,
                state_separator.text.toString()
            )

            if (appendAttributes) {
                val attributes = if (selectedAttributeIds.isNullOrEmpty())
                    widget_text_config_attribute.text.toString()
                else
                    selectedAttributeIds
                intent.putExtra(
                    EntityWidget.EXTRA_ATTRIBUTE_IDS,
                    attributes
                )

                intent.putExtra(
                    EntityWidget.EXTRA_ATTRIBUTE_SEPARATOR,
                    attribute_separator.text.toString()
                )
            }

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
        val context = this@EntityWidgetConfigureActivity
        deleteConfirmation(context)
    }

    private fun deleteConfirmation(context: Context) {
        val staticWidgetDao = AppDatabase.getInstance(context).staticWidgetDao()

        val builder: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(context)

        builder.setTitle(R.string.confirm_delete_this_widget_title)
        builder.setMessage(R.string.confirm_delete_this_widget_message)

        builder.setPositiveButton(
            R.string.confirm_positive
        ) { dialog, _ ->
            staticWidgetDao.delete(appWidgetId)
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
