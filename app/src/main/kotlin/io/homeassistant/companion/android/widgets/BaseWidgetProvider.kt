package io.homeassistant.companion.android.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityChange
import io.homeassistant.companion.android.common.data.integration.EntityFilter
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.database.widget.WidgetDao
import io.homeassistant.companion.android.database.widget.WidgetEntity
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import timber.log.Timber

/** The [entities] a widget follows the changes of, and the [job] following them. */
private data class WidgetSubscription(val entities: List<String>, val job: Job)

/**
 * A widget provider class for widgets that update based on entity state changes.
 */
abstract class BaseWidgetProvider<T : WidgetEntity<T>, DAO : WidgetDao<T>> : AppWidgetProvider() {

    companion object {
        const val UPDATE_VIEW =
            "io.homeassistant.companion.android.widgets.template.BaseWidgetProvider.UPDATE_VIEW"
        const val UPDATE_WIDGETS =
            "io.homeassistant.companion.android.widgets.UPDATE_WIDGETS"

        private var widgetScope: CoroutineScope = newCoroutineScopeProvider()
        private val widgetSubscriptions = ConcurrentHashMap<Int, WidgetSubscription>()

        private fun newCoroutineScopeProvider() = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var dao: DAO

    protected var lastIntent = ""

    protected val widgetScope
        get() = Companion.widgetScope

    init {
        setupWidgetScope()
    }

    private fun setupWidgetScope() {
        if (!widgetScope.isActive) {
            Companion.widgetScope = newCoroutineScopeProvider()
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
        lastIntent = intent.action.toString()
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        super.onReceive(context, intent)

        setupWidgetScope()

        when (intent.action.toString()) {
            UPDATE_VIEW -> widgetScope.launch { updateView(context, appWidgetId) }
            UPDATE_WIDGETS, Intent.ACTION_SCREEN_ON -> widgetScope.launch { onScreenOn(context) }
            Intent.ACTION_SCREEN_OFF -> onScreenOff()
            ACTION_APPWIDGET_CREATED -> {
                widgetScope.launch {
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

            else -> widgetScope.launch { onReceiveIntentNotHandled(context, intent, appWidgetId) }
        }
    }

    abstract suspend fun onReceiveIntentNotHandled(context: Context, intent: Intent, appWidgetId: Int)

    suspend fun onScreenOn(context: Context) {
        if (!serverManager.isRegistered()) return
        updateAllWidgets(context)

        val allWidgets = getAllWidgetIdsWithEntities(context)
        val widgetsWithDifferentEntities = allWidgets.filter {
            it.value.second != widgetSubscriptions[it.key]?.entities
        }
        if (widgetsWithDifferentEntities.isNotEmpty()) {
            widgetsWithDifferentEntities.forEach { (id, pair) ->
                widgetSubscriptions[id]?.job?.cancel()

                val (serverId, entities) = pair.first to pair.second
                val entityChanges =
                    if (serverManager.getServer(serverId) != null) {
                        serverManager.integrationRepository(
                            serverId,
                        ).getEntityChanges(EntityFilter(entityIds = entities.toSet()))
                    } else {
                        null
                    }
                if (entityChanges != null) {
                    val job = widgetScope.launch(start = CoroutineStart.LAZY) {
                        entityChanges.collect { change ->
                            when (change) {
                                is EntityChange.StateChanged -> onEntityStateChanged(context, id, change.entity)
                                // Rendered without the entity, like when it cannot be fetched
                                is EntityChange.Removed -> updateView(context, id)
                            }
                        }
                        // Only completes when the changes cannot be followed, remove data to make it
                        // retry on the next update, unless a newer job already replaced this one
                        widgetSubscriptions.remove(id, WidgetSubscription(entities, coroutineContext.job))
                    }
                    widgetSubscriptions[id] = WidgetSubscription(entities, job)
                    job.start()
                } else { // Remove data to make it retry on the next update
                    widgetSubscriptions.remove(id)
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
        widgetSubscriptions.clear()
    }

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
        widgetSubscriptions.remove(appWidgetId)?.job?.cancel()
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
