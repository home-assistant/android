package io.homeassistant.companion.android.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import io.homeassistant.companion.android.R
import kotlinx.android.synthetic.main.widget_button_configure.*

class ButtonWidgetConfigureActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private var onClickListener = View.OnClickListener {
        val context = this@ButtonWidgetConfigureActivity

        // When the button is clicked, store the service call data locally
        saveServiceCallData(
            context, appWidgetId,
            context.widget_text_config_domain.text.toString(),
            context.widget_text_config_service.text.toString(),
            context.widget_text_config_service_data.text.toString()
        )

        // Save the label text and set the TextView of the widget
        val labelText: String = context.widget_text_config_label.text.toString()
        saveStringPref(context, appWidgetId, PREF_KEY_LABEL, labelText)

        val views = RemoteViews(context.packageName, R.layout.widget_button)
        views.setTextViewText(R.id.widgetLabel, labelText)

        // It is the responsibility of the configuration activity to update the app widget
        AppWidgetManager.getInstance(context)
            .updateAppWidget(appWidgetId, views)

        // Make sure we pass back the original appWidgetId
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        // An issue I encountered: rather than the RESULT_OK calling ACTION_APPWIDGET_UPDATE,
        // the update is being called at creation of the activity and not at the end.
        // I am adding here an update broadcast specifically to get the widget to update post-config
        val intent = Intent()
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        context.sendBroadcast(intent)

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
    }
}
