package io.homeassistant.companion.android.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.database.widget.WidgetDao
import io.homeassistant.companion.android.database.widget.WidgetEntity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

private fun newCoroutineScopeProvider(): () -> CoroutineScope {
    val exceptionHandler =
        CoroutineExceptionHandler { _, throwable -> Timber.e(throwable, "Unhandled error in widget scope") }
    // Use SupervisorJob to avoid cancelling all jobs when one fails since the scope is used across all the widgets
    return { CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler) }
}

data class EntitiesPerServer(val serverId: Int, val entityIds: List<String>)

/**
 * Base class for Glance widgets that handles the persistence in the database of a created widget and update of the
 * widget based on entity state changes.
 *
 * This class provides the foundational functionality for managing widget updates, handling lifecycle events,
 * and observing entity state changes. It is designed to be extended by specific widget implementations.
 *
 * ### Key Features:
 * - **Widget entity persistence**: Persist the [WidgetEntity] given while creating the widget using [EXTRA_WIDGET_ENTITY] inside the [DAO].
 * - **Entity state observation**: Watches for updates to specific entities and triggers widget updates accordingly.
 * - **Lifecycle management**: Handles widget lifecycle action [Intent.ACTION_SCREEN_ON], [Intent.ACTION_SCREEN_OFF] and [AppWidgetManager.ACTION_APPWIDGET_UPDATE].
 * - **Real-time updates**: Supports real-time updates for widgets when the user grants the necessary permissions.
 * - **Cleanup**: Cancel subscription when widget are removed and make sure the database reflect the current widgets used.
 *
 * ### Notes:
 * - Glance widget, may "freeze" subscriptions after some time unless the user grants
 *   the `real-time` permission. To unfreeze, call `update` on the widget.
 * - Ensure that data sources are observed within the composition state (e.g., using [androidx.compose.runtime.collectAsState]) to keep
 *   widgets updated while the composition is active (less than a minute even with the `real-time` permission).
 * - Make sure to invoke [registerReceiver] to register actions to manage widget updates effectively.
 * - Even if we are watching for entity changes in the receiver the data received cannot be sent to the widget directly,
 *   if you need to send data to the widget you need to update the [DAO] and call [update].
 * - The changes are watched only after [Intent.ACTION_SCREEN_ON]. It means that on startup the widget won't watch for changes
 *   until the screen is turned off and on again.
 *
 * ### Usage:
 * Extend this class and implement the abstract method `getWidgetEntitiesByServer` to provide the mapping of widget IDs
 * to their associated entities.
 *
 * ```kotlin
 * class MyWidgetReceiver : BaseGlanceWidgetReceiver<MyWidgetDao>() {
 *     override suspend fun getWidgetEntitiesByServer(context: Context): Map<Int, EntitiesPerServer> {
 *         // Return a map of widget IDs to their associated entities
 *     }
 * }
 * ```
 *
 * Register the widget in the [io.homeassistant.companion.android.HomeAssistantApplication]:
 * ```kotlin
 * // In io.homeassistant.companion.android.HomeAssistantApplication
 * override fun onCreate() {
 *     super.onCreate()
 *     ...
 *     MyWidgetReceiver().registerReceiver(this)
 * }
 * ```
 *
 * ### Implementation details:
 * - **Coroutine scope**: A shared [CoroutineScope] is used to manage asynchronous tasks for all widgets of a given class. It uses
 *   [SupervisorJob] to ensure that one failing job does not cancel others.
 * - **Entity updates**: Subscriptions to entity updates are managed per widget. When a widget is deleted, its
 *   subscriptions are canceled, and its data is removed from the database.
 *
 * @param DAO The type of the DAO used for managing widget data in the database. It must implement [WidgetDao] and
 *            be injectable with Hilt.
 */
