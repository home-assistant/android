package io.homeassistant.companion.android.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Spinner
import io.homeassistant.companion.android.R
import kotlinx.android.synthetic.main.widget_button_configure.*

class ButtonWidgetConfigureActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private var onClickListener = View.OnClickListener {
        val context = this@ButtonWidgetConfigureActivity

        // Set up a broadcast intent and pass the service call data as extras
        val intent = Intent()
        intent.action = ButtonWidget.RECEIVE_DATA
        intent.component = ComponentName(context, ButtonWidget::class.java)
        intent.putExtra(
            ButtonWidget.EXTRA_DOMAIN,
            context.widget_text_config_domain.text.toString()
        )
        intent.putExtra(
            ButtonWidget.EXTRA_SERVICE,
            context.widget_text_config_service.text.toString()
        )
        intent.putExtra(
            ButtonWidget.EXTRA_SERVICE_DATA,
            context.widget_text_config_service_data.text.toString()
        )
        intent.putExtra(
            ButtonWidget.EXTRA_LABEL,
            context.widget_text_config_label.text.toString()
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

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        setContentView(R.layout.widget_button_configure)
        findViewById<View>(R.id.add_button).setOnClickListener(onClickListener)

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

        findViewById<Spinner>(R.id.widget_config_spinner).adapter =
            ButtonWidgetConfigSpinnerAdaptor(this, icons)
    }
}
