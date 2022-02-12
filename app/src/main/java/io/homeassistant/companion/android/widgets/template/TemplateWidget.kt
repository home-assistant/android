package io.homeassistant.companion.android.widgets.template

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html.fromHtml
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TemplateWidget : BaseWidgetProvider() {
    companion object {
        private const val TAG = "TemplateWidget"
        internal const val EXTRA_TEMPLATE = "extra_template"
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val templateWidgetDao = AppDatabase.getInstance(context).templateWidgetDao()
        // When the user deletes the widget, delete the preference associated with it.
        for (appWidgetId in appWidgetIds) {
            templateWidgetDao.delete(appWidgetId)
        }
    }

    override fun getAllWidgetIds(context: Context): List<Int> {
        val templateWidgetDao = AppDatabase.getInstance(context).templateWidgetDao()
        return templateWidgetDao.getAll()?.map { it.id }.orEmpty()
    }

    override suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, suggestedEntity: Entity<Map<String, Any>>?): RemoteViews {
        // Every time AppWidgetManager.updateAppWidget(...) is called, the button listener
        // and label need to be re-assigned, or the next time the layout updates
        // (e.g home screen rotation) the widget will fall back on its default layout
        // without any click listener being applied

        val intent = Intent(context, TemplateWidget::class.java).apply {
            action = UPDATE_VIEW
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val templateWidgetDao = AppDatabase.getInstance(context).templateWidgetDao()
        val widget = templateWidgetDao.get(appWidgetId)

        return RemoteViews(context.packageName, R.layout.widget_template).apply {

            setOnClickPendingIntent(
                R.id.widgetLayout,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            if (widget != null) {
                var renderedTemplate = templateWidgetDao.get(appWidgetId)?.lastUpdate ?: "Loading"
                try {
                    renderedTemplate = integrationUseCase.renderTemplate(widget.template, mapOf())
                    templateWidgetDao.updateTemplateWidgetLastUpdate(appWidgetId, renderedTemplate)
                    setViewVisibility(R.id.widgetTemplateError, View.GONE)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to render template: ${widget.template}", e)
                    setViewVisibility(R.id.widgetTemplateError, View.VISIBLE)
                }
                setTextViewText(
                    R.id.widgetTemplateText,
                    fromHtml(renderedTemplate)
                )
            }
        }
    }

    override fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val template: String? = extras.getString(EXTRA_TEMPLATE)

        if (template == null) {
            Log.e(TAG, "Did not receive complete widget data")
            return
        }
        val templateWidgetDao = AppDatabase.getInstance(context).templateWidgetDao()

        mainScope.launch {
            templateWidgetDao.add(
                TemplateWidgetEntity(
                    appWidgetId,
                    template,
                    templateWidgetDao.get(appWidgetId)?.lastUpdate ?: "Loading"
                )
            )
            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    override fun onEntityStateChanged(context: Context, entity: Entity<*>) {
        getAllWidgetIds(context).forEach { appWidgetId ->
            val intent = Intent(context, TemplateWidget::class.java).apply {
                action = UPDATE_VIEW
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            context.sendBroadcast(intent)
        }
    }
}
