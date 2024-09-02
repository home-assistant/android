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
import io.homeassistant.companion.android.common.data.integration.friendlyState
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryOptions
import io.homeassistant.companion.android.database.widget.HistoryWidgetDao
import io.homeassistant.companion.android.database.widget.HistoryWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
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
        internal const val EXTRA_ATTRIBUTE_IDS = "EXTRA_ATTRIBUTE_IDS"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val EXTRA_TEXT_SIZE = "EXTRA_TEXT_SIZE"
        internal const val EXTRA_STATE_SEPARATOR = "EXTRA_STATE_SEPARATOR"
        internal const val EXTRA_ATTRIBUTE_SEPARATOR = "EXTRA_ATTRIBUTE_SEPARATOR"
        internal const val EXTRA_TAP_ACTION = "EXTRA_TAP_ACTION"
        internal const val EXTRA_BACKGROUND_TYPE = "EXTRA_BACKGROUND_TYPE"
        internal const val EXTRA_TEXT_COLOR = "EXTRA_TEXT_COLOR"
        internal const val DEFAULT_TEXT_SIZE = 30F

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
                val entityId: String = widget.entityId
                val attributeIds: String? = widget.attributeIds
                val label: String? = widget.label
                val textSize: Float = widget.textSize
                val stateSeparator: String = widget.stateSeparator
                val attributeSeparator: String = widget.attributeSeparator

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
                    entityId,
                    suggestedEntity,
                    attributeIds,
                    stateSeparator,
                    attributeSeparator,
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
                    label ?: entityId
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
        entityId: String?,
        suggestedEntity: Entity<Map<String, Any>>?,
        attributeIds: String?,
        stateSeparator: String,
        attributeSeparator: String,
        appWidgetId: Int
    ): ResolvedText {
        var entityStatesList: List<Entity<Map<String, Any>>>? = null
        var entityCaughtException = false
        try {
            // NOTE: History allows to pass a list of entities, but we currently only support a single entity, hence the usage of firstOrNull
            entityStatesList = entityId?.let { serverManager.integrationRepository(serverId).getHistory(listOf(it))?.firstOrNull() }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to fetch entity", e)
            entityCaughtException = true
        }
        val entityOptions = if (
            entityStatesList?.any { it.entityId == entityId } == true &&
            serverManager.getServer(serverId)?.version?.isAtLeast(2023, 3) == true
        ) {
            serverManager.webSocketRepository(serverId).getEntityRegistryFor(entityStatesList.first().entityId)?.options
        } else {
            null
        }

        try {
            historyWidgetDao.updateWidgetLastUpdate(
                appWidgetId,
                getLastUpdateFromEntityStatesList(entityStatesList, context, entityOptions, attributeIds, attributeSeparator, stateSeparator)
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
        val entitySelection: String? = extras.getString(EXTRA_ENTITY_ID)
        val attributeSelection: ArrayList<String>? = extras.getStringArrayList(EXTRA_ATTRIBUTE_IDS)
        val labelSelection: String? = extras.getString(EXTRA_LABEL)
        val textSizeSelection: String? = extras.getString(EXTRA_TEXT_SIZE)
        val stateSeparatorSelection: String? = extras.getString(EXTRA_STATE_SEPARATOR)
        val attributeSeparatorSelection: String? = extras.getString(EXTRA_ATTRIBUTE_SEPARATOR)
        val tapActionSelection = BundleCompat.getSerializable(extras, EXTRA_TAP_ACTION, WidgetTapAction::class.java)
            ?: WidgetTapAction.REFRESH
        val backgroundTypeSelection = BundleCompat.getSerializable(extras, EXTRA_BACKGROUND_TYPE, WidgetBackgroundType::class.java)
            ?: WidgetBackgroundType.DAYNIGHT
        val textColorSelection: String? = extras.getString(EXTRA_TEXT_COLOR)

        if (serverId == null || entitySelection == null) {
            Log.e(TAG, "Did not receive complete service call data")
            return
        }

        widgetScope?.launch {
            Log.d(
                TAG,
                "Saving entity state config data:" + System.lineSeparator() +
                    "entity id: " + entitySelection + System.lineSeparator() +
                    "attribute: " + (attributeSelection ?: "N/A")
            )
            historyWidgetDao.add(
                HistoryWidgetEntity(
                    appWidgetId,
                    serverId,
                    entitySelection,
                    attributeSelection?.joinToString(","),
                    labelSelection,
                    textSizeSelection?.toFloatOrNull() ?: DEFAULT_TEXT_SIZE,
                    stateSeparatorSelection ?: "",
                    attributeSeparatorSelection ?: "",
                    tapActionSelection,
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
        entityOptions: EntityRegistryOptions?,
        attributeIds: String?,
        attributeSeparator: String,
        stateSeparator: String
    ): String = entityList?.fold("") { acc, entity ->

        val localDate = with(entity.lastUpdated) {
            LocalDateTime.ofInstant(toInstant(), timeZone.toZoneId())
        }
        val entityTextToVisualize = StringBuilder(localDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")))
        entityTextToVisualize.append(": ")
        if (attributeIds == null) {
            entityTextToVisualize.append(entity.friendlyState(context, entityOptions))
        } else {
            try {
                val fetchedAttributes = entity.attributes as? Map<*, *> ?: mapOf<String, String>()
                val attributeValues =
                    attributeIds.split(",").map { id -> fetchedAttributes[id]?.toString() }
                val lastUpdate =
                    entity.friendlyState(context, entityOptions).plus(if (attributeValues.isNotEmpty()) stateSeparator else "")
                        .plus(attributeValues.joinToString(attributeSeparator))
                entityTextToVisualize.append(lastUpdate)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to fetch entity state and attributes", e)
            }
        }
        if (acc.isEmpty()) {
            acc.plus(entityTextToVisualize)
        } else {
            acc.plus(",\n").plus(entityTextToVisualize)
        }
    } ?: ""
}
