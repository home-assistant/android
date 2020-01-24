package io.homeassistant.companion.android.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.Entity
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.integration.Service
import javax.inject.Inject
import kotlinx.android.synthetic.main.widget_button_configure.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ButtonWidgetConfigureActivity : Activity() {
    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    private lateinit var services: ArrayAdapter<Service>
    private lateinit var entities: ArrayAdapter<Entity<Any>>

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private var onClickListener = View.OnClickListener {
        val context = this@ButtonWidgetConfigureActivity

        // Set up a broadcast intent and pass the service call data as extras
        val intent = Intent()
        intent.action = ButtonWidget.RECEIVE_DATA
        intent.component = ComponentName(context, ButtonWidget::class.java)
        intent.putExtra(
            ButtonWidget.EXTRA_DOMAIN,
            services.getItem(context.service.selectedItemPosition)?.domain
        )
        intent.putExtra(
            ButtonWidget.EXTRA_SERVICE,
            services.getItem(context.service.selectedItemPosition)?.service
        )
        intent.putExtra(
            ButtonWidget.EXTRA_ENTITY_ID,
            entities.getItem(entity_id.selectedItemPosition)?.entityId
        )
        intent.putExtra(
            ButtonWidget.EXTRA_LABEL,
            label.text.toString()
        )
        intent.putExtra(
            ButtonWidget.EXTRA_ICON,
            context.widget_config_spinner.selectedItemId.toInt()
        )
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        context.sendBroadcast(intent)

        // Make sure we pass back the original appWidgetId
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    public override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        DaggerProviderComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        setContentView(R.layout.widget_button_configure)

        services = SingleItemArrayAdapter(this) {
            if (it != null) "${it.domain}.${it.service}" else ""
        }
        service.adapter = services

        entities = SingleItemArrayAdapter(this) {
            it?.entityId ?: ""
        }
        entity_id.adapter = entities

        mainScope.launch {

            services.addAll(integrationUseCase.getServices().sortedBy { it.domain + it.service })
            entities.addAll(integrationUseCase.getEntities().sortedBy { it.entityId })
            entities.insert(null, 0)
            runOnUiThread {
                services.notifyDataSetChanged()
                entities.notifyDataSetChanged()
            }
        }

        add_button.setOnClickListener(onClickListener)

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

        // Set up icon spinner
        val icons = intArrayOf(
            R.drawable.ic_flash_on_black_24dp,
            R.drawable.ic_lightbulb_outline_black_24dp,
            R.drawable.ic_home_black_24dp,
            R.drawable.ic_power_settings_new_black_24dp
        )

        widget_config_spinner.adapter = ButtonWidgetConfigSpinnerAdaptor(this, icons)
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }
}
