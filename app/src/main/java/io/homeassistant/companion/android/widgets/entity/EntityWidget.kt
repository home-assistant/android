package io.homeassistant.companion.android.widgets.entity

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
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.canSupportPrecision
import io.homeassistant.companion.android.common.data.integration.friendlyState
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.getAttribute
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class EntityWidget : BaseWidgetProvider() {

    companion object {
        private const val TAG = "StaticWidget"

        internal const val EXTRA_SERVER_ID = "EXTRA_SERVER_ID"
        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        internal const val EXTRA_ATTRIBUTE_IDS = "EXTRA_ATTRIBUTE_IDS"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val EXTRA_TEXT_SIZE = "EXTRA_TEXT_SIZE"
        internal const val EXTRA_STATE_SEPARATOR = "EXTRA_STATE_SEPARATOR"
        internal const val EXTRA_ATTRIBUTE_SEPARATOR = "EXTRA_ATTRIBUTE_SEPARATOR"
        internal const val EXTRA_BACKGROUND_TYPE = "EXTRA_BACKGROUND_TYPE"
        internal const val EXTRA_TEXT_COLOR = "EXTRA_TEXT_COLOR"

        private data class ResolvedText(val text: CharSequence?, val exception: Boolean = false)
    }

    @Inject
    lateinit var staticWidgetDao: StaticWidgetDao

    override fun getWidgetProvider(context: Context): ComponentName =
        ComponentName(context, EntityWidget::class.java)

    override suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, suggestedEntity: Entity<Map<String, Any>>?): RemoteViews {
        val intent = Intent(context, EntityWidget::class.java).apply {
            action = UPDATE_VIEW
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val widget = staticWidgetDao.get(appWidgetId)
        val useDynamicColors = widget?.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable()
        val views = RemoteViews(context.packageName, if (useDynamicColors) R.layout.widget_static_wrapper_dynamiccolor else R.layout.widget_static_wrapper_default).apply {
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
        staticWidgetDao.getAll().associate { it.id to (it.serverId to listOf(it.entityId)) }

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
        var entity: Entity<Map<String, Any>>? = null
        var entityCaughtException = false
        try {
            entity = if (suggestedEntity != null && suggestedEntity.entityId == entityId) {
                suggestedEntity
            } else {
                entityId?.let { serverManager.integrationRepository(serverId).getEntity(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to fetch entity", e)
            entityCaughtException = true
        }
        val entityOptions = if (
            entity?.canSupportPrecision() == true &&
            serverManager.getServer(serverId)?.version?.isAtLeast(2023, 3) == true
        ) {
            serverManager.webSocketRepository(serverId).getEntityRegistryFor(entity.entityId)?.options
        } else {
            null
        }
        if (attributeIds == null) {
            staticWidgetDao.updateWidgetLastUpdate(
                appWidgetId,
                entity?.friendlyState(context, entityOptions) ?: staticWidgetDao.get(appWidgetId)?.lastUpdate ?: ""
            )
            return ResolvedText(staticWidgetDao.get(appWidgetId)?.lastUpdate, entityCaughtException)
        }

        var fetchedAttributes: Map<*, *>
        var attributeValues: List<String?>
        try {
            fetchedAttributes = entity?.attributes as? Map<*, *> ?: mapOf<String, String>()
            attributeValues =
                attributeIds.split(",").map { id -> fetchedAttributes.get(id)?.toString() }
            val lastUpdate =
                entity?.friendlyState(context, entityOptions).plus(if (attributeValues.isNotEmpty()) stateSeparator else "")
                    .plus(attributeValues.joinToString(attributeSeparator))
            staticWidgetDao.updateWidgetLastUpdate(appWidgetId, lastUpdate)
            return ResolvedText(lastUpdate)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to fetch entity state and attributes", e)
        }
        return ResolvedText(staticWidgetDao.get(appWidgetId)?.lastUpdate, true)
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
        val backgroundTypeSelection: WidgetBackgroundType = extras.getSerializable(EXTRA_BACKGROUND_TYPE) as WidgetBackgroundType
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
            staticWidgetDao.add(
                StaticWidgetEntity(
                    appWidgetId,
                    serverId,
                    entitySelection,
                    attributeSelection?.joinToString(","),
                    labelSelection,
                    textSizeSelection?.toFloatOrNull() ?: 30F,
                    stateSeparatorSelection ?: "",
                    attributeSeparatorSelection ?: "",
                    staticWidgetDao.get(appWidgetId)?.lastUpdate ?: "",
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
            staticWidgetDao.deleteAll(appWidgetIds)
            appWidgetIds.forEach { removeSubscription(it) }
        }
    }
}
