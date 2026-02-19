package io.homeassistant.companion.android.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.launchAsync
import io.homeassistant.companion.android.database.widget.WidgetDao
import io.homeassistant.companion.android.database.widget.WidgetEntity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A widget provider class for widgets that update based on entity state changes.
 */
abstract class BaseWidgetProvider<T : WidgetEntity<T>, DAO : WidgetDao<T>> : AppWidgetProvider() {

    companion object {
        const val UPDATE_VIEW =
            "io.homeassistant.companion.android.widgets.template.BaseWidgetProvider.UPDATE_VIEW"
        const val UPDATE_WIDGETS =
            "io.homeassistant.companion.android.widgets.UPDATE_WIDGETS"

        private var widgetScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private val widgetEntities = mutableMapOf<Int, List<String>>()
        private val widgetJobs = mutableMapOf<Int, Job>()
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var dao: DAO

    protected var lastIntent = ""

    init {
        setupWidgetScope()
    }

    private fun setupWidgetScope() {
        if (!widgetScope.isActive) {
            Companion.widgetScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            widgetScope.launch {
                updateView(context, appWidgetId, appWidgetManager)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        super.onReceive(context, intent)

        setupWidgetScope()

        when (intent.action.toString()) {
            UPDATE_VIEW -> launchAsync(widgetScope) { updateView(context, appWidgetId) }
            UPDATE_WIDGETS, Intent.ACTION_SCREEN_ON -> launchAsync(widgetScope) { onScreenOn(context) }
            Intent.ACTION_SCREEN_OFF -> onScreenOff()
            ACTION_APPWIDGET_CREATED -> {
                launchAsync(widgetScope) {
                    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                        FailFast.fail { "Missing appWidgetId in intent to add widget in DAO" }
                    } else {
                        // Use deprecated function to not have to specify the class of T
                        @Suppress("DEPRECATION", "UNCHECKED_CAST")
                        val entity = intent.getSerializableExtra(EXTRA_WIDGET_ENTITY) as? T
                        entity?.let {
                            dao.add(entity.copyWithWidgetId(appWidgetId))
                        } ?: FailFast.fail { "Missing $EXTRA_WIDGET_ENTITY or it's of the wrong type in intent." }
                    }
                    onScreenOn(context)
                }
            }

            else -> launchAsync(widgetScope) { onReceiveIntentNotHandled(context, intent, appWidgetId) }
        }
    }

    abstract suspend fun onReceiveIntentNotHandled(context: Context, intent: Intent, appWidgetId: Int)

    suspend fun onScreenOn(context: Context) {
        if (!serverManager.isRegistered()) return
        updateAllWidgets(context)

        val allWidgets = getAllWidgetIdsWithEntities(context)
        val widgetsWithDifferentEntities = allWidgets.filter { it.value.second != widgetEntities[it.key] }
        if (widgetsWithDifferentEntities.isNotEmpty()) {
            ContextCompat.registerReceiver(
                context.applicationContext,
                this@BaseWidgetProvider,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED,
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
                    widgetJobs[id] = widgetScope.launch {
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

    private fun onScreenOff() {
        try {
            widgetScope.cancel()
        } catch (e: IllegalStateException) {
            Timber.w(e, "Calling onScreenOff without any job started")
        }
        widgetEntities.clear()
        widgetJobs.clear()
    }

    protected val widgetScope
        get() = Companion.widgetScope

    private suspend fun updateAllWidgets(context: Context) {
        val widgetProvider = getWidgetProvider(context)
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val systemWidgetIds = appWidgetManager.getAppWidgetIds(widgetProvider).toSet()
        val dbWidgetIds = getAllWidgetIdsWithEntities(context).keys

        val invalidWidgetIds = dbWidgetIds.minus(systemWidgetIds)
        if (invalidWidgetIds.isNotEmpty()) {
            Timber.tag(widgetProvider.shortClassName).i(
                "Found widgets $invalidWidgetIds in database, but not in AppWidgetManager - sending onDeleted",
            )
            onDeleted(context, invalidWidgetIds.toIntArray())
        }

        dbWidgetIds.filter { systemWidgetIds.contains(it) }.forEach {
            updateView(context, it)
        }
    }

    protected suspend fun updateView(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context),
    ) {
        val views = getWidgetRemoteViews(context, appWidgetId)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    protected fun removeSubscription(appWidgetId: Int) {
        widgetEntities.remove(appWidgetId)
        widgetJobs[appWidgetId]?.cancel()
        widgetJobs.remove(appWidgetId)
    }

    abstract fun getWidgetProvider(context: Context): ComponentName
    abstract suspend fun getWidgetRemoteViews(
        context: Context,
        appWidgetId: Int,
        suggestedEntity: Entity? = null,
    ): RemoteViews

    // A map of widget IDs to [server ID, list of entity IDs]
    abstract suspend fun getAllWidgetIdsWithEntities(context: Context): Map<Int, Pair<Int, List<String>>>
    abstract suspend fun onEntityStateChanged(context: Context, appWidgetId: Int, entity: Entity)
}
