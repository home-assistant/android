package io.homeassistant.companion.android.widgets.grid

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.BundleCompat
import androidx.core.widget.RemoteViewsCompat
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.padding
import com.mikepenz.iconics.utils.size
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.onEntityPressedWithoutState
import io.homeassistant.companion.android.database.widget.GridWidgetDao
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.common.WidgetAuthenticationActivity
import io.homeassistant.companion.android.widgets.grid.config.GridConfiguration
import io.homeassistant.companion.android.widgets.grid.config.GridItem
import javax.inject.Inject
import kotlin.String
import kotlin.collections.Map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GridWidget : BaseWidgetProvider() {
    companion object {
        private const val TAG = "GridWidget"
        const val CALL_SERVICE =
            "io.homeassistant.companion.android.widgets.grid.GridWidget.CALL_SERVICE"
        const val CALL_SERVICE_AUTH =
            "io.homeassistant.companion.android.widgets.grid.GridWidget.CALL_SERVICE_AUTH"
        const val EXTRA_ACTION_ID =
            "io.homeassistant.companion.android.widgets.grid.GridWidget.EXTRA_ACTION_ID"
        const val EXTRA_CONFIG =
            "io.homeassistant.companion.android.widgets.grid.GridWidget.EXTRA_CONFIG"
    }

    @Inject
    lateinit var gridWidgetDao: GridWidgetDao

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val actionId = intent.getIntExtra(EXTRA_ACTION_ID, -1)

        super.onReceive(context, intent)
        when (action) {
            CALL_SERVICE_AUTH -> authThenCallConfiguredAction(context, appWidgetId, actionId)
            CALL_SERVICE -> callConfiguredAction(appWidgetId, actionId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        widgetScope?.launch {
            gridWidgetDao.deleteAll(appWidgetIds)
            appWidgetIds.forEach { removeSubscription(it) }
        }
    }

    private fun authThenCallConfiguredAction(context: Context, appWidgetId: Int, actionId: Int) {
        Log.d(TAG, "Calling authentication, then configured action")

        val extras = Bundle().apply {
            putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putInt(EXTRA_ACTION_ID, actionId)
        }
        val intent = Intent(context, WidgetAuthenticationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            putExtra(WidgetAuthenticationActivity.EXTRA_TARGET, GridWidget::class.java)
            putExtra(WidgetAuthenticationActivity.EXTRA_ACTION, CALL_SERVICE)
            putExtra(WidgetAuthenticationActivity.EXTRA_EXTRAS, extras)
        }
        context.startActivity(intent)
    }

    private fun callConfiguredAction(appWidgetId: Int, actionId: Int) {
        Log.d(TAG, "Calling widget action")

        val widget = gridWidgetDao.get(appWidgetId)
        val item = widget?.items?.find { it.id == actionId }

        mainScope.launch {
            val entityId = item?.entityId

            Log.d(TAG, "Action Call Data loaded: entity_id: $entityId")
            if (entityId == null) {
                Log.w(TAG, "Action Call Data incomplete. Aborting action call")
            } else {
                // If everything loaded correctly, attempt the call
                try {
                    Log.d(TAG, "Sending action call to Home Assistant")
                    onEntityPressedWithoutState(
                        entityId,
                        serverManager.integrationRepository(widget.gridWidget.serverId)
                    )
                    Log.d(TAG, "Action call sent successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to call action", e)
                }
            }
        }
    }

    override fun getWidgetProvider(context: Context): ComponentName =
        ComponentName(context, GridWidget::class.java)

    override suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, suggestedEntity: Entity<Map<String, Any>>?): RemoteViews {
        val gridConfig = gridWidgetDao.get(appWidgetId)?.asGridConfiguration()
        val entityStates = gridConfig?.let { getEntityStates(gridConfig.serverId ?: 0, gridConfig.items.map { it.entityId }, suggestedEntity) }
        return gridConfig.asRemoteViews(context, appWidgetId, entityStates)
    }

    override suspend fun getAllWidgetIdsWithEntities(context: Context): Map<Int, Pair<Int, List<String>>> =
        gridWidgetDao.getAll().associate {
            val entityIds = it.items
                .map { it.entityId }
                .filterNot { it.isEmpty() }

            it.gridWidget.id to (it.gridWidget.serverId to entityIds)
        }

    override fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        val extras = extras ?: return
        val config = BundleCompat.getParcelable(extras, EXTRA_CONFIG, GridConfiguration::class.java) ?: return

        widgetScope?.launch {
            gridWidgetDao.add(config.asDbEntity(appWidgetId))
        }

        onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
    }

    override suspend fun onEntityStateChanged(context: Context, appWidgetId: Int, entity: Entity<*>) {
        widgetScope?.launch {
            val views = getWidgetRemoteViews(context, appWidgetId, entity as Entity<Map<String, Any>>)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }
    }

    private fun GridConfiguration?.asRemoteViews(context: Context, widgetId: Int, entityStates: Map<String, String>? = null): RemoteViews {
        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            R.layout.widget_grid_wrapper_dynamiccolor
        } else {
            R.layout.widget_grid_wrapper_default
        }
        val remoteViews = RemoteViews(context.packageName, layout)

        if (this != null) {
            remoteViews.apply {
                if (label.isNullOrEmpty()) {
                    setViewVisibility(R.id.widgetLabel, View.GONE)
                } else {
                    setViewVisibility(R.id.widgetLabel, View.VISIBLE)
                    setTextViewText(R.id.widgetLabel, label)
                }

                val intent = Intent(context, GridWidget::class.java).apply {
                    action = if (requireAuthentication) CALL_SERVICE_AUTH else CALL_SERVICE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                setPendingIntentTemplate(
                    R.id.widgetGrid,
                    PendingIntent.getBroadcast(
                        context,
                        widgetId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                )

                RemoteViewsCompat.setRemoteAdapter(
                    context = context,
                    remoteViews = this,
                    appWidgetId = widgetId,
                    viewId = R.id.widgetGrid,
                    items = items.asRemoteCollection(context, entityStates)
                )
            }
        }
        return remoteViews
    }

    private fun List<GridItem>.asRemoteCollection(context: Context, entityStates: Map<String, String>? = null) =
        RemoteViewsCompat.RemoteCollectionItems.Builder().apply {
            setHasStableIds(true)
            forEach { action ->
                addItem(
                    context = context,
                    item = action,
                    state = entityStates?.get(action.entityId)
                )
            }
        }.build()

    private suspend fun getEntityStates(serverId: Int, entities: List<String>, suggestedEntity: Entity<Map<String, Any>>? = null): Map<String, String> =
        entities.associateWith {
            if (suggestedEntity?.entityId != it) {
                serverManager.integrationRepository(serverId).getEntity(it)?.state ?: "Unknown"
            } else {
                suggestedEntity.state
            }
        }

    private fun RemoteViewsCompat.RemoteCollectionItems.Builder.addItem(context: Context, item: GridItem, state: String? = null) {
        addItem(item.id.toLong(), item.asRemoteViews(context, state))
    }

    private fun GridItem.asRemoteViews(context: Context, state: String? = null) =
        RemoteViews(context.packageName, R.layout.widget_grid_button).apply {
            val icon = CommunityMaterial.getIconByMdiName(icon)
            icon?.let {
                val iconDrawable = DrawableCompat.wrap(
                    IconicsDrawable(context, icon).apply {
                        padding = IconicsSize.dp(2)
                        size = IconicsSize.dp(24)
                    }
                )

                setImageViewBitmap(R.id.widgetImageButton, iconDrawable.toBitmap())
            }
            setTextViewText(
                R.id.widgetLabel,
                label
            )
            setTextViewText(
                R.id.widgetState,
                state ?: context.getString(commonR.string.state_unknown)
            )

            val fillInIntent = Intent().apply {
                Bundle().also { extras ->
                    extras.putInt(EXTRA_ACTION_ID, id)
                    putExtras(extras)
                }
            }
            setOnClickFillInIntent(R.id.gridButtonLayout, fillInIntent)
        }
}
