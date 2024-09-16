package io.homeassistant.companion.android.widgets.button

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.View.VISIBLE
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import androidx.core.os.BundleCompat
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.repositories.ButtonWidgetRepository
import io.homeassistant.companion.android.util.getAttribute
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.common.WidgetAuthenticationActivity
import java.util.regex.Pattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ButtonWidget : BaseWidgetProvider<ButtonWidgetRepository, Entity<Map<String, Any>>>() {
    companion object {
        private const val TAG = "ButtonWidget"
        const val CALL_SERVICE =
            "io.homeassistant.companion.android.widgets.button.ButtonWidget.CALL_SERVICE"
        private const val CALL_SERVICE_AUTH =
            "io.homeassistant.companion.android.widgets.button.ButtonWidget.CALL_SERVICE_AUTH"

        internal const val EXTRA_SERVER_ID = "EXTRA_SERVER_ID"
        internal const val EXTRA_DOMAIN = "EXTRA_DOMAIN"
        internal const val EXTRA_ACTION = "EXTRA_SERVICE"
        internal const val EXTRA_ACTION_DATA = "EXTRA_SERVICE_DATA"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val EXTRA_ICON_NAME = "EXTRA_ICON_NAME"
        internal const val EXTRA_BACKGROUND_TYPE = "EXTRA_BACKGROUND_TYPE"
        internal const val EXTRA_TEXT_COLOR = "EXTRA_TEXT_COLOR"
        internal const val EXTRA_REQUIRE_AUTHENTICATION = "EXTRA_REQUIRE_AUTHENTICATION"

        // Vector icon rendering resolution fallback (if we can't infer via AppWidgetManager for some reason)
        private const val DEFAULT_MAX_ICON_SIZE = 512
    }

    override fun getWidgetProvider(context: Context): ComponentName = ComponentName(context, ButtonWidget::class.java)

    override suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int, hasActiveConnection: Boolean, suggestedEntity: Entity<Map<String, Any>>?): RemoteViews {
        // Every time AppWidgetManager.updateAppWidget(...) is called, the button listener
        // and label need to be re-assigned, or the next time the layout updates
        // (e.g home screen rotation) the widget will fall back on its default layout
        // without any click listener being applied

        val widget = repository.get(appWidgetId)
        val auth = widget?.requireAuthentication == true

        val intent = Intent(context, ButtonWidget::class.java).apply {
            action = if (auth) CALL_SERVICE_AUTH else CALL_SERVICE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val useDynamicColors = widget?.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable()
        return RemoteViews(context.packageName, if (useDynamicColors) R.layout.widget_button_wrapper_dynamiccolor else R.layout.widget_button_wrapper_default).apply {
            // Theming
            var textColor = context.getAttribute(R.attr.colorWidgetOnBackground, ContextCompat.getColor(context, commonR.color.colorWidgetButtonLabel))
            if (widget?.backgroundType == WidgetBackgroundType.TRANSPARENT) {
                widget.textColor?.let { textColor = it.toColorInt() }
                setTextColor(R.id.widgetLabel, textColor)
            }

            setWidgetBackground(this, R.id.widgetLayout, widget)

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
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            setTextViewText(
                R.id.widgetLabel,
                widget?.label ?: ""
            )
        }
    }

    override fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val serverId = if (extras.containsKey(EXTRA_SERVER_ID)) extras.getInt(EXTRA_SERVER_ID) else null
        val domain: String? = extras.getString(EXTRA_DOMAIN)
        val action: String? = extras.getString(EXTRA_ACTION)
        val actionData: String? = extras.getString(EXTRA_ACTION_DATA)
        val label: String? = extras.getString(EXTRA_LABEL)
        val requireAuthentication: Boolean = extras.getBoolean(EXTRA_REQUIRE_AUTHENTICATION)
        val icon: String = extras.getString(EXTRA_ICON_NAME) ?: "mdi:flash"
        val backgroundType = BundleCompat.getSerializable(extras, EXTRA_BACKGROUND_TYPE, WidgetBackgroundType::class.java)
            ?: WidgetBackgroundType.DAYNIGHT
        val textColor: String? = extras.getString(EXTRA_TEXT_COLOR)

        if (serverId == null || domain == null || action == null || actionData == null) {
            Log.e(TAG, "Did not receive complete action call data")
            return
        }

        widgetScope?.launch {
            Log.d(
                TAG,
                "Saving action call config data:" + System.lineSeparator() +
                    "domain: " + domain + System.lineSeparator() +
                    "action: " + action + System.lineSeparator() +
                    "action_data: " + actionData + System.lineSeparator() +
                    "require_authentication: " + requireAuthentication + System.lineSeparator() +
                    "label: " + label
            )

            val widget = ButtonWidgetEntity(
                appWidgetId,
                serverId,
                null,
                icon,
                domain,
                action,
                actionData,
                label,
                backgroundType,
                textColor,
                requireAuthentication
            )

            repository.add(widget)

            // It is the responsibility of the configuration activity to update the app widget
            // This method is only called during the initial setup of the widget,
            // so rather than duplicating code in the ButtonWidgetConfigurationActivity,
            // it is just calling onUpdate manually here.
            // onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        super.onReceive(context, intent)
        when (action) {
            CALL_SERVICE_AUTH -> authThenCallConfiguredAction(context, appWidgetId)
            CALL_SERVICE -> callConfiguredAction(context, appWidgetId)
        }
    }

    override suspend fun getUpdates(serverId: Int, entityIds: List<String>): Flow<Entity<Map<String, Any>>> = serverManager.integrationRepository(serverId).getEntityUpdates(entityIds) as Flow<Entity<Map<String, Any>>>

    private fun authThenCallConfiguredAction(context: Context, appWidgetId: Int) {
        Log.d(TAG, "Calling authentication, then configured action")

        val intent = Intent(context, WidgetAuthenticationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        context.startActivity(intent)
    }

    private fun setLabelVisibility(views: RemoteViews, widget: ButtonWidgetEntity?) {
        val labelVisibility = if (widget?.label.isNullOrBlank()) View.GONE else View.VISIBLE
        views.setViewVisibility(R.id.widgetLabelLayout, labelVisibility)
    }

    private fun callConfiguredAction(context: Context, appWidgetId: Int) {
        Log.d(TAG, "Calling widget action")

        // Set up progress bar as immediate feedback to show the click has been received
        // Success or failure feedback will come from the mainScope coroutine
        val loadingViews = RemoteViews(context.packageName, R.layout.widget_button)
        val appWidgetManager = AppWidgetManager.getInstance(context)

        loadingViews.setViewVisibility(R.id.widgetProgressBar, View.VISIBLE)
        loadingViews.setViewVisibility(R.id.widgetImageButtonLayout, View.GONE)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, loadingViews)

        val widget = repository.get(appWidgetId)

        widgetScope?.launch {
            // Set default feedback as negative
            var feedbackColor = R.drawable.widget_button_background_red
            var feedbackIcon = R.drawable.ic_clear_black

            // Load the action call data from Shared Preferences
            val domain = widget?.domain
            val action = widget?.service
            val actionDataJson = widget?.serviceData

            Log.d(
                TAG,
                "Action Call Data loaded:" + System.lineSeparator() +
                    "domain: " + domain + System.lineSeparator() +
                    "action: " + action + System.lineSeparator() +
                    "action_data: " + actionDataJson
            )

            if (domain == null || action == null || actionDataJson == null) {
                Log.w(TAG, "Action Call Data incomplete.  Aborting action call")
            } else {
                // If everything loaded correctly, package the action data and attempt the call
                try {
                    // Convert JSON to HashMap
                    val actionDataMap: HashMap<String, Any> =
                        jacksonObjectMapper().readValue(actionDataJson)

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

                    Log.d(TAG, "Sending action call to Home Assistant")
                    serverManager.integrationRepository(widget.serverId).callAction(domain, action, actionDataMap)
                    Log.d(TAG, "Action call sent successfully")

                    // If action call does not throw an exception, send positive feedback
                    feedbackColor = R.drawable.widget_button_background_green
                    feedbackIcon = R.drawable.ic_check_black_24dp
                } catch (e: Exception) {
                    Log.e(TAG, "Could not send action call.", e)
                    Toast.makeText(context, commonR.string.action_failure, Toast.LENGTH_LONG).show()
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
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    forceUpdateView(context, appWidgetId, appWidgetManager)
                },
                1000
            )
        }
    }
}
