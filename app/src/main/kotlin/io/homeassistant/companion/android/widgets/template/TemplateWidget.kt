package io.homeassistant.companion.android.widgets.template

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
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
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.database.widget.TemplateWidgetDao
import io.homeassistant.companion.android.database.widget.TemplateWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.getAttribute
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider.Companion.UPDATE_WIDGETS
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class TemplateWidget : AppWidgetProvider() {
    companion object {
        const val UPDATE_VIEW =
            "io.homeassistant.companion.android.widgets.template.TemplateWidget.UPDATE_VIEW"
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

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
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
            UPDATE_WIDGETS -> onScreenOn(context)
            Intent.ACTION_SCREEN_ON -> onScreenOn(context)
            Intent.ACTION_SCREEN_OFF -> onScreenOff()
            ACTION_APPWIDGET_CREATED -> {
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    FailFast.fail { "Missing appWidgetId in intent to add widget in DAO" }
                } else {
                    widgetScope?.launch {
                        val entity = intent.extras?.let {
                            BundleCompat.getSerializable(
                                it,
                                EXTRA_WIDGET_ENTITY,
                                TemplateWidgetEntity::class.java,
                            )
                        }
                        entity?.let {
                            templateWidgetDao.add(entity.copyWithWidgetId(appWidgetId))
                        } ?: FailFast.fail { "Missing $EXTRA_WIDGET_ENTITY or it's of the wrong type in intent." }
                    }
                }
                onScreenOn(context)
            }
        }
    }

    private fun onScreenOn(context: Context) {
        setupWidgetScope()
        widgetScope!!.launch {
            if (!serverManager.isRegistered()) return@launch
            updateAllWidgets(context)

            val allWidgets = templateWidgetDao.getAll()
            val widgetsWithDifferentTemplate = allWidgets.filter { it.template != widgetTemplates[it.id] }
            if (widgetsWithDifferentTemplate.isNotEmpty()) {
                if (thisSetScope) {
                    ContextCompat.registerReceiver(
                        context.applicationContext,
                        this@TemplateWidget,
                        IntentFilter(Intent.ACTION_SCREEN_OFF),
                        ContextCompat.RECEIVER_NOT_EXPORTED,
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

    private suspend fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val systemWidgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, TemplateWidget::class.java),
        ).toSet()
        val dbWidgetIds = templateWidgetDao.getAll().map { it.id }

        val invalidWidgetIds = dbWidgetIds.minus(systemWidgetIds)
        if (invalidWidgetIds.isNotEmpty()) {
            Timber.i("Found widgets $invalidWidgetIds in database, but not in AppWidgetManager - sending onDeleted")
            onDeleted(context, invalidWidgetIds.toIntArray())
        }

        dbWidgetIds.filter { systemWidgetIds.contains(it) }.forEach {
            updateView(context, it)
        }
    }

    private fun updateView(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
    ) {
        widgetScope?.launch {
            val views = getWidgetRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private suspend fun getWidgetRemoteViews(
        context: Context,
        appWidgetId: Int,
        suggestedTemplate: String? = null,
    ): RemoteViews {
        // Every time AppWidgetManager.updateAppWidget(...) is called, the button listener
        // and label need to be re-assigned, or the next time the layout updates
        // (e.g home screen rotation) the widget will fall back on its default layout
        // without any click listener being applied

        val intent = Intent(context, TemplateWidget::class.java).apply {
            action = UPDATE_VIEW
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val widget = templateWidgetDao.get(appWidgetId)

        val useDynamicColors =
            widget?.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable()
        return RemoteViews(
            context.packageName,
            if (useDynamicColors) {
                R.layout.widget_template_wrapper_dynamiccolor
            } else {
                R.layout.widget_template_wrapper_default
            },
        ).apply {
            setOnClickPendingIntent(
                R.id.widgetLayout,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            if (widget != null) {
                // Theming
                if (widget.backgroundType == WidgetBackgroundType.TRANSPARENT) {
                    var textColor = context.getAttribute(
                        R.attr.colorWidgetOnBackground,
                        ContextCompat.getColor(context, commonR.color.colorWidgetButtonLabel),
                    )
                    widget.textColor?.let { textColor = it.toColorInt() }

                    setInt(R.id.widgetLayout, "setBackgroundColor", Color.TRANSPARENT)
                    setTextColor(R.id.widgetTemplateText, textColor)
                }

                // Content
                var renderedTemplate: String? =
                    templateWidgetDao.get(appWidgetId)?.lastUpdate ?: context.getString(commonR.string.loading)
                try {
                    renderedTemplate =
                        suggestedTemplate
                            ?: serverManager.integrationRepository(
                                widget.serverId,
                            ).renderTemplate(widget.template, mapOf()).toString()
                    templateWidgetDao.updateTemplateWidgetLastUpdate(
                        appWidgetId,
                        renderedTemplate,
                    )
                    setViewVisibility(R.id.widgetTemplateError, View.GONE)
                } catch (e: Exception) {
                    Timber.e(e, "Unable to render template: ${widget.template}")
                    setViewVisibility(R.id.widgetTemplateError, View.VISIBLE)
                }
                setTextViewText(
                    R.id.widgetTemplateText,
                    renderedTemplate?.let {
                        HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY)
                    },
                )
                setTextViewTextSize(
                    R.id.widgetTemplateText,
                    TypedValue.COMPLEX_UNIT_SP,
                    widget.textSize,
                )
            } else {
                setTextViewText(R.id.widgetTemplateText, "")
            }
        }
    }

    private fun onTemplateChanged(context: Context, appWidgetId: Int, template: String?) {
        widgetScope?.launch {
            val views = getWidgetRemoteViews(context, appWidgetId, template)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }
    }
}
