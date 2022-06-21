package io.homeassistant.companion.android.widgets.button

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.android.material.color.DynamicColors
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.database.widget.WidgetBackgroundType
import io.homeassistant.companion.android.util.getAttribute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import javax.inject.Inject
import io.homeassistant.companion.android.common.R as commonR

@AndroidEntryPoint
class ButtonWidget : AppWidgetProvider() {
    companion object {
        private const val TAG = "ButtonWidget"
        private const val CALL_SERVICE =
            "io.homeassistant.companion.android.widgets.button.ButtonWidget.CALL_SERVICE"
        internal const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.button.ButtonWidget.RECEIVE_DATA"

        internal const val EXTRA_DOMAIN = "EXTRA_DOMAIN"
        internal const val EXTRA_SERVICE = "EXTRA_SERVICE"
        internal const val EXTRA_SERVICE_DATA = "EXTRA_SERVICE_DATA"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val EXTRA_ICON = "EXTRA_ICON"
        internal const val EXTRA_BACKGROUND_TYPE = "EXTRA_BACKGROUND_TYPE"
        internal const val EXTRA_TEXT_COLOR = "EXTRA_TEXT_COLOR"

        // Vector icon rendering resolution fallback (if we can't infer via AppWidgetManager for some reason)
        private const val DEFAULT_MAX_ICON_SIZE = 512
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    @Inject
    lateinit var buttonWidgetDao: ButtonWidgetDao

    private var iconPack: IconPack? = null

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
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
            val buttonWidgetEntityList = buttonWidgetDao.getAll()
            if (buttonWidgetEntityList.isNotEmpty()) {
                Log.d(TAG, "Updating all widgets")
                val appWidgetManager = AppWidgetManager.getInstance(context)
                for (item in buttonWidgetEntityList) {
                    val views = getWidgetRemoteViews(context, item.id)

                    views.setViewVisibility(R.id.widgetProgressBar, View.INVISIBLE)
                    views.setViewVisibility(R.id.widgetImageButtonLayout, View.VISIBLE)
                    views.setViewVisibility(R.id.widgetLabelLayout, View.VISIBLE)
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

        Log.d(
            TAG,
            "Broadcast received: " + System.lineSeparator() +
                "Broadcast action: " + action + System.lineSeparator() +
                "AppWidgetId: " + appWidgetId
        )

        super.onReceive(context, intent)
        when (action) {
            CALL_SERVICE -> callConfiguredService(context, appWidgetId)
            RECEIVE_DATA -> saveServiceCallConfiguration(context, intent.extras, appWidgetId)
            Intent.ACTION_SCREEN_ON -> updateAllWidgets(context)
        }
    }

    private fun getWidgetRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        // Every time AppWidgetManager.updateAppWidget(...) is called, the button listener
        // and label need to be re-assigned, or the next time the layout updates
        // (e.g home screen rotation) the widget will fall back on its default layout
        // without any click listener being applied

        val intent = Intent(context, ButtonWidget::class.java).apply {
            action = CALL_SERVICE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val widget = buttonWidgetDao.get(appWidgetId)

        // Create an icon pack and load all drawables.
        if (iconPack == null) {
            val loader = IconPackLoader(context)
            iconPack = createMaterialDesignIconPack(loader)
            iconPack!!.loadDrawables(loader.drawableLoader)
        }
        val useDynamicColors = widget?.backgroundType == WidgetBackgroundType.DYNAMICCOLOR && DynamicColors.isDynamicColorAvailable()
        return RemoteViews(context.packageName, if (useDynamicColors) R.layout.widget_button_wrapper_dynamiccolor else R.layout.widget_button_wrapper_default).apply {
            // Theming
            var textColor = context.getAttribute(R.attr.colorWidgetOnBackground, ContextCompat.getColor(context, commonR.color.colorWidgetButtonLabel))
            if (widget?.backgroundType == WidgetBackgroundType.TRANSPARENT) {
                widget.textColor?.let { textColor = it.toColorInt() }
                setTextColor(R.id.widgetLabel, textColor)
            }
            setWidgetBackground(this, widget)

            // Content
            val iconId = widget?.iconId ?: 988171 // Lightning bolt

            val iconDrawable = iconPack?.icons?.get(iconId)?.drawable
            if (iconDrawable != null) {
                val icon = DrawableCompat.wrap(iconDrawable)
                if (widget?.backgroundType == WidgetBackgroundType.TRANSPARENT) {
                    setInt(R.id.widgetImageButton, "setColorFilter", textColor)
                }

                // Determine reasonable dimensions for drawing vector icon as a bitmap
                val aspectRatio = iconDrawable.intrinsicWidth / iconDrawable.intrinsicHeight.toDouble()
                val awo = if (widget != null) AppWidgetManager.getInstance(context).getAppWidgetOptions(widget.id) else null
                val maxWidth = awo?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) ?: DEFAULT_MAX_ICON_SIZE
                val maxHeight = awo?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: DEFAULT_MAX_ICON_SIZE
                var width: Int
                var height: Int
                if (maxWidth > maxHeight) {
                    width = maxWidth
                    height = (maxWidth * (1 / aspectRatio)).toInt()
                } else {
                    width = (maxHeight * aspectRatio).toInt()
                    height = maxHeight
                }

                // Render the icon into the Button's ImageView
                setImageViewBitmap(R.id.widgetImageButton, icon.toBitmap(width, height))
            }

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

    private fun callConfiguredService(context: Context, appWidgetId: Int) {
        Log.d(TAG, "Calling widget service")

        // Set up progress bar as immediate feedback to show the click has been received
        // Success or failure feedback will come from the mainScope coroutine
        val loadingViews = RemoteViews(context.packageName, R.layout.widget_button)
        val appWidgetManager = AppWidgetManager.getInstance(context)

        loadingViews.setViewVisibility(R.id.widgetProgressBar, View.VISIBLE)
        loadingViews.setViewVisibility(R.id.widgetImageButtonLayout, View.GONE)
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, loadingViews)

        val widget = buttonWidgetDao.get(appWidgetId)

        mainScope.launch {
            // Set default feedback as negative
            var feedbackColor = R.drawable.widget_button_background_red
            var feedbackIcon = R.drawable.ic_clear_black

            // Load the service call data from Shared Preferences
            val domain = widget?.domain
            val service = widget?.service
            val serviceDataJson = widget?.serviceData

            Log.d(
                TAG,
                "Service Call Data loaded:" + System.lineSeparator() +
                    "domain: " + domain + System.lineSeparator() +
                    "service: " + service + System.lineSeparator() +
                    "service_data: " + serviceDataJson
            )

            if (domain == null || service == null || serviceDataJson == null) {
                Log.w(TAG, "Service Call Data incomplete.  Aborting service call")
            } else {
                // If everything loaded correctly, package the service data and attempt the call
                try {

                    // Convert JSON to HashMap
                    val serviceDataMap: HashMap<String, Any> =
                        jacksonObjectMapper().readValue(serviceDataJson)

                    if (serviceDataMap["entity_id"] != null) {
                        val entityIdWithoutBrackets = Pattern.compile("\\[(.*?)\\]")
                            .matcher(serviceDataMap["entity_id"].toString())
                        if (entityIdWithoutBrackets.find()) {
                            val value = entityIdWithoutBrackets.group(1)
                            if (value != null) {
                                if (value == "all" ||
                                    value.split(",").contains("all")
                                ) {
                                    serviceDataMap["entity_id"] = "all"
                                }
                            }
                        }
                    }

                    Log.d(TAG, "Sending service call to Home Assistant")
                    integrationUseCase.callService(domain, service, serviceDataMap)
                    Log.d(TAG, "Service call sent successfully")

                    // If service call does not throw an exception, send positive feedback
                    feedbackColor = R.drawable.widget_button_background_green
                    feedbackIcon = R.drawable.ic_check_black_24dp
                } catch (e: Exception) {
                    Log.e(TAG, "Could not send service call.", e)
                    Toast.makeText(context, commonR.string.service_call_failure, Toast.LENGTH_LONG).show()
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
                    views.setViewVisibility(R.id.widgetLabelLayout, View.VISIBLE)
                    setWidgetBackground(views, widget)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                },
                1000
            )
        }
    }

    private fun saveServiceCallConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val domain: String? = extras.getString(EXTRA_DOMAIN)
        val service: String? = extras.getString(EXTRA_SERVICE)
        val serviceData: String? = extras.getString(EXTRA_SERVICE_DATA)
        val label: String? = extras.getString(EXTRA_LABEL)
        val icon: Int = extras.getInt(EXTRA_ICON)
        val backgroundType: WidgetBackgroundType = extras.getSerializable(EXTRA_BACKGROUND_TYPE) as WidgetBackgroundType
        val textColor: String? = extras.getString(EXTRA_TEXT_COLOR)

        if (domain == null || service == null || serviceData == null) {
            Log.e(TAG, "Did not receive complete service call data")
            return
        }

        mainScope.launch {
            Log.d(
                TAG,
                "Saving service call config data:" + System.lineSeparator() +
                    "domain: " + domain + System.lineSeparator() +
                    "service: " + service + System.lineSeparator() +
                    "service_data: " + serviceData + System.lineSeparator() +
                    "label: " + label
            )

            val widget = ButtonWidgetEntity(appWidgetId, icon, domain, service, serviceData, label, backgroundType, textColor)
            buttonWidgetDao.add(widget)

            // It is the responsibility of the configuration activity to update the app widget
            // This method is only called during the initial setup of the widget,
            // so rather than duplicating code in the ButtonWidgetConfigurationActivity,
            // it is just calling onUpdate manually here.
            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
        }
    }
}
