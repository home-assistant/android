package io.homeassistant.companion.android.widgets.template

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Html.fromHtml
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.getAttribute
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import kotlinx.coroutines.launch
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class TemplateWidget : BaseWidgetProvider() {
    companion object {
        private const val TAG = "TemplateWidget"
        internal const val EXTRA_TEMPLATE = "extra_template"
        internal const val EXTRA_TEXT_SIZE = "EXTRA_TEXT_SIZE"
        internal const val EXTRA_BACKGROUND_TYPE = "EXTRA_BACKGROUND_TYPE"
        internal const val EXTRA_TEXT_COLOR = "EXTRA_TEXT_COLOR"
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val templateWidgetDao = AppDatabase.getInstance(context).templateWidgetDao()
        // When the user deletes the widget, delete the preference associated with it.
        mainScope.launch {
            templateWidgetDao.deleteAll(appWidgetIds)
        }
    }

    override suspend fun getAllWidgetIds(context: Context): List<Int> {
        val templateWidgetDao = AppDatabase.getInstance(context).templateWidgetDao()
        return templateWidgetDao.getAll().map { it.id }
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

        val useDynamicColors = widget?.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable()
        return RemoteViews(context.packageName, if (useDynamicColors) R.layout.widget_template_wrapper_dynamiccolor else R.layout.widget_template_wrapper_default).apply {
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
                // Theming
                if (widget.backgroundType == WidgetBackgroundType.TRANSPARENT) {
                    var textColor = context.getAttribute(R.attr.colorWidgetOnBackground, ContextCompat.getColor(context, commonR.color.colorWidgetButtonLabel))
                    widget.textColor?.let { textColor = it.toColorInt() }

                    setInt(R.id.widgetLayout, "setBackgroundColor", Color.TRANSPARENT)
                    setTextColor(R.id.widgetTemplateText, textColor)
                }

                // Content
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
                setTextViewTextSize(
                    R.id.widgetTemplateText,
                    TypedValue.COMPLEX_UNIT_SP,
                    widget.textSize
                )
            } else {
                setTextViewText(R.id.widgetTemplateText, "")
            }
        }
    }

    override fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val template: String? = extras.getString(EXTRA_TEMPLATE)
        val textSize: Float = extras.getFloat(EXTRA_TEXT_SIZE)
        val backgroundTypeSelection: WidgetBackgroundType = extras.getSerializable(EXTRA_BACKGROUND_TYPE) as WidgetBackgroundType
        val textColorSelection: String? = extras.getString(EXTRA_TEXT_COLOR)

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
                    textSize,
                    templateWidgetDao.get(appWidgetId)?.lastUpdate ?: "Loading",
                    backgroundTypeSelection,
                    textColorSelection
                )
            )
            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    override suspend fun onEntityStateChanged(context: Context, entity: Entity<*>) {
        getAllWidgetIds(context).forEach { appWidgetId ->
            val intent = Intent(context, TemplateWidget::class.java).apply {
                action = UPDATE_VIEW
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            context.sendBroadcast(intent)
        }
    }
}
