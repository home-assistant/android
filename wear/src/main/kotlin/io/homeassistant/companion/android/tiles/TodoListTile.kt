@file:OptIn(ExperimentalTime::class)

package io.homeassistant.companion.android.tiles

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.EntityFilter
import io.homeassistant.companion.android.common.data.integration.display.EntitiesForDisplayManager
import io.homeassistant.companion.android.common.data.integration.display.awaitLoadedOrNull
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.GetTodosResponse.TodoItem
import io.homeassistant.companion.android.database.wear.TodoListTile
import io.homeassistant.companion.android.database.wear.TodoListTileDao
import io.homeassistant.companion.android.home.HomeActivity
import io.homeassistant.companion.android.todo.TodoListActivity
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/** A tile showing the open items of a Home Assistant to-do list, tap an item to complete it. */
@AndroidEntryPoint
class TodoListTile : TileService() {

    companion object {
        private const val TAG = "TodoListTile"
        const val DEFAULT_REFRESH_INTERVAL = 600L
        private const val MAX_VISIBLE_ITEMS = 4
        private const val ITEM_PADDING_DP = 3f
        private const val CLICK_COMPLETE_PREFIX = "complete:"
        private const val CLICK_OPEN_LIST = "open_list"
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(serviceJob)

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    @Inject
    lateinit var todoListTileDao: TodoListTileDao

    @Inject
    lateinit var entitiesForDisplayManager: EntitiesForDisplayManager

    @Inject
    lateinit var clock: Clock

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        serviceScope.future {
            val tileId = requestParams.tileId
            val tileConfig = todoListTileDao.get(tileId)
            val lastId = requestParams.currentState.lastClickableId

            if (lastId.isNotEmpty() && wearPrefsRepository.getWearHapticFeedback()) hapticClick(applicationContext)

            val entityId = tileConfig?.entityId
            val timeline = when {
                !serverManager.isRegistered() -> loggedOutTimeline(
                    this@TodoListTile,
                    requestParams,
                    commonR.string.todo_list_tile,
                    commonR.string.todo_list_tile_log_in,
                )
                entityId.isNullOrBlank() -> getNotConfiguredTimeline(
                    this@TodoListTile,
                    requestParams,
                    commonR.string.todo_list_tile_no_entity_yet,
                    HomeActivity.Companion.LaunchMode.TodoListTile,
                )
                else -> listTimeline(requestParams, entityId, lastId)
            }

            Tile.Builder()
                .setResourcesVersion("$TAG$tileId.${clock.now().toEpochMilliseconds()}")
                .setFreshnessIntervalMillis(TimeUnit.SECONDS.toMillis(freshnessSeconds(tileConfig)))
                .setTileTimeline(timeline)
                .build()
        }

    private fun freshnessSeconds(tileConfig: TodoListTile?): Long {
        val interval = tileConfig?.refreshInterval ?: DEFAULT_REFRESH_INTERVAL
        return if (interval <= 1) 0 else interval
    }

    /** The items of the list, after completing the one tapped on the previous request. */
    private suspend fun listTimeline(
        requestParams: RequestBuilders.TileRequest,
        entityId: String,
        lastId: String,
    ): Timeline = try {
        val webSocket = serverManager.webSocketRepository()
        if (lastId.startsWith(CLICK_COMPLETE_PREFIX)) {
            webSocket.updateTodo(
                entityId,
                lastId.removePrefix(CLICK_COMPLETE_PREFIX),
                null,
                TodoItem.COMPLETED_STATUS,
            )
        }
        val items = checkNotNull(webSocket.getTodos(entityId)).response[entityId]?.items.orEmpty()
            .filter { it.status == TodoItem.NEEDS_ACTION_STATUS }
        val name = entitiesForDisplayManager
            .snapshot(ServerManager.SERVER_ID_ACTIVE, EntityFilter(entityIds = setOf(entityId)))
            .awaitLoadedOrNull()
            ?.entity(entityId)
            ?.name ?: entityId

        timeline(entityId, name, items)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "Unable to fetch to-do list $entityId")
        primaryLayoutTimeline(
            this,
            requestParams,
            null,
            commonR.string.todo_list_load_failed,
            commonR.string.refresh,
            ActionBuilders.LoadAction.Builder().build(),
        )
    }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        serviceScope.future {
            Resources.Builder()
                .setVersion(requestParams.version)
                .addIdToImageMapping(
                    RESOURCE_REFRESH,
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_refresh)
                                .build(),
                        ).build(),
                )
                .build()
        }

    override fun onTileAddEvent(requestParams: EventBuilders.TileAddEvent) = runBlocking {
        if (todoListTileDao.get(requestParams.tileId) == null) {
            todoListTileDao.add(TodoListTile(id = requestParams.tileId))
        }
    }

    override fun onTileRemoveEvent(requestParams: EventBuilders.TileRemoveEvent) = runBlocking {
        todoListTileDao.delete(requestParams.tileId)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun timeline(entityId: String, listName: String, items: List<TodoItem>): Timeline {
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(listName)
                    .setMaxLines(1)
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(DimensionBuilders.sp(16f))
                            .setColor(ColorBuilders.argb(getColor(commonR.color.colorPrimary)))
                            .build(),
                    )
                    .setModifiers(clickModifiers(CLICK_OPEN_LIST, openListAction(entityId)))
                    .build(),
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(6f)).build())

        if (items.isEmpty()) {
            column.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(getString(commonR.string.todo_list_tile_empty))
                    .build(),
            )
        }
        items.take(MAX_VISIBLE_ITEMS).forEach { column.addContent(itemRow(it)) }
        if (items.size > MAX_VISIBLE_ITEMS) {
            column.addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(getString(commonR.string.todo_list_tile_more, items.size - MAX_VISIBLE_ITEMS))
                    .setModifiers(clickModifiers(CLICK_OPEN_LIST, openListAction(entityId)))
                    .build(),
            )
        }

        return Timeline.fromLayoutElement(
            LayoutElementBuilders.Box.Builder()
                .addContent(column.build())
                .addContent(getRefreshButton())
                .setModifiers(getRefreshModifiers())
                .build(),
        )
    }

    private fun itemRow(item: TodoItem): LayoutElementBuilders.Text {
        val key = item.uid ?: item.summary
        val modifiers = if (key != null) {
            clickModifiers(
                "$CLICK_COMPLETE_PREFIX$key",
                ActionBuilders.LoadAction.Builder().build(),
                paddingDp = ITEM_PADDING_DP,
            )
        } else {
            ModifiersBuilders.Modifiers.Builder().build()
        }
        return LayoutElementBuilders.Text.Builder()
            .setText("○ ${item.summary.orEmpty()}")
            .setMaxLines(1)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
            .setModifiers(modifiers)
            .build()
    }

    private fun openListAction(entityId: String) = ActionBuilders.LaunchAction.Builder()
        .setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder()
                .setPackageName(applicationContext.packageName)
                .setClassName(TodoListActivity::class.java.name)
                .addKeyToExtraMapping(
                    TodoListActivity.EXTRA_ENTITY_ID,
                    ActionBuilders.AndroidStringExtra.Builder().setValue(entityId).build(),
                )
                .build(),
        )
        .build()

    private fun clickModifiers(id: String, action: ActionBuilders.Action, paddingDp: Float = 0f) =
        ModifiersBuilders.Modifiers.Builder()
            .setClickable(Clickable.Builder().setId(id).setOnClick(action).build())
            .setPadding(
                ModifiersBuilders.Padding.Builder()
                    .setTop(DimensionBuilders.dp(paddingDp))
                    .setBottom(DimensionBuilders.dp(paddingDp))
                    .build(),
            )
            .build()
}
