package io.homeassistant.companion.android.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.repositories.BaseDaoWidgetRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.database.widget.ThemeableWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.hasActiveConnection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A widget provider class for widgets that update based on entity state changes.
 */
abstract class BaseWidgetProvider<T : BaseDaoWidgetRepository<*>, WidgetDataType> : AppWidgetProvider() {

    companion object {
        const val UPDATE_VIEW =
            "io.homeassistant.companion.android.widgets.UPDATE_VIEW"
        const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.RECEIVE_DATA"

        var widgetScope: CoroutineScope? = null
        var widgetWorkScope: CoroutineScope? = null
        val widgetEntities = mutableMapOf<Int, List<String>>()
        val widgetJobs = mutableMapOf<Int, Job>()
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var repository: T

    protected var thisSetScope = false
    protected var lastIntent = ""

    init {
        setupWidgetScope()
    }

    private fun setupWidgetScope() {
        if (widgetScope == null || !widgetScope!!.isActive) {
            widgetScope = CoroutineScope(Dispatchers.Main + Job())
            widgetWorkScope = CoroutineScope(Dispatchers.IO + Job())
            thisSetScope = true
        }
    }

    private suspend fun updateAllWidgets(
        context: Context
    ) {
        val widgetProvider = getWidgetProvider(context)
        val systemWidgetIds = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(widgetProvider)
            .toSet()
        val dbWidgetIds = getAllWidgetIdsWithEntities().keys

        val invalidWidgetIds = dbWidgetIds.minus(systemWidgetIds)
        if (invalidWidgetIds.isNotEmpty()) {
            Log.i(
                getWidgetProvider(context).shortClassName,
                "Found widgets $invalidWidgetIds in database, but not in AppWidgetManager - sending onDeleted"
            )
            onDeleted(context, invalidWidgetIds.toIntArray())
        }

        dbWidgetIds.filter { systemWidgetIds.contains(it) }.forEach {
            forceUpdateView(context, it)
        }
    }

    fun forceUpdateView(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    ) {
        widgetScope?.launch {
            val hasActiveConnection = context.hasActiveConnection()
            val views = getWidgetRemoteViews(context, appWidgetId, hasActiveConnection)
            Log.d(getWidgetProvider(context).shortClassName, "Updating Widget View updateAppWidget() hasActiveConnection: $hasActiveConnection")
            appWidgetManager.updateAppWidget(appWidgetId, views)
            onWidgetsViewUpdated(context, appWidgetId, appWidgetManager, views, hasActiveConnection)
        }
    }

    private suspend fun getAllWidgetIdsWithEntities(): Map<Int, Pair<Int, List<String>>> =
        repository.getAllFlow()
            .first()
            .associate {
                it.id to (it.serverId to listOf(it.entityId.orEmpty()))
            }

    open fun onScreenOn(context: Context) {
        setupWidgetScope()
        if (!serverManager.isRegistered()) return
        widgetScope!!.launch {
            updateAllWidgets(context)

            val allWidgets = getAllWidgetIdsWithEntities()
            val widgetsWithDifferentEntities = allWidgets.filter { it.value.second != widgetEntities[it.key] }
            if (widgetsWithDifferentEntities.isNotEmpty()) {
                ContextCompat.registerReceiver(
                    context.applicationContext,
                    this@BaseWidgetProvider,
                    IntentFilter(Intent.ACTION_SCREEN_OFF),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )

                widgetsWithDifferentEntities.forEach { (id, pair) ->
                    widgetJobs[id]?.cancel()

                    val (serverId, entities) = pair.first to pair.second
                    val entityUpdates =
                        if (serverManager.getServer(serverId) != null) {
                            getUpdates(serverId, entities)
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

    abstract suspend fun getUpdates(serverId: Int, entityIds: List<String>): Flow<WidgetDataType>?

    private fun onScreenOff() {
        if (thisSetScope) {
            widgetWorkScope?.cancel()
            widgetScope?.cancel()
            thisSetScope = false
            widgetEntities.clear()
            widgetJobs.clear()
        }
    }

    fun setWidgetBackground(views: RemoteViews, layoutId: Int, widget: ThemeableWidgetEntity?) {
        when (widget?.backgroundType) {
            WidgetBackgroundType.TRANSPARENT -> {
                views.setInt(layoutId, "setBackgroundColor", Color.TRANSPARENT)
            }

            else -> {
                views.setInt(layoutId, "setBackgroundResource", R.drawable.widget_button_background)
            }
        }
    }

    protected fun removeSubscription(appWidgetId: Int) {
        widgetEntities.remove(appWidgetId)
        widgetJobs[appWidgetId]?.cancel()
        widgetJobs.remove(appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        widgetScope?.launch {
            repository.deleteAll(appWidgetIds)
            appWidgetIds.forEach { removeSubscription(it) }
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
                forceUpdateView(context, appWidgetId, appWidgetManager)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        lastIntent = intent.action.toString()
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        Log.d(
            getWidgetProvider(context).shortClassName,
            "Broadcast received: " + System.lineSeparator() +
                "Broadcast action: " + lastIntent + System.lineSeparator() +
                "AppWidgetId: " + appWidgetId
        )

        super.onReceive(context, intent)
        when (lastIntent) {
            UPDATE_VIEW -> forceUpdateView(context, appWidgetId)
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

    open fun onWidgetsViewUpdated(context: Context, appWidgetId: Int, appWidgetManager: AppWidgetManager, remoteViews: RemoteViews, hasActiveConnection: Boolean) {
        Log.d(
            getWidgetProvider(context).shortClassName,
            "onWidgetsViewUpdated() received, AppWidgetId: $appWidgetId hasActiveConnection: $hasActiveConnection"
        )
    }

    open suspend fun onEntityStateChanged(context: Context, appWidgetId: Int, entity: WidgetDataType) {
        Log.d(
            getWidgetProvider(context).shortClassName,
            "onEntityStateChanged(), AppWidgetId: $appWidgetId"
        )
    }

    abstract fun getWidgetProvider(context: Context): ComponentName
    abstract suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, hasActiveConnection: Boolean, suggestedEntity: WidgetDataType? = null): RemoteViews

    // A map of widget IDs to [server ID, list of entity IDs]
    abstract fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int)
}
