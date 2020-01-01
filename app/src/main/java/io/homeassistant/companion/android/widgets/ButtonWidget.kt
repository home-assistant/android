package io.homeassistant.companion.android.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ButtonWidget : AppWidgetProvider() {
    companion object {
        private const val TAG = "ButtonWidget"
        private const val CALL_SERVICE = "CALL_SERVICE"
    }

    private lateinit var domain: String
    private lateinit var service: String
    private lateinit var serviceData: String

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            loadServiceCallData(context, appWidgetId)

            val intent = Intent(context, ButtonWidget::class.java).apply {
                action = CALL_SERVICE
                putExtra("appWidgetId", appWidgetId)
            }

            val views = RemoteViews(context.packageName, R.layout.widget_button).apply {
                setOnClickPendingIntent(
                    R.id.widgetImageView,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                setTextViewText(R.id.widgetLabel, loadStringPref(context, appWidgetId, PREF_KEY_LABEL))
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
        for (appWidgetId in appWidgetIds) {
            deleteStringPref(context, appWidgetId, PREF_KEY_DOMAIN)
            deleteStringPref(context, appWidgetId, PREF_KEY_SERVICE)
            deleteStringPref(context, appWidgetId, PREF_KEY_SERVICE_DATA)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Broadcast received: " + System.lineSeparator() +
                "Broadcast action: " + intent.action + System.lineSeparator() +
                "AppWidgetId: " + intent.getIntExtra("appWidgetId", 0))

        super.onReceive(context, intent)
        ensureInjected(context)
        loadServiceCallData(context, intent.getIntExtra("appWidgetId", 0))

        when (intent.action) {
            CALL_SERVICE -> callConfiguredService()
        }
    }

    private fun callConfiguredService() {
        Log.d(TAG, "Calling widget service")

        val serviceDataMap = HashMap<String, String>()
        serviceDataMap["entity_id"] = serviceData

        mainScope.launch {
            try {
                integrationUseCase.callService(domain, service, serviceDataMap)
            } catch (e: Exception) {
                Log.e(TAG, "Could not send service call.", e)
            }
        }
    }

    private fun ensureInjected(context: Context) {
        if (context.applicationContext is GraphComponentAccessor) {
            DaggerWidgetReceiverComponent.builder()
                .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
                .build()
                .inject(this)
        } else {
            throw Exception("Application Context passed is not of our application!")
        }
    }

    private fun loadServiceCallData(context: Context, appWidgetId: Int) {
        // Load the service call data from Shared Preferences
        domain = loadStringPref(context, appWidgetId, PREF_KEY_DOMAIN) ?: ""
        service = loadStringPref(context, appWidgetId, PREF_KEY_SERVICE) ?: ""
        serviceData = loadStringPref(context, appWidgetId, PREF_KEY_SERVICE_DATA) ?: ""

        Log.d(
            TAG, "Service Call Data loaded:" + System.lineSeparator() +
                    "domain: " + domain + System.lineSeparator() +
                    "service: " + service + System.lineSeparator() +
                    "service_data: " + serviceData
        )
    }
}
