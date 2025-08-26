package io.homeassistant.companion.android.widgets.button

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
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
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.FailFast
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.getAttribute
import io.homeassistant.companion.android.util.icondialog.getIconByMdiName
import io.homeassistant.companion.android.widgets.ACTION_APPWIDGET_CREATED
import io.homeassistant.companion.android.widgets.BaseWidgetProvider
import io.homeassistant.companion.android.widgets.BaseWidgetProvider.Companion.widgetScope
import io.homeassistant.companion.android.widgets.EXTRA_WIDGET_ENTITY
import io.homeassistant.companion.android.widgets.common.WidgetAuthenticationActivity
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class ButtonWidget : AppWidgetProvider() {
    companion object {
        const val CALL_SERVICE =
            "io.homeassistant.companion.android.widgets.button.ButtonWidget.CALL_SERVICE"
        private const val CALL_SERVICE_AUTH =
            "io.homeassistant.companion.android.widgets.button.ButtonWidget.CALL_SERVICE_AUTH"

        // Vector icon rendering resolution fallback (if we can't infer via AppWidgetManager for some reason)
        private const val DEFAULT_MAX_ICON_SIZE = 512
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var buttonWidgetDao: ButtonWidgetDao

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            mainScope.launch {
                val views = getWidgetRemoteViews(context, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private fun updateAllWidgets(context: Context) {
        mainScope.launch {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return@launch
            val systemWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ButtonWidget::class.java))
            val dbWidgetList = buttonWidgetDao.getAll()

            val invalidWidgetIds = dbWidgetList
                .filter { !systemWidgetIds.contains(it.id) }
                .map { it.id }
            if (invalidWidgetIds.isNotEmpty()) {
                Timber.i("Found widgets $invalidWidgetIds in database, but not in AppWidgetManager - sending onDeleted")
                onDeleted(context, invalidWidgetIds.toIntArray())
            }

            val buttonWidgetEntityList = dbWidgetList.filter { systemWidgetIds.contains(it.id) }
            if (buttonWidgetEntityList.isNotEmpty()) {
                Timber.d("Updating all widgets")
                for (item in buttonWidgetEntityList) {
                    val views = getWidgetRemoteViews(context, item.id)

                    setLabelVisibility(views, item)
                    views.setViewVisibility(R.id.widgetProgressBar, View.INVISIBLE)
                    views.setViewVisibility(R.id.widgetImageButtonLayout, View.VISIBLE)
                    appWidgetManager.updateAppWidget(item.id, views)
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // When the user deletes the widget, delete the preference associated with it.
        mainScope.launch {
            buttonWidgetDao.deleteAll(appWidgetIds)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        Timber.d(
            "Broadcast received: " + System.lineSeparator() +
                "Broadcast action: " + action + System.lineSeparator() +
                "AppWidgetId: " + appWidgetId,
        )

        super.onReceive(context, intent)
        when (action) {
            CALL_SERVICE_AUTH -> authThenCallConfiguredAction(context, appWidgetId)
            CALL_SERVICE -> callConfiguredAction(context, appWidgetId)
            BaseWidgetProvider.UPDATE_WIDGETS -> updateAllWidgets(context)
            Intent.ACTION_SCREEN_ON -> updateAllWidgets(context)
            ACTION_APPWIDGET_CREATED -> {
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    FailFast.fail { "Missing appWidgetId in intent to add widget in DAO" }
                } else {
                    widgetScope?.launch {
                        val entity = intent.extras?.let {
                            BundleCompat.getSerializable(
                                it,
                                EXTRA_WIDGET_ENTITY,
                                ButtonWidgetEntity::class.java,
                            )
                        }
                        entity?.let {
                            buttonWidgetDao.add(entity.copyWithWidgetId(appWidgetId))
                        } ?: FailFast.fail { "Missing $EXTRA_WIDGET_ENTITY or it's of the wrong type in intent." }
                    }
                }
                updateAllWidgets(context)
            }
        }
    }

    private fun authThenCallConfiguredAction(context: Context, appWidgetId: Int) {
        Timber.d("Calling authentication, then configured action")

        val intent = Intent(context, WidgetAuthenticationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        context.startActivity(intent)
    }

    private suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        // Every time AppWidgetManager.updateAppWidget(...) is called, the button listener
        // and label need to be re-assigned, or the next time the layout updates
        // (e.g home screen rotation) the widget will fall back on its default layout
        // without any click listener being applied

        val widget = buttonWidgetDao.get(appWidgetId)
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
            setWidgetBackground(this, widget)

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

    private fun setWidgetBackground(views: RemoteViews, widget: ButtonWidgetEntity?) {
        when (widget?.backgroundType) {
            WidgetBackgroundType.TRANSPARENT -> {
                views.setInt(R.id.widgetLayout, "setBackgroundColor", Color.TRANSPARENT)
            }

            else -> {
                views.setInt(R.id.widgetLayout, "setBackgroundResource", R.drawable.widget_button_background)
            }
        }
    }

    private fun setLabelVisibility(views: RemoteViews, widget: ButtonWidgetEntity?) {
        val labelVisibility = if (widget?.label.isNullOrBlank()) View.GONE else View.VISIBLE
        views.setViewVisibility(R.id.widgetLabelLayout, labelVisibility)
    }

    private fun callConfiguredAction(context: Context, appWidgetId: Int) {
        Timber.d("Calling widget action")

        // Set up progress bar as immediate feedback to show the click has been received
        // Success or failure feedback will come from the mainScope coroutine
        val loadingViews = RemoteViews(context.packageName, R.layout.widget_button)
        val appWidgetManager = AppWidgetManager.getInstance(context)

        loadingViews.setViewVisibility(R.id.widgetProgressBar, View.VISIBLE)
        loadingViews.setViewVisibility(R.id.widgetImageButtonLayout, View.GONE)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, loadingViews)

        mainScope.launch {
            val widget = buttonWidgetDao.get(appWidgetId)
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
                } catch (e: Exception) {
                    Timber.e(e, "Could not send action call.")
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

            // Reload default views in the coroutine to pass to the post handler
            val views = getWidgetRemoteViews(context, appWidgetId)

            // Set a timer to change it back after 1 second
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    setLabelVisibility(views, widget)
                    setWidgetBackground(views, widget)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                },
                1000,
            )
        }
    }
}
