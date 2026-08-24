package io.homeassistant.companion.android.widgets.button

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import androidx.core.os.BundleCompat
import com.google.android.material.color.DynamicColors
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.IconicsSize
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.padding
import com.mikepenz.iconics.utils.size
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.getIconByMdiName
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.getAttribute
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import io.homeassistant.companion.android.widgets.common.WidgetAuthenticationActivity
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val TOGGLE_SERVICE_SUFFIX = ".toggle"
private val TOGGLE_STATE_RECONNECTION_DELAY = 2.seconds

@AndroidEntryPoint
class ButtonWidget : BaseWidgetProvider<ButtonWidgetEntity, ButtonWidgetDao>() {
    companion object {
        const val CALL_SERVICE =
            "io.homeassistant.companion.android.widgets.button.ButtonWidget.CALL_SERVICE"
        private const val CALL_SERVICE_AUTH =
            "io.homeassistant.companion.android.widgets.button.ButtonWidget.CALL_SERVICE_AUTH"

        // Vector icon rendering resolution fallback (if we can't infer via AppWidgetManager for some reason)
        private const val DEFAULT_MAX_ICON_SIZE = 512

        // Last known entity state per widget so renders don't block on a REST call
        private val toggleStates = ConcurrentHashMap<Int, Entity>()
    }

