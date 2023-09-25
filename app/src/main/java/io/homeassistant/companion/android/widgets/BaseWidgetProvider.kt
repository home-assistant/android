package io.homeassistant.companion.android.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A widget provider class for widgets that update based on entity state changes.
 */
abstract class BaseWidgetProvider : AppWidgetProvider() {

    companion object {
        const val UPDATE_VIEW =
            "io.homeassistant.companion.android.widgets.template.BaseWidgetProvider.UPDATE_VIEW"
        const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.template.TemplateWidget.RECEIVE_DATA"

        var widgetScope: CoroutineScope? = null
        val widgetEntities = mutableMapOf<Int, List<String>>()
        val widgetJobs = mutableMapOf<Int, Job>()
    }

    @Inject
    protected lateinit var serverManager: ServerManager

    private var thisSetScope = false
    protected var lastIntent = ""

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

    fun onScreenOn(context: Context) {
        setupWidgetScope()
        if (!serverManager.isRegistered()) return
        widgetScope!!.launch {
            updateAllWidgets(context)

            val allWidgets = getAllWidgetIdsWithEntities(context)
            val widgetsWithDifferentEntities = allWidgets.filter { it.value.second != widgetEntities[it.key] }
            if (widgetsWithDifferentEntities.isNotEmpty()) {
                context.applicationContext.registerReceiver(
                    this@BaseWidgetProvider,
                    IntentFilter(Intent.ACTION_SCREEN_OFF)
                )

                widgetsWithDifferentEntities.forEach { (id, pair) ->
                    widgetJobs[id]?.cancel()

                    val (serverId, entities) = pair.first to pair.second
                    val entityUpdates =
                        if (serverManager.getServer(serverId) != null) {
                            serverManager.integrationRepository(serverId).getEntityUpdates(entities)
                        } else {
                            null
                        }
                    if (entityUpdates != null) {
                        widgetEntities[id] = entities
                        widgetJobs[id] = widgetScope!!.launch {
                            entityUpdates.collect {
                                onEntityStateChanged(context, id, it)
                            }
                        }
                    } else { // Remove data to make it retry on the next update
                        widgetEntities.remove(id)
                        widgetJobs.remove(id)
                    }
                }
            }
        }
    }

    private fun onScreenOff() {
        if (thisSetScope) {
            widgetScope?.cancel()
            thisSetScope = false
            widgetEntities.clear()
            widgetJobs.clear()
        }
    }

    private suspend fun updateAllWidgets(
        context: Context
    ) {
        val widgetProvider = getWidgetProvider(context)
        val systemWidgetIds = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(widgetProvider)
            .toSet()
        val dbWidgetIds = getAllWidgetIdsWithEntities(context).keys

        val invalidWidgetIds = dbWidgetIds.minus(systemWidgetIds)
        if (invalidWidgetIds.isNotEmpty()) {
            Log.i(
                widgetProvider.shortClassName,
                "Found widgets $invalidWidgetIds in database, but not in AppWidgetManager - sending onDeleted"
            )
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

    protected fun removeSubscription(appWidgetId: Int) {
        widgetEntities.remove(appWidgetId)
        widgetJobs[appWidgetId]?.cancel()
        widgetJobs.remove(appWidgetId)
    }

    abstract fun getWidgetProvider(context: Context): ComponentName
    abstract suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, suggestedEntity: Entity<Map<String, Any>>? = null): RemoteViews

    // A map of widget IDs to [server ID, list of entity IDs]
    abstract suspend fun getAllWidgetIdsWithEntities(context: Context): Map<Int, Pair<Int, List<String>>>
    abstract fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int)
    abstract suspend fun onEntityStateChanged(context: Context, appWidgetId: Int, entity: Entity<*>)
}
