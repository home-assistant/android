package io.homeassistant.companion.android.widgets.template

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.widgets.DaggerProviderComponent
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TemplateWidget : AppWidgetProvider() {
    companion object {
        private const val TAG = "TemplateWidget"
        private const val UPDATE_VIEW =
            "io.homeassistant.companion.android.widgets.template.TemplateWidget.UPDATE_VIEW"
        internal const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.template.TemplateWidget.RECEIVE_DATA"

        internal const val EXTRA_TEMPLATE = "extra_template"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    private lateinit var templateWidgetDao: TemplateWidgetDao

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        templateWidgetDao = AppDatabase.getInstance(context).templateWidgetDao()
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            mainScope.launch {
                val views = getWidgetRemoteViews(context, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        templateWidgetDao = AppDatabase.getInstance(context).templateWidgetDao()
        // When the user deletes the widget, delete the preference associated with it.
        for (appWidgetId in appWidgetIds) {
            templateWidgetDao.delete(appWidgetId)
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

        templateWidgetDao = AppDatabase.getInstance(context).templateWidgetDao()
        val templateWidgetList = templateWidgetDao.getAll()

        super.onReceive(context, intent)
        when (action) {
            UPDATE_VIEW -> updateView(context, appWidgetId)
            RECEIVE_DATA -> saveEntityConfiguration(context, intent.extras, appWidgetId)
            Intent.ACTION_SCREEN_ON -> updateAllWidgets(context, templateWidgetList)
        }
    }

    private suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        // Every time AppWidgetManager.updateAppWidget(...) is called, the button listener
        // and label need to be re-assigned, or the next time the layout updates
        // (e.g home screen rotation) the widget will fall back on its default layout
        // without any click listener being applied

        val intent = Intent(context, TemplateWidget::class.java).apply {
            action = UPDATE_VIEW
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val widget = templateWidgetDao.get(appWidgetId)

        return RemoteViews(context.packageName, R.layout.widget_template).apply {

            setOnClickPendingIntent(
                R.id.widgetLayout,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            if (widget != null) {
                var renderedTemplate = "Loading"
                try {
                    renderedTemplate = integrationUseCase.renderTemplate(widget.template, mapOf())
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to render template: ${widget.template}", e)
                    Toast.makeText(context, R.string.widget_template_error, Toast.LENGTH_LONG).show()
                }
                setTextViewText(
                    R.id.widgetTemplateText,
                    renderedTemplate
                )
            }
        }
    }

    private fun updateView(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    ) {
        if (!isConnectionActive(context)) {
            Log.d(TAG, "Skipping widget update since network connection is not active")
            return
        }

        Log.d(TAG, "Updating Template Widget View: $appWidgetId")
        mainScope.launch {
            val views = getWidgetRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun updateAllWidgets(
        context: Context,
        templateWidgetList: Array<TemplateWidgetEntity>?
    ) {
        if (templateWidgetList != null) {
            Log.d(TAG, "Updating all widgets")
            for (item in templateWidgetList) {
                updateView(context, item.id)
            }
        }
    }

    private fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val template: String? = extras.getString(EXTRA_TEMPLATE)

        if (template == null) {
            Log.e(TAG, "Did not receive complete widget data")
            return
        }

        mainScope.launch {
            templateWidgetDao.add(
                TemplateWidgetEntity(
                    appWidgetId,
                    template
                )
            )
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

    private fun isConnectionActive(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo?.isConnected ?: false
    }
}