    private fun authThenCallConfiguredAction(context: Context, appWidgetId: Int) {
        Timber.d("Calling authentication, then configured action")

        val intent = Intent(context, WidgetAuthenticationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        context.startActivity(intent)
    }

    override fun getWidgetProvider(context: Context): ComponentName = ComponentName(context, ButtonWidget::class.java)

    override suspend fun getWidgetRemoteViews(
        context: Context,
        appWidgetId: Int,
        suggestedEntity: Entity?,
    ): RemoteViews {
        // Every time AppWidgetManager.updateAppWidget(...) is called, the button listener
        // and label need to be re-assigned, or the next time the layout updates
        // (e.g home screen rotation) the widget will fall back on its default layout
        // without any click listener being applied

        val widget = dao.get(appWidgetId)
        val toggleEntity = widget?.takeIf { it.isToggleWidget() }?.let { toggleWidget ->
            val entity = suggestedEntity?.takeIf { it.entityId == getToggleEntityId(toggleWidget) }
                ?: toggleStates[appWidgetId]
                ?: getToggleEntity(toggleWidget)
            entity?.also { toggleStates[appWidgetId] = it }
        }
        val auth = widget?.requireAuthentication == true

        val intent = Intent(context, ButtonWidget::class.java).apply {
            action = if (auth) CALL_SERVICE_AUTH else CALL_SERVICE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val useDynamicColors =
            widget?.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable()
        return RemoteViews(
            context.packageName,
            if (useDynamicColors) {
                R.layout.widget_button_wrapper_dynamiccolor
            } else {
                R.layout.widget_button_wrapper_default
            },
        ).apply {
            // Theming
            var textColor = context.getAttribute(
                R.attr.colorWidgetOnBackground,
                ContextCompat.getColor(context, commonR.color.colorWidgetButtonLabel),
            )
            if (widget?.backgroundType == WidgetBackgroundType.TRANSPARENT) {
                widget.textColor?.let { textColor = it.toColorInt() }
                setTextColor(R.id.widgetLabel, textColor)
            }
            setWidgetBackground(context, this, widget, toggleEntity)

            // Label
            setLabelVisibility(this, widget)

            // Content
            val iconData = widget?.iconName?.let { CommunityMaterial.getIconByMdiName(it) }
                ?: CommunityMaterial.Icon2.cmd_flash // Lightning bolt

            val iconDrawable = IconicsDrawable(context, iconData).apply {
                padding = IconicsSize.dp(2)
                size = IconicsSize.dp(24)
            }
            val icon = DrawableCompat.wrap(iconDrawable)
            if (widget?.backgroundType == WidgetBackgroundType.TRANSPARENT) {
                setInt(R.id.widgetImageButton, "setColorFilter", textColor)
            }

            // Determine reasonable dimensions for drawing vector icon as a bitmap
            val aspectRatio = iconDrawable.intrinsicWidth / iconDrawable.intrinsicHeight.toDouble()
            val awo = if (widget != null) AppWidgetManager.getInstance(context).getAppWidgetOptions(widget.id) else null
            val maxWidth = (
                awo?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, DEFAULT_MAX_ICON_SIZE)
                    ?: DEFAULT_MAX_ICON_SIZE
                ).coerceAtLeast(16)
            val maxHeight = (
                awo?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, DEFAULT_MAX_ICON_SIZE)
                    ?: DEFAULT_MAX_ICON_SIZE
                ).coerceAtLeast(16)
            val width: Int
            val height: Int
            if (maxWidth > maxHeight) {
                width = maxWidth
                height = (maxWidth * (1 / aspectRatio)).toInt()
            } else {
                width = (maxHeight * aspectRatio).toInt()
                height = maxHeight
            }

            // Render the icon into the Button's ImageView
            setImageViewBitmap(R.id.widgetImageButton, icon.toBitmap(width, height))

            setOnClickPendingIntent(
                R.id.widgetImageButtonLayout,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            setTextViewText(
                R.id.widgetLabel,
                widget?.label ?: "",
            )
        }
    }

    private fun setWidgetBackground(
        context: Context,
        views: RemoteViews,
        widget: ButtonWidgetEntity?,
        toggleEntity: Entity?,
    ) {
        when {
            widget.isToggleWidget() && toggleEntity?.state == "on" -> {
                views.setInt(
                    R.id.widgetLayout,
                    "setBackgroundResource",
                    R.drawable.widget_button_background_toggle_on,
                )
            }

            widget?.backgroundType == WidgetBackgroundType.TRANSPARENT -> {
                views.setInt(R.id.widgetLayout, "setBackgroundColor", Color.TRANSPARENT)
            }

            else -> {
                views.setInt(R.id.widgetLayout, "setBackgroundResource", R.drawable.widget_button_background)
            }
        }
    }

    private fun ButtonWidgetEntity?.isToggleWidget(): Boolean {
        this ?: return false
        return service == TOGGLE_SERVICE_SUFFIX.removePrefix(".") || service.endsWith(TOGGLE_SERVICE_SUFFIX)
    }

    private suspend fun getToggleEntity(widget: ButtonWidgetEntity): Entity? {
        val entityId = getToggleEntityId(widget) ?: return null
        return try {
            serverManager.integrationRepository(widget.serverId).getEntity(entityId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Unable to read state for $entityId")
            null
        }
    }

    private fun getToggleEntityId(widget: ButtonWidgetEntity): String? {
        if (!widget.isToggleWidget()) return null
        val actionData = kotlinJsonMapper.decodeFromString<Map<String, Any?>>(
            MapAnySerializer,
            widget.serviceData,
        )
        return resolveSingleEntityId(actionData["entity_id"])
    }

    /** Fetches the current state over REST and re-renders only when it differs from the cached one. */
    private suspend fun refreshToggleState(context: Context, appWidgetId: Int) {
        val widget = dao.get(appWidgetId) ?: return
        val entity = getToggleEntity(widget) ?: return
        val previous = toggleStates.put(appWidgetId, entity)
        if (previous?.state != entity.state) {
            AppWidgetManager.getInstance(context)
                .updateAppWidget(appWidgetId, getWidgetRemoteViews(context, appWidgetId, entity))
        }
    }

    private fun resolveSingleEntityId(entityId: Any?): String? {
        val value = when (entityId) {
            is String -> entityId.removePrefix("[").removeSuffix("]")
            is List<*> -> entityId.singleOrNull() as? String
            else -> null
        }?.trim()
        return value?.takeIf { it != "all" && ',' !in it }
    }

    private fun setLabelVisibility(views: RemoteViews, widget: ButtonWidgetEntity?) {
        val labelVisibility = if (widget?.label.isNullOrBlank()) View.GONE else View.VISIBLE
        views.setViewVisibility(R.id.widgetLabelLayout, labelVisibility)
    }

    private suspend fun callConfiguredAction(context: Context, appWidgetId: Int) {
        Timber.d("Calling widget action")

        // Set up progress bar as immediate feedback to show the click has been received
        // Success or failure feedback will come from the mainScope coroutine
        val loadingViews = RemoteViews(context.packageName, R.layout.widget_button)
        val appWidgetManager = AppWidgetManager.getInstance(context)

        loadingViews.setViewVisibility(R.id.widgetProgressBar, View.VISIBLE)
        loadingViews.setViewVisibility(R.id.widgetImageButtonLayout, View.GONE)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, loadingViews)

        val widget = dao.get(appWidgetId)
        // Set default feedback as negative
        var feedbackColor = R.drawable.widget_button_background_red
        var feedbackIcon = R.drawable.ic_clear_black

        // Load the action call data from Shared Preferences
        val domain = widget?.domain
        val action = widget?.service
        val actionDataJson = widget?.serviceData

        Timber.d(
            "Action Call Data loaded:" + System.lineSeparator() +
                "domain: " + domain + System.lineSeparator() +
                "action: " + action + System.lineSeparator() +
                "action_data: " + actionDataJson,
        )

        if (domain == null || action == null || actionDataJson == null) {
            Timber.w("Action Call Data incomplete.  Aborting action call")
        } else {
            // If everything loaded correctly, package the action data and attempt the call
            try {
                // Convert JSON to HashMap
                val actionDataMap = kotlinJsonMapper.decodeFromString<Map<String, Any?>>(
                    MapAnySerializer,
                    actionDataJson,
                ).toMutableMap()

                if (actionDataMap["entity_id"] != null) {
                    val entityIdWithoutBrackets = Pattern.compile("\\[(.*?)\\]")
                        .matcher(actionDataMap["entity_id"].toString())
                    if (entityIdWithoutBrackets.find()) {
                        val value = entityIdWithoutBrackets.group(1)
                        if (value != null) {
                            if (value == "all" ||
                                value.split(",").contains("all")
                            ) {
                                actionDataMap["entity_id"] = "all"
                            }
                        }
                    }
                }

                Timber.d("Sending action call to Home Assistant")
                serverManager.integrationRepository(widget.serverId).callAction(domain, action, actionDataMap)
                Timber.d("Action call sent successfully")

                // If action call does not throw an exception, send positive feedback
                feedbackColor = R.drawable.widget_button_background_green
                feedbackIcon = R.drawable.ic_check_black_24dp
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Could not send action call.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, commonR.string.action_failure, Toast.LENGTH_LONG).show()
                }
            }
        }

