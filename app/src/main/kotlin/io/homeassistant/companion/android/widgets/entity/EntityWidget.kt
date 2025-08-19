package io.homeassistant.companion.android.widgets.entity

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.canSupportPrecision
import io.homeassistant.companion.android.common.data.integration.friendlyState
import io.homeassistant.companion.android.common.data.integration.onEntityPressedWithoutState
import io.homeassistant.companion.android.database.widget.StaticWidgetDao
import io.homeassistant.companion.android.database.widget.StaticWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.database.widget.WidgetTapAction
import io.homeassistant.companion.android.util.getAttribute
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class EntityWidget : BaseWidgetProvider<StaticWidgetEntity, StaticWidgetDao>() {

    companion object {
        internal const val TOGGLE_ENTITY =
            "io.homeassistant.companion.android.widgets.entity.EntityWidget.TOGGLE_ENTITY"

        private data class ResolvedText(val text: CharSequence?, val exception: Boolean = false)
    }

    override fun getWidgetProvider(context: Context): ComponentName = ComponentName(context, EntityWidget::class.java)

    override suspend fun getWidgetRemoteViews(
        context: Context,
        appWidgetId: Int,
        suggestedEntity: Entity?,
    ): RemoteViews {
        val widget = dao.get(appWidgetId)

        val intent = Intent(context, EntityWidget::class.java).apply {
            action = if (widget?.tapAction == WidgetTapAction.TOGGLE) TOGGLE_ENTITY else UPDATE_VIEW
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val useDynamicColors =
            widget?.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable()
        val views = RemoteViews(
            context.packageName,
            if (useDynamicColors) {
                R.layout.widget_static_wrapper_dynamiccolor
            } else {
                R.layout.widget_static_wrapper_default
            },
        ).apply {
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
                    var textColor = context.getAttribute(
                        R.attr.colorWidgetOnBackground,
                        ContextCompat.getColor(context, commonR.color.colorWidgetButtonLabel),
                    )
                    widget.textColor?.let { textColor = it.toColorInt() }

                    setInt(R.id.widgetLayout, "setBackgroundColor", Color.TRANSPARENT)
                    setTextColor(R.id.widgetText, textColor)
                    setTextColor(R.id.widgetLabel, textColor)
                }

                // Content
                setViewVisibility(
                    R.id.widgetTextLayout,
                    View.VISIBLE,
                )
                setViewVisibility(
                    R.id.widgetProgressBar,
                    View.INVISIBLE,
                )
                val resolvedText = resolveTextToShow(
                    context,
                    serverId,
                    entityId,
                    suggestedEntity,
                    attributeIds,
                    stateSeparator,
                    attributeSeparator,
                    appWidgetId,
                )
                setTextViewTextSize(
                    R.id.widgetText,
                    TypedValue.COMPLEX_UNIT_SP,
                    textSize,
                )
                setTextViewText(
                    R.id.widgetText,
                    resolvedText.text,
                )
                setTextViewText(
                    R.id.widgetLabel,
                    label ?: entityId,
                )
                setViewVisibility(
                    R.id.widgetStaticError,
                    if (resolvedText.exception) View.VISIBLE else View.GONE,
                )
                setOnClickPendingIntent(
                    R.id.widgetTextLayout,
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            } else {
                setTextViewText(R.id.widgetText, "")
                setTextViewText(R.id.widgetLabel, "")
            }
        }

        return views
    }

    override suspend fun getAllWidgetIdsWithEntities(context: Context): Map<Int, Pair<Int, List<String>>> =
        dao.getAll().associate { it.id to (it.serverId to listOf(it.entityId)) }

    private suspend fun resolveTextToShow(
        context: Context,
        serverId: Int,
        entityId: String?,
        suggestedEntity: Entity?,
        attributeIds: String?,
        stateSeparator: String,
        attributeSeparator: String,
        appWidgetId: Int,
    ): ResolvedText {
        var entity: Entity? = null
        var entityCaughtException = false
        try {
            entity = if (suggestedEntity != null && suggestedEntity.entityId == entityId) {
                suggestedEntity
            } else {
                entityId?.let { serverManager.integrationRepository(serverId).getEntity(it) }
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to fetch entity")
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
            dao.updateWidgetLastUpdate(
                appWidgetId,
                entity?.friendlyState(context, entityOptions) ?: dao.get(appWidgetId)?.lastUpdate ?: "",
            )
            return ResolvedText(dao.get(appWidgetId)?.lastUpdate, entityCaughtException)
        }

        try {
            val fetchedAttributes = entity?.attributes as? Map<*, *> ?: mapOf<String, String>()
            val attributeValues =
                attributeIds.split(",").map { id -> fetchedAttributes[id]?.toString() }
            val lastUpdate =
                entity?.friendlyState(
                    context,
                    entityOptions,
                ).plus(if (attributeValues.isNotEmpty()) stateSeparator else "")
                    .plus(attributeValues.joinToString(attributeSeparator))
            dao.updateWidgetLastUpdate(appWidgetId, lastUpdate)
            return ResolvedText(lastUpdate)
        } catch (e: Exception) {
            Timber.e(e, "Unable to fetch entity state and attributes")
        }
        return ResolvedText(dao.get(appWidgetId)?.lastUpdate, true)
    }

    override suspend fun onEntityStateChanged(context: Context, appWidgetId: Int, entity: Entity) {
        widgetScope?.launch {
            val views = getWidgetRemoteViews(context, appWidgetId, entity as Entity)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
        }
    }

    private fun toggleEntity(context: Context, appWidgetId: Int) {
        widgetScope?.launch {
            // Show progress bar as feedback
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val loadingViews = RemoteViews(context.packageName, R.layout.widget_static)
            loadingViews.setViewVisibility(R.id.widgetProgressBar, View.VISIBLE)
            loadingViews.setViewVisibility(R.id.widgetTextLayout, View.GONE)
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, loadingViews)

            var success = false
            dao.get(appWidgetId)?.let {
                try {
                    onEntityPressedWithoutState(
                        it.entityId,
                        serverManager.integrationRepository(it.serverId),
                    )
                    success = true
                } catch (e: Exception) {
                    Timber.e(e, "Unable to send toggle service call")
                }
            }

            if (!success) {
                Toast.makeText(context, commonR.string.action_failure, Toast.LENGTH_LONG).show()

                val views = getWidgetRemoteViews(context, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } // else update will be triggered by websocket subscription
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        super.onReceive(context, intent)
        when (lastIntent) {
            TOGGLE_ENTITY -> toggleEntity(context, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        widgetScope?.launch {
            dao.deleteAll(appWidgetIds)
            appWidgetIds.forEach { removeSubscription(it) }
        }
    }
}
