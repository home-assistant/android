package io.homeassistant.companion.android.widgets.multi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.MultiWidgetDao
import io.homeassistant.companion.android.database.widget.MultiWidgetEntity
import io.homeassistant.companion.android.widgets.DaggerProviderComponent
import java.util.regex.Pattern
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MultiWidget : AppWidgetProvider() {
    companion object {
        private const val TAG = "MultiWidget"
        private const val CALL_LOWER_SERVICE =
            "io.homeassistant.companion.android.widgets.multi.MultiWidget.CALL_LOWER_SERVICE"
        private const val CALL_UPPER_SERVICE =
            "io.homeassistant.companion.android.widgets.multi.MultiWidget.CALL_UPPER_SERVICE"
        internal const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.multi.MultiWidget.RECEIVE_DATA"
        private const val UPDATE_WIDGET =
            "io.homeassistant.companion.android.widgets.multi.MultiWidget.UPDATE_WIDGET"

        internal const val EXTRA_UPPER_BUTTON = "EXTRA_UPPER_BUTTON"
        internal const val EXTRA_UPPER_ICON_ID = "EXTRA_UPPER_ICON_ID"
        internal const val EXTRA_UPPER_DOMAIN = "EXTRA_UPPER_DOMAIN"
        internal const val EXTRA_UPPER_SERVICE = "EXTRA_UPPER_SERVICE"
        internal const val EXTRA_UPPER_SERVICE_DATA = "EXTRA_UPPER_SERVICE_DATA"
        internal const val EXTRA_LOWER_BUTTON = "EXTRA_LOWER_BUTTON"
        internal const val EXTRA_LOWER_ICON_ID = "EXTRA_LOWER_ICON_ID"
        internal const val EXTRA_LOWER_DOMAIN = "EXTRA_LOWER_DOMAIN"
        internal const val EXTRA_LOWER_SERVICE = "EXTRA_LOWER_SERVICE"
        internal const val EXTRA_LOWER_SERVICE_DATA = "EXTRA_LOWER_SERVICE_DATA"
        internal const val EXTRA_LABEL_TYPE = "EXTRA_LABEL_TYPE"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val EXTRA_TEMPLATE = "EXTRA_TEMPLATE"

        internal const val LABEL_PLAINTEXT = 0
        internal const val LABEL_TEMPLATE = 1
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    lateinit var multiWidgetDao: MultiWidgetDao

    private var iconPack: IconPack? = null

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    val Boolean.int
        get() = if (this) 1 else 0

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        multiWidgetDao = AppDatabase.getInstance(context).multiWidgetDao()
        // There may be multiple widgets active, so update all of them
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(
                context,
                appWidgetId,
                appWidgetManager
            )
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    ) {
        mainScope.launch {
            val views = getWidgetRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        Log.d(
            TAG, "Broadcast received: " + System.lineSeparator() +
                    "Broadcast action: " + action + System.lineSeparator() +
                    "AppWidgetId: " + appWidgetId
        )

        ensureInjected(context)

        multiWidgetDao = AppDatabase.getInstance(context).multiWidgetDao()

        super.onReceive(context, intent)
        when (action) {
            UPDATE_WIDGET -> updateAppWidget(context, appWidgetId)
            RECEIVE_DATA -> saveConfiguration(context, intent.extras, appWidgetId)
            CALL_UPPER_SERVICE -> callService(context, appWidgetId, true)
            CALL_LOWER_SERVICE -> callService(context, appWidgetId, false)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        multiWidgetDao = AppDatabase.getInstance(context).multiWidgetDao()
        // When the user deletes the widget, delete the data associated with it.
        for (appWidgetId in appWidgetIds) {
            multiWidgetDao.delete(appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    private suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        // Every time AppWidgetManager.updateAppWidget(...) is called, the click listeners
        // and label/template need to be re-assigned, or  the widget will fall back on its
        // default layout without any click listeners being applied

        val updateIntent = Intent(context, MultiWidget::class.java).apply {
            action = UPDATE_WIDGET
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val lowerServiceIntent = Intent(context, MultiWidget::class.java).apply {
            action = CALL_LOWER_SERVICE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val upperServiceIntent = Intent(context, MultiWidget::class.java).apply {
            action = CALL_UPPER_SERVICE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val widget = multiWidgetDao.get(appWidgetId)

        // Create an icon pack and load all drawables if a button is present
        if (widget != null) {
            if ((widget.upperButton == 1) || (widget.lowerButton == 1)) {
                if (iconPack == null) {
                    val loader = IconPackLoader(context)
                    iconPack = createMaterialDesignIconPack(loader)
                    iconPack!!.loadDrawables(loader.drawableLoader)
                }
            }
        }

        return RemoteViews(context.packageName, R.layout.widget_multi).apply {
            // Set on-click pending intents
            setOnClickPendingIntent(
                R.id.widgetLabel,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    updateIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            setOnClickPendingIntent(
                R.id.widgetImageButtonUpper,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    upperServiceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            setOnClickPendingIntent(
                R.id.widgetImageButtonLower,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    lowerServiceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

            if (widget != null) {
                // If there are buttons, set button icons
                if (widget.lowerButton == 1) {
                    val iconId = widget.lowerIconId ?: 988171 // Lightning bolt
                    val iconDrawable = iconPack?.icons?.get(iconId)?.drawable
                    if (iconDrawable != null) {
                        val icon = DrawableCompat.wrap(iconDrawable)
                        setImageViewBitmap(R.id.widgetImageButtonLower, icon.toBitmap())
                    }
                } else {
                    setViewVisibility(R.id.widgetImageButtonLower, View.GONE)
                }

                if (widget.upperButton == 1) {
                    val iconId = widget.upperIconId ?: 988171 // Lightning bolt
                    val iconDrawable = iconPack?.icons?.get(iconId)?.drawable
                    if (iconDrawable != null) {
                        val icon = DrawableCompat.wrap(iconDrawable)
                        setImageViewBitmap(R.id.widgetImageButtonUpper, icon.toBitmap())
                    }
                } else {
                    setViewVisibility(R.id.widgetImageButtonUpper, View.GONE)
                }

                // Set label/template text
                when (widget.labelType) {
                    LABEL_PLAINTEXT -> {
                        if ((widget.label == null) || (widget.label == "")) {
                            setViewVisibility(R.id.widgetLabelLayout, View.GONE)
                        }
                        setTextViewText(R.id.widgetLabel, widget.label)
                    }
                    LABEL_TEMPLATE -> {
                        var renderedTemplate = "Loading"
                        try {
                            renderedTemplate =
                                integrationUseCase.renderTemplate(
                                    widget.template as String,
                                    mapOf()
                                )
                        } catch (e: Exception) {
                            Log.e(TAG, "Unable to render template: ${widget.template}", e)
                        }
                        setTextViewText(R.id.widgetLabel, renderedTemplate)
                    }
                }
            }
        }
    }

    private fun saveConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        // Retrieve configuration values from extras bundle
        val upperButton: Boolean? = extras.getBoolean(EXTRA_UPPER_BUTTON)
        val upperIconId: Int? = extras.getInt(EXTRA_UPPER_ICON_ID)
        val upperDomain: String? = extras.getString(EXTRA_UPPER_DOMAIN)
        val upperService: String? = extras.getString(EXTRA_UPPER_SERVICE)
        val upperServiceData: String? = extras.getString(EXTRA_UPPER_SERVICE_DATA)
        val lowerButton: Boolean? = extras.getBoolean(EXTRA_LOWER_BUTTON)
        val lowerIconId: Int? = extras.getInt(EXTRA_LOWER_ICON_ID)
        val lowerDomain: String? = extras.getString(EXTRA_LOWER_DOMAIN)
        val lowerService: String? = extras.getString(EXTRA_LOWER_SERVICE)
        val lowerServiceData: String? = extras.getString(EXTRA_LOWER_SERVICE_DATA)
        val labelType: Int? = extras.getInt(EXTRA_LABEL_TYPE)
        val label: String? = extras.getString(EXTRA_LABEL)
        val template: String? = extras.getString(EXTRA_TEMPLATE)

        // First verification
        if (upperButton == null || lowerButton == null || labelType == null) {
            Log.e(TAG, "Did not receive complete configuration")
            return
        }

        if (upperButton) {
            // Additional verification
            if (upperDomain == null || upperService == null || upperServiceData == null) {
                Log.e(TAG, "Did not receive complete service call data for upper button")
                return
            }
        }

        if (lowerButton) {
            // Additional verification
            if (lowerDomain == null || lowerService == null || lowerServiceData == null) {
                Log.e(TAG, "Did not receive complete service call data for lower button")
                return
            }
        }

        mainScope.launch {
            Log.d(
                TAG, "Saving multi widget config data:" + System.lineSeparator() +
                        "upperButton: " + upperButton + System.lineSeparator() +
                        "upperIconId: " + upperIconId + System.lineSeparator() +
                        "upperDomain: " + upperDomain + System.lineSeparator() +
                        "upperService: " + upperService + System.lineSeparator() +
                        "upperServiceData: " + upperServiceData + System.lineSeparator() +
                        "lowerButton: " + lowerButton + System.lineSeparator() +
                        "lowerIconId: " + lowerIconId + System.lineSeparator() +
                        "lowerDomain: " + lowerDomain + System.lineSeparator() +
                        "lowerService: " + lowerService + System.lineSeparator() +
                        "lowerServiceData: " + lowerServiceData + System.lineSeparator() +
                        "labelType: " + labelType + System.lineSeparator() +
                        "label: " + label + System.lineSeparator() +
                        "template: " + template + System.lineSeparator()
            )

            multiWidgetDao.add(
                MultiWidgetEntity(
                    appWidgetId,
                    upperButton.int,
                    upperIconId,
                    upperDomain,
                    upperService,
                    upperServiceData,
                    lowerButton.int,
                    lowerIconId,
                    lowerDomain,
                    lowerService,
                    lowerServiceData,
                    labelType,
                    label,
                    template
                )
            )

            // It is the responsibility of the configuration activity to update the app widget
            // This method is only called during the initial setup of the widget,
            // so rather than duplicating code in the ButtonWidgetConfigurationActivity,
            // it is just calling onUpdate manually here.
            updateAppWidget(context, appWidgetId)
        }
    }

    private fun callService(context: Context, appWidgetId: Int, upper: Boolean) {
        val widget = multiWidgetDao.get(appWidgetId)

        if (widget == null) {
            Log.e(TAG, "Could not retrieve widget from database; aborting.")
            return
        }

        mainScope.launch {
            // Load the service call data from database
            val domain: String?
            val service: String?
            val serviceDataJson: String?

            if (upper) {
                domain = widget.upperDomain
                service = widget.upperService
                serviceDataJson = widget.lowerServiceData
            } else {
                domain = widget.lowerDomain
                service = widget.lowerService
                serviceDataJson = widget.lowerServiceData
            }

            Log.d(
                TAG, "Service Call Data loaded:" + System.lineSeparator() +
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
                                if (value == "all" || value.split(",").contains("all")) {
                                    serviceDataMap["entity_id"] = "all"
                                }
                            }
                        }
                    }

                    integrationUseCase.callService(domain, service, serviceDataMap)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not send service call.", e)
                }
            }

            // Update app widget once service call has been made
            // in case template needs to display a state change
            updateAppWidget(context, appWidgetId)
        }
    }

    private fun ensureInjected(context: Context) {
        if (context.applicationContext is GraphComponentAccessor) {
            DaggerProviderComponent.builder()
                .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
                .build()
                .inject(this)
        } else {
            throw Exception("Application Context passed is not of our application!")
        }
    }
}
