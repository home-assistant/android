package io.homeassistant.companion.android.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.google.gson.Gson
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.widgets.WidgetUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ButtonWidget : AppWidgetProvider() {
    companion object {
        private const val TAG = "ButtonWidget"
        private const val CALL_SERVICE =
            "io.homeassistant.companion.android.widgets.ButtonWidget.CALL_SERVICE"
        internal const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.ButtonWidget.RECEIVE_DATA"

        internal const val EXTRA_DOMAIN = "EXTRA_DOMAIN"
        internal const val EXTRA_SERVICE = "EXTRA_SERVICE"
        internal const val EXTRA_SERVICE_DATA = "EXTRA_SERVICE_DATA"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val EXTRA_ICON = "EXTRA_ICON"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase
    @Inject
    lateinit var widgetStorage: WidgetUseCase

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            mainScope.launch {
                val views = getWidgetRemoteViews(context, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
        for (appWidgetId in appWidgetIds) {
            mainScope.launch {
                widgetStorage.deleteWidgetData(appWidgetId)
            }
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        Log.d(
            TAG, "Broadcast received: " + System.lineSeparator() +
                    "Broadcast action: " + action + System.lineSeparator() +
                    "AppWidgetId: " + appWidgetId
        )

        ensureInjected(context)

        super.onReceive(context, intent)
        when (action) {
            CALL_SERVICE -> callConfiguredService(context, appWidgetId)
            RECEIVE_DATA -> saveServiceCallConfiguration(context, intent.extras, appWidgetId)
        }
    }

    private suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        // Every time AppWidgetManager.updateAppWidget(...) is called, the button listener
        // and label need to be re-assigned, or the next time the layout updates
        // (e.g home screen rotation) the widget will fall back on its default layout
        // without any click listener being applied

        val intent = Intent(context, ButtonWidget::class.java).apply {
            action = CALL_SERVICE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val views = RemoteViews(context.packageName, R.layout.widget_button).apply {
            val iconName = widgetStorage.loadIcon(appWidgetId) ?: "ic_flash_on_black_24dp"
            val icon = context.resources.getIdentifier(iconName, "drawable", `package`)

            setImageViewResource(
                R.id.widgetImageButton,
                icon
            )
            setOnClickPendingIntent(
                R.id.widgetImageButtonLayout,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            setTextViewText(
                R.id.widgetLabel,
                widgetStorage.loadLabel(appWidgetId)
            )
        }

        return views
    }

    private fun callConfiguredService(context: Context, appWidgetId: Int) {
        Log.d(TAG, "Calling widget service")

        // Set up progress bar as immediate feedback to show the click has been received
        // Success or failure feedback will come from the mainScope coroutine
        val loadingViews: RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_button)
        val appWidgetManager = AppWidgetManager.getInstance(context)

        loadingViews.setViewVisibility(R.id.widgetProgressBar, View.VISIBLE)
        loadingViews.setViewVisibility(R.id.widgetImageButtonLayout, View.GONE)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, loadingViews)

        mainScope.launch {
            // Change color of background image for feedback
            var views = getWidgetRemoteViews(context, appWidgetId)

            // Set default feedback as negative
            var feedbackColor = R.drawable.widget_button_background_red
            var feedbackIcon = R.drawable.ic_clear_black

            // Load the service call data from Shared Preferences
            val domain = widgetStorage.loadDomain(appWidgetId)
            val service = widgetStorage.loadService(appWidgetId)
            val serviceDataJson = widgetStorage.loadServiceData(appWidgetId)

            Log.d(
                TAG, "Service Call Data loaded:" + System.lineSeparator() +
                        "domain: " + domain + System.lineSeparator() +
                        "service: " + service + System.lineSeparator() +
                        "service_data: " + serviceDataJson
            )

            if (domain == null || service == null || serviceDataJson == null) {
                Log.w(TAG, "Service Call Data incomplete.  Aborting service call")
            } else {
                // If everything loaded correctly, package the service data and attempt the call
                try {
                    // Convert JSON to HashMap
                    val serviceDataMap =
                        Gson().fromJson(serviceDataJson, HashMap<String, Any>()::class.java)

                    integrationUseCase.callService(domain, service, serviceDataMap)

                    // If service call does not throw an exception, send positive feedback
                    feedbackColor = R.drawable.widget_button_background_green
                    feedbackIcon = R.drawable.ic_check_black_24dp
                } catch (e: Exception) {
                    Log.e(TAG, "Could not send service call.", e)
                }
            }

            // Update widget and set visibilities for feedback
            views.setInt(R.id.widgetLayout, "setBackgroundResource", feedbackColor)
            views.setImageViewResource(R.id.widgetImageButton, feedbackIcon)
            views.setViewVisibility(R.id.widgetProgressBar, View.INVISIBLE)
            views.setViewVisibility(R.id.widgetLabelLayout, View.GONE)
            views.setViewVisibility(R.id.widgetImageButtonLayout, View.VISIBLE)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Reload default views in the coroutine to pass to the post handler
            views = getWidgetRemoteViews(context, appWidgetId)

            // Set a timer to change it back after 1 second
            Handler().postDelayed({
                views.setViewVisibility(R.id.widgetLabelLayout, View.VISIBLE)
                views.setInt(
                    R.id.widgetLayout,
                    "setBackgroundResource",
                    R.drawable.widget_button_background_white
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }, 1000)
        }
    }

    private fun saveServiceCallConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val domain: String? = extras.getString(EXTRA_DOMAIN)
        val service: String? = extras.getString(EXTRA_SERVICE)
        val serviceData: String? = extras.getString(EXTRA_SERVICE_DATA)
        val label: String? = extras.getString(EXTRA_LABEL)
        val icon: Int = extras.getInt(EXTRA_ICON)

        if (domain == null || service == null || serviceData == null) {
            Log.e(TAG, "Did not receive complete service call data")
            return
        }

        mainScope.launch {
            Log.d(
                TAG, "Saving service call config data:" + System.lineSeparator() +
                        "domain: " + domain + System.lineSeparator() +
                        "service: " + service + System.lineSeparator() +
                        "service_data: " + serviceData + System.lineSeparator() +
                        "label: " + label
            )

            widgetStorage.saveServiceCallData(
                appWidgetId,
                domain,
                service,
                serviceData
            )
            widgetStorage.saveLabel(appWidgetId, label)

            val iconName = context.resources.getResourceEntryName(icon)
            widgetStorage.saveIcon(appWidgetId, iconName)

            // It is the responsibility of the configuration activity to update the app widget
            // This method is only called during the initial setup of the widget,
            // so rather than duplicating code in the ButtonWidgetConfigurationActivity,
            // it is just calling onUpdate manually here.
            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    private fun ensureInjected(context: Context) {
        if (context.applicationContext is GraphComponentAccessor) {
            DaggerProviderComponent.builder()
                .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
                .build()
                .inject(this)
        } else {
            throw Exception("Application Context passed is not of our application!")
        }
    }
}
