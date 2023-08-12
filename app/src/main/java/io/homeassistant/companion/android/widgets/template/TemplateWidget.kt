package io.homeassistant.companion.android.widgets.template

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.text.HtmlCompat
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.getAttribute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class TemplateWidget : AppWidgetProvider() {
    companion object {
        private const val TAG = "TemplateWidget"

        const val UPDATE_VIEW =
            "io.homeassistant.companion.android.widgets.template.TemplateWidget.UPDATE_VIEW"
        const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.template.TemplateWidget.RECEIVE_DATA"

        internal const val EXTRA_SERVER_ID = "EXTRA_SERVER_ID"
        internal const val EXTRA_TEMPLATE = "extra_template"
        internal const val EXTRA_TEXT_SIZE = "EXTRA_TEXT_SIZE"
        internal const val EXTRA_BACKGROUND_TYPE = "EXTRA_BACKGROUND_TYPE"
        internal const val EXTRA_TEXT_COLOR = "EXTRA_TEXT_COLOR"

        private var widgetScope: CoroutineScope? = null
        private val widgetTemplates = mutableMapOf<Int, String>()
        private val widgetJobs = mutableMapOf<Int, Job>()
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var templateWidgetDao: TemplateWidgetDao

    private var thisSetScope = false
    private var lastIntent = ""

    init {
        setupWidgetScope()
    }

    private fun setupWidgetScope() {
        if (widgetScope == null || !widgetScope!!.isActive) {
            widgetScope = CoroutineScope(Dispatchers.Main + Job())
            thisSetScope = true
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            widgetScope?.launch {
                val views = getWidgetRemoteViews(context, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
        widgetScope?.launch {
            templateWidgetDao.deleteAll(appWidgetIds)
            appWidgetIds.forEach {
                widgetTemplates.remove(it)
                widgetJobs[it]?.cancel()
                widgetJobs.remove(it)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        lastIntent = intent.action.toString()
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        super.onReceive(context, intent)
        when (lastIntent) {
            UPDATE_VIEW -> updateView(context, appWidgetId)
            RECEIVE_DATA -> {
                saveEntityConfiguration(
                    context,
                    intent.extras,
                    appWidgetId
                )
                onScreenOn(context)
            }
            Intent.ACTION_SCREEN_ON -> onScreenOn(context)
            Intent.ACTION_SCREEN_OFF -> onScreenOff()
        }
    }

    private fun onScreenOn(context: Context) {
        setupWidgetScope()
        if (!serverManager.isRegistered()) return
        widgetScope!!.launch {
            updateAllWidgets(context)

            val allWidgets = templateWidgetDao.getAll()
            val widgetsWithDifferentTemplate = allWidgets.filter { it.template != widgetTemplates[it.id] }
            if (widgetsWithDifferentTemplate.isNotEmpty()) {
                if (thisSetScope) {
                    context.applicationContext.registerReceiver(
                        this@TemplateWidget,
                        IntentFilter(Intent.ACTION_SCREEN_OFF)
                    )
                }

                widgetsWithDifferentTemplate.forEach { widget ->
                    widgetJobs[widget.id]?.cancel()

                    val templateUpdates =
                        if (serverManager.getServer(widget.serverId) != null) {
                            serverManager.integrationRepository(widget.serverId).getTemplateUpdates(widget.template)
                        } else {
                            null
                        }
                    if (templateUpdates != null) {
                        widgetTemplates[widget.id] = widget.template
                        widgetJobs[widget.id] = widgetScope!!.launch {
                            templateUpdates.collect {
                                onTemplateChanged(context, widget.id, it)
                            }
                        }
                    } else { // Remove data to make it retry on the next update
                        widgetTemplates.remove(widget.id)
                        widgetJobs.remove(widget.id)
                    }
                }
            }
        }
    }

    private fun onScreenOff() {
        if (thisSetScope) {
            widgetScope?.cancel()
            thisSetScope = false
            widgetTemplates.clear()
            widgetJobs.clear()
        }
    }

    private suspend fun updateAllWidgets(
        context: Context
    ) {
        val systemWidgetIds = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TemplateWidget::class.java))
            .toSet()
        val dbWidgetIds = templateWidgetDao.getAll().map { it.id }

        val invalidWidgetIds = dbWidgetIds.minus(systemWidgetIds)
        if (invalidWidgetIds.isNotEmpty()) {
            Log.i(TAG, "Found widgets $invalidWidgetIds in database, but not in AppWidgetManager - sending onDeleted")
            onDeleted(context, invalidWidgetIds.toIntArray())
        }

        dbWidgetIds.filter { systemWidgetIds.contains(it) }.forEach {
            updateView(context, it)
        }
    }

    private fun updateView(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    ) {
        widgetScope?.launch {
            val views = getWidgetRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, suggestedTemplate: String? = null): RemoteViews {
        // Every time AppWidgetManager.updateAppWidget(...) is called, the button listener
        // and label need to be re-assigned, or the next time the layout updates
        // (e.g home screen rotation) the widget will fall back on its default layout
        // without any click listener being applied

        val intent = Intent(context, TemplateWidget::class.java).apply {
            action = UPDATE_VIEW
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

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
                var renderedTemplate: String? = templateWidgetDao.get(appWidgetId)?.lastUpdate ?: context.getString(commonR.string.loading)
                try {
                    renderedTemplate = suggestedTemplate ?: serverManager.integrationRepository(widget.serverId).renderTemplate(widget.template, mapOf()).toString()
                    templateWidgetDao.updateTemplateWidgetLastUpdate(
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

    private fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val serverId = if (extras.containsKey(EXTRA_SERVER_ID)) extras.getInt(EXTRA_SERVER_ID) else null
        val template: String? = extras.getString(EXTRA_TEMPLATE)
        val textSize: Float = extras.getFloat(EXTRA_TEXT_SIZE)
        val backgroundTypeSelection: WidgetBackgroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getSerializable(EXTRA_BACKGROUND_TYPE, WidgetBackgroundType::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getSerializable(EXTRA_BACKGROUND_TYPE) as? WidgetBackgroundType
        } ?: WidgetBackgroundType.DAYNIGHT
        val textColorSelection: String? = extras.getString(EXTRA_TEXT_COLOR)

        if (serverId == null || template == null) {
            Log.e(TAG, "Did not receive complete widget data")
            return
        }

        widgetScope?.launch {
            templateWidgetDao.add(
                TemplateWidgetEntity(
                    appWidgetId,
                    serverId,
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

    private fun onTemplateChanged(context: Context, appWidgetId: Int, template: String?) {
        widgetScope?.launch {
            val views = getWidgetRemoteViews(context, appWidgetId, template)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }
    }
}