abstract class BaseGlanceEntityWidgetReceiver<T : WidgetEntity<T>, DAO : WidgetDao<T>> @VisibleForTesting constructor(
    private val widgetScopeProvider: () -> CoroutineScope,
    private val glanceManagerProvider: (Context) -> GlanceAppWidgetManager,
) : GlanceAppWidgetReceiver() {

    constructor() : this(newCoroutineScopeProvider(), { GlanceAppWidgetManager(it) })

    @Inject
    lateinit var dao: DAO

    @Inject
    lateinit var serverManager: ServerManager

    private var widgetScope: CoroutineScope = widgetScopeProvider()

    private val widgetJobs = mutableMapOf<Int, Job>()

    // Helper to identify the widget in the logs
    private val widgetClassName by lazy { glanceAppWidget.javaClass.name }

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        Timber.v("Received intent action = ${intent.action} for widget = $appWidgetId ($widgetClassName) $this")

        // Handle the Glance [androidx.glance.action.Action]
        super.onReceive(context, intent)

        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> startWatchingForEntitiesChanges(context)
            Intent.ACTION_SCREEN_OFF -> stopWatchingForEntitiesChanges()
            /**
             * This action will trigger an update for all widgets but will not monitor for changes.
             *
             * WARNING: This action is received from a different instance of this class than the others above, so you should not start something that
             * needs to be canceled here like [startWatchingForEntitiesChanges] otherwise it won't ever be canceled.
             *
             * Extract from the action documentation:
             * > This action may be sent in response to a new instance of this AppWidget provider being instantiated,
             * the requested {@link AppWidgetProviderInfo#updatePeriodMillis update interval} elapsing, or the system booting.
             */
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                widgetScope.launch {
                    glanceAppWidget.updateAll(context)
                }
            }
            ACTION_APPWIDGET_CREATED -> {
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    FailFast.fail { "Missing appWidgetId in intent to add widget in DAO" }
                } else {
                    widgetScope.launch {
                        // Use deprecated function to not have to specify the class of T
                        @Suppress("DEPRECATION", "UNCHECKED_CAST")
                        val entity = intent.getSerializableExtra(EXTRA_WIDGET_ENTITY) as? T
                        entity?.let {
                            dao.add(entity.copyWithWidgetId(appWidgetId))
                        } ?: FailFast.fail { "Missing $EXTRA_WIDGET_ENTITY or it's of the wrong type in intent." }
                    }
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        deleteWidgetsFromDatabase(appWidgetIds)
    }

    /**
     * Register this receiver to receive [Intent.ACTION_SCREEN_ON] and [Intent.ACTION_SCREEN_OFF].
     * It doesn't exported the receiver.
     */
    fun registerReceiver(context: Context) {
        ContextCompat.registerReceiver(
            context,
            this,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun deleteWidgetsFromDatabase(appWidgetIds: IntArray) {
        widgetScope.launch {
            dao.deleteAll(appWidgetIds)
            appWidgetIds.forEach(::removeSubscription)
        }
    }

    private fun setupWidgetScope() {
        if (!widgetScope.isActive) {
            widgetScope = widgetScopeProvider()
        }
    }

    private fun startWatchingForEntitiesChanges(context: Context) {
        setupWidgetScope()
        widgetScope.launch {
            if (!serverManager.isRegistered()) {
                Timber.tag(widgetClassName).d("No server registered won't watch for entities")
                return@launch
            }

            val entitiesPerServer = cleanupOrphansWidgetAndGetEntitiesByServer(context)

            entitiesPerServer.forEach { (appWidgetId, entitiesPerServer) ->
                widgetJobs[appWidgetId]?.cancel()

                val serverId = entitiesPerServer.serverId
                val entitiesId = entitiesPerServer.entityIds

                val entityUpdatesFlow = if (serverManager.getServer(serverId) != null) {
                    serverManager.integrationRepository(serverId).getEntityUpdates(entitiesId)
                } else {
                    null
                }
                if (entityUpdatesFlow != null) {
                    widgetJobs[appWidgetId] = widgetScope.launch {
                        Timber.tag(
                            widgetClassName,
                        ).d("Watching updates of entities($entitiesId) for widget $appWidgetId")
                        entityUpdatesFlow.collect {
                            onEntityUpdate(context, appWidgetId, it)
                            updateView(context, appWidgetId)
                        }
                    }
                } else {
                    Timber.tag(widgetClassName).w("Entity updates is null for widget $appWidgetId not watching")
                    // Shouldn't do anything since the job should have been canceled already
                    removeSubscription(appWidgetId)
                }
            }
        }
    }

    private fun stopWatchingForEntitiesChanges() {
        widgetScope.cancel()
        widgetJobs.clear()
    }

    private fun removeSubscription(appWidgetId: Int) {
        widgetJobs.remove(appWidgetId)?.cancel()
    }

    private suspend fun cleanupOrphansWidgetAndGetEntitiesByServer(context: Context): Map<Int, EntitiesPerServer> {
        val manager = glanceManagerProvider(context)
        val glanceIds = manager.getGlanceIds(glanceAppWidget.javaClass)

        val systemWidgetIds = glanceIds.map { manager.getAppWidgetId(it) }

        val entitiesPerServer = getWidgetEntitiesByServer(context)

        val invalidWidgetIds = entitiesPerServer.keys.minus(systemWidgetIds)

        if (invalidWidgetIds.isNotEmpty()) {
            Timber.tag(widgetClassName).i(
                "Found widgets $invalidWidgetIds in database, but not in AppWidgetManager - removing it from the database.",
            )
            deleteWidgetsFromDatabase(invalidWidgetIds.toIntArray())
        }

        val entitiesPerServerWithoutInvalid = entitiesPerServer - invalidWidgetIds

        return entitiesPerServerWithoutInvalid
    }

    /**
     * From https://cs.android.com/androidx/platform/frameworks/support/+/a2cb47e01ac0b5f3427ef4e61719c0f1aaba4fc1:glance/glance-appwidget/src/main/java/androidx/glance/appwidget/GlanceAppWidget.kt;l=78
     *
     * Note: [update] and [updateAll] do not restart `provideGlance` if it is already running. As a
     * result, you should load initial data before calling `provideContent`, and then observe your
     * sources of data within the composition (e.g. [androidx.compose.runtime.collectAsState]). This
     * ensures that your widget will continue to update while the composition is active. When you
     * update your data source from elsewhere in the app, make sure to call `update` in case a
     * Worker for this widget is not currently running.
     */
    private suspend fun updateView(context: Context, appWidgetId: Int) {
        val manager = glanceManagerProvider(context)
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val glanceId = manager.getGlanceIdBy(appWidgetId)

            glanceAppWidget.update(context, glanceId)
        } else {
            glanceAppWidget.updateAll(context)
        }
    }

    /**
     * Implement this method to provide the mapping of widget IDs to their associated entities and server.
     */
    internal abstract suspend fun getWidgetEntitiesByServer(context: Context): Map<Int, EntitiesPerServer>

    /**
     * Invoked when an entity update is received. After this callback the [updateView] is invoked.
     */
    internal open suspend fun onEntityUpdate(context: Context, appWidgetId: Int, entity: Entity) {}
}
