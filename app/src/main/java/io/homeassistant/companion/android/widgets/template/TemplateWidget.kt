package io.homeassistant.companion.android.widgets.template

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.os.BundleCompat
import androidx.core.text.HtmlCompat
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.repositories.TemplateWidgetRepository
import io.homeassistant.companion.android.util.getAttribute
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TemplateWidget : BaseWidgetProvider<TemplateWidgetRepository, String?>() {

    companion object {
        private const val TAG = "TemplateWidget"

        internal const val EXTRA_SERVER_ID = "EXTRA_SERVER_ID"
        internal const val EXTRA_TEMPLATE = "extra_template"
        internal const val EXTRA_TEXT_SIZE = "EXTRA_TEXT_SIZE"
        internal const val EXTRA_BACKGROUND_TYPE = "EXTRA_BACKGROUND_TYPE"
        internal const val EXTRA_TEXT_COLOR = "EXTRA_TEXT_COLOR"
    }

    override fun getWidgetProvider(context: Context): ComponentName =
        ComponentName(context, TemplateWidget::class.java)

    override suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, hasActiveConnection: Boolean, suggestedEntity: String?): RemoteViews {
        // Every time AppWidgetManager.updateAppWidget(...) is called, the button listener
        // and label need to be re-assigned, or the next time the layout updates
        // (e.g home screen rotation) the widget will fall back on its default layout
        // without any click listener being applied

        val intent = Intent(context, TemplateWidget::class.java).apply {
            action = UPDATE_VIEW
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val widget = repository.get(appWidgetId)

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

                    setTextColor(R.id.widgetTemplateText, textColor)
                }

                setWidgetBackground(this, R.id.widgetLayout, widget)

                // Content
                var renderedTemplate: String? = repository.get(appWidgetId)?.lastUpdate ?: context.getString(commonR.string.loading)
                try {
                    renderedTemplate = suggestedEntity ?: serverManager.integrationRepository(widget.serverId).renderTemplate(widget.template, mapOf()).toString()
                    repository.updateWidgetLastUpdate(
                        appWidgetId,
                        renderedTemplate
                    )
                    setViewVisibility(R.id.widgetTemplateError, View.GONE)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to render template: ${widget.template}", e)
                    setViewVisibility(R.id.widgetTemplateError, View.VISIBLE)
                }
                setTextViewText(
                    R.id.widgetTemplateText,
                    renderedTemplate?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY) }
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

    override fun onWidgetsViewUpdated(context: Context, appWidgetId: Int, appWidgetManager: AppWidgetManager, remoteViews: RemoteViews, hasActiveConnection: Boolean) {
        super.onWidgetsViewUpdated(context, appWidgetId, appWidgetManager, remoteViews, hasActiveConnection)
        remoteViews.setViewVisibility(
            R.id.widgetTemplateError,
            if (!hasActiveConnection) View.VISIBLE else View.GONE
        )
    }

    override suspend fun getUpdates(serverId: Int, entityIds: List<String>): Flow<String?>? {
        return serverManager.integrationRepository(serverId).getTemplateUpdates(entityIds.first())
    }

    override fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val serverId = if (extras.containsKey(EXTRA_SERVER_ID)) extras.getInt(EXTRA_SERVER_ID) else null
        val template: String? = extras.getString(EXTRA_TEMPLATE)
        val textSize: Float = extras.getFloat(EXTRA_TEXT_SIZE)
        val backgroundTypeSelection = BundleCompat.getSerializable(extras, EXTRA_BACKGROUND_TYPE, WidgetBackgroundType::class.java)
            ?: WidgetBackgroundType.DAYNIGHT
        val textColorSelection: String? = extras.getString(EXTRA_TEXT_COLOR)

        if (serverId == null || template == null) {
            Log.e(TAG, "Did not receive complete widget data")
            return
        }

        widgetScope?.launch {
            repository.add(
                TemplateWidgetEntity(
                    appWidgetId,
                    serverId,
                    "template_$appWidgetId",
                    template,
                    textSize,
                    repository.get(appWidgetId)?.lastUpdate ?: ContextCompat.getString(context, commonR.string.loading),
                    backgroundTypeSelection,
                    textColorSelection
                )
            )
        }
    }
}