        // Update widget and set visibilities for feedback
        val feedbackViews = RemoteViews(context.packageName, R.layout.widget_button)
        feedbackViews.setInt(R.id.widgetLayout, "setBackgroundResource", feedbackColor)
        feedbackViews.setImageViewResource(R.id.widgetImageButton, feedbackIcon)
        feedbackViews.setViewVisibility(R.id.widgetProgressBar, View.INVISIBLE)
        feedbackViews.setViewVisibility(R.id.widgetLabelLayout, View.GONE)
        feedbackViews.setViewVisibility(R.id.widgetImageButtonLayout, View.VISIBLE)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, feedbackViews)

        // Set a timer to change it back after 1 second
        delay(1.seconds)
        if (widget.isToggleWidget()) {
            // Instant render from cache (updated by the WebSocket event), then verify over REST
            appWidgetManager.updateAppWidget(appWidgetId, getWidgetRemoteViews(context, appWidgetId))
            refreshToggleState(context, appWidgetId)
            delay(TOGGLE_STATE_RECONNECTION_DELAY)
            refreshToggleState(context, appWidgetId)
            return
        }

        // Reload default views in the coroutine to pass to the post handler
        val views = getWidgetRemoteViews(context, appWidgetId)
        setLabelVisibility(views, widget)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override suspend fun getAllWidgetIdsWithEntities(context: Context): Map<Int, Pair<Int, List<String>>> {
        return dao.getAll().associate { widget ->
            widget.id to (widget.serverId to listOfNotNull(getToggleEntityId(widget)))
        }
    }

    override suspend fun onEntityStateChanged(context: Context, appWidgetId: Int, entity: Entity) {
        toggleStates[appWidgetId] = entity
        val views = getWidgetRemoteViews(context, appWidgetId, entity)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    override suspend fun onReceiveIntentNotHandled(context: Context, intent: Intent, appWidgetId: Int) {
        when (intent.action) {
            CALL_SERVICE_AUTH -> authThenCallConfiguredAction(context, appWidgetId)
            CALL_SERVICE -> {
                // Refresh the subscription in parallel so the action and spinner are not delayed
                widgetScope.launch { onScreenOn(context, forceRefreshWidgetId = appWidgetId) }
                callConfiguredAction(context, appWidgetId)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        widgetScope.launch {
            dao.deleteAll(appWidgetIds)
            appWidgetIds.forEach {
                toggleStates.remove(it)
                removeSubscription(it)
            }
        }
    }
}
