package io.homeassistant.companion.android.widgets.history

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.os.BundleCompat
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.canSupportPrecision
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.friendlyState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryOptions
import io.homeassistant.companion.android.database.widget.HistoryWidgetDao
import io.homeassistant.companion.android.database.widget.HistoryWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.getAttribute
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistoryWidget : BaseWidgetProvider() {

    companion object {
        private const val TAG = "HistoryWidget"

        internal const val EXTRA_SERVER_ID = "EXTRA_SERVER_ID"
        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val EXTRA_TEXT_SIZE = "EXTRA_TEXT_SIZE"
        internal const val EXTRA_BACKGROUND_TYPE = "EXTRA_BACKGROUND_TYPE"
        internal const val EXTRA_TEXT_COLOR = "EXTRA_TEXT_COLOR"
        internal const val DEFAULT_TEXT_SIZE = 30F
        internal const val DEFAULT_ENTITY_ID_SEPARATOR = ","

        private data class ResolvedText(val text: CharSequence?, val exception: Boolean = false)
    }

    @Inject
    lateinit var historyWidgetDao: HistoryWidgetDao

    override fun getWidgetProvider(context: Context): ComponentName =
        ComponentName(context, HistoryWidget::class.java)

    override suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, suggestedEntity: Entity<Map<String, Any>>?): RemoteViews {
        val widget = historyWidgetDao.get(appWidgetId)

        val intent = Intent(context, HistoryWidget::class.java).apply {
            action = UPDATE_VIEW
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val useDynamicColors = widget?.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable()
        val views = RemoteViews(context.packageName, if (useDynamicColors) R.layout.widget_history_wrapper_dynamiccolor else R.layout.widget_history_wrapper_default).apply {
            if (widget != null) {
                val serverId = widget.serverId
                val entityIds: String = widget.entityId
                val label: String? = widget.label
                val textSize: Float = widget.textSize

                // Theming
                if (widget.backgroundType == WidgetBackgroundType.TRANSPARENT) {
                    var textColor = context.getAttribute(R.attr.colorWidgetOnBackground, ContextCompat.getColor(context, commonR.color.colorWidgetButtonLabel))
                    widget.textColor?.let { textColor = it.toColorInt() }

                    setInt(R.id.widgetLayout, "setBackgroundColor", Color.TRANSPARENT)
                    setTextColor(R.id.widgetText, textColor)
                    setTextColor(R.id.widgetLabel, textColor)
                }

                // Content
                setViewVisibility(
                    R.id.widgetTextLayout,
                    View.VISIBLE
                )
                setViewVisibility(
                    R.id.widgetProgressBar,
                    View.INVISIBLE
                )
                val resolvedText = resolveTextToShow(
                    context,
                    serverId,
                    entityIds,
                    suggestedEntity,
                    appWidgetId
                )
                setTextViewTextSize(
                    R.id.widgetText,
                    TypedValue.COMPLEX_UNIT_SP,
                    textSize
                )
                setTextViewText(
                    R.id.widgetText,
                    resolvedText.text
                )
                setTextViewText(
                    R.id.widgetLabel,
                    label ?: entityIds
                )
                setViewVisibility(
                    R.id.widgetStaticError,
                    if (resolvedText.exception) View.VISIBLE else View.GONE
                )
                setOnClickPendingIntent(
                    R.id.widgetTextLayout,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                setTextViewText(R.id.widgetText, "")
                setTextViewText(R.id.widgetLabel, "")
            }
        }

        return views
    }

    override suspend fun getAllWidgetIdsWithEntities(context: Context): Map<Int, Pair<Int, List<String>>> =
        historyWidgetDao.getAll().associate { it.id to (it.serverId to listOf(it.entityId)) }

    private suspend fun resolveTextToShow(
        context: Context,
        serverId: Int,
        entityIds: String?,
        suggestedEntity: Entity<Map<String, Any>>?,
        appWidgetId: Int
    ): ResolvedText {
        var entitiesStatesList: List<List<Entity<Map<String, Any>>>>? = null
        var entityCaughtException = false
        try {
            if (suggestedEntity != null) {
                entitiesStatesList = serverManager.integrationRepository(serverId).getHistory(listOf(suggestedEntity.entityId))
            } else {
                entityIds?.let { ids -> entitiesStatesList = serverManager.integrationRepository(serverId).getHistory(ids.split(DEFAULT_ENTITY_ID_SEPARATOR)) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to fetch entity", e)
            entityCaughtException = true
        }

        val entityOptionsList = mutableMapOf<String, EntityRegistryOptions?>()

        entitiesStatesList?.forEach { entityStateList ->
            if (entityStateList.all { it.canSupportPrecision() && serverManager.getServer(serverId)?.version?.isAtLeast(2023, 3) == true }) {
                entityOptionsList[entityStateList.first().entityId] = serverManager.webSocketRepository(serverId).getEntityRegistryFor(entityStateList.first().entityId)?.options
            }
        }

        try {
            val textBuilder = StringBuilder()
            entitiesStatesList?.forEachIndexed { index, entityStatesList ->
                if (index > 0) textBuilder.append("\n---\n")
                textBuilder.append(getLastUpdateFromEntityStatesList(entityStatesList, context, entityOptionsList[entityStatesList.first().entityId]))
            }
            historyWidgetDao.updateWidgetLastUpdate(
                appWidgetId,
                textBuilder.toString()
            )
            return ResolvedText(historyWidgetDao.get(appWidgetId)?.lastUpdate, entityCaughtException)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to fetch entity state and attributes", e)
            return ResolvedText(historyWidgetDao.get(appWidgetId)?.lastUpdate, true)
        }
    }

    override fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val serverId = if (extras.containsKey(EXTRA_SERVER_ID)) extras.getInt(EXTRA_SERVER_ID) else null
        val entityIds: String? = extras.getString(EXTRA_ENTITY_ID)
        val labelSelection: String? = extras.getString(EXTRA_LABEL)
        val textSizeSelection: String? = extras.getString(EXTRA_TEXT_SIZE)
        val backgroundTypeSelection = BundleCompat.getSerializable(extras, EXTRA_BACKGROUND_TYPE, WidgetBackgroundType::class.java)
            ?: WidgetBackgroundType.DAYNIGHT
        val textColorSelection: String? = extras.getString(EXTRA_TEXT_COLOR)

        if (serverId == null || entityIds == null) {
            Log.e(TAG, "Did not receive complete service call data")
            return
        }

        widgetScope?.launch {
            Log.d(
                TAG,
                "Saving entity state config data:" + System.lineSeparator() +
                    "entity id: " + entityIds + System.lineSeparator()
            )
            historyWidgetDao.add(
                HistoryWidgetEntity(
                    appWidgetId,
                    serverId,
                    entityIds,
                    labelSelection,
                    textSizeSelection?.toFloatOrNull() ?: DEFAULT_TEXT_SIZE,
                    historyWidgetDao.get(appWidgetId)?.lastUpdate ?: "",
                    backgroundTypeSelection,
                    textColorSelection
                )
            )

            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    override suspend fun onEntityStateChanged(context: Context, appWidgetId: Int, entity: Entity<*>) {
        widgetScope?.launch {
            val views = getWidgetRemoteViews(context, appWidgetId, entity as Entity<Map<String, Any>>)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        widgetScope?.launch {
            historyWidgetDao.deleteAll(appWidgetIds)
            appWidgetIds.forEach { removeSubscription(it) }
        }
    }

    private fun getLastUpdateFromEntityStatesList(
        entityList: List<Entity<Map<String, Any>>>?,
        context: Context,
        entityOptions: EntityRegistryOptions?
    ): String = entityList?.fold("") { acc, entity ->

        val localDate = with(entity.lastUpdated) {
            LocalDateTime.ofInstant(toInstant(), timeZone.toZoneId())
        }
        val entityTextToVisualize = StringBuilder("(")
        entityTextToVisualize.append(localDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append(") ")
        entityTextToVisualize.append(entity.friendlyState(context, entityOptions))
        if (acc.isEmpty()) {
            acc.plus(entity.friendlyName).plus(": ").plus(entityTextToVisualize)
        } else {
            acc.plus("\n").plus(entity.friendlyName).plus(": ").plus(entityTextToVisualize)
        }
    } ?: ""
}
