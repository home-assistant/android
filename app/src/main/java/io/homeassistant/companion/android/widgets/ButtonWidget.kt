package io.homeassistant.companion.android.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
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
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ButtonWidget : AppWidgetProvider() {
    companion object {
        private const val TAG = "ButtonWidget"
        private const val CALL_SERVICE =
            "io.homeassistant.companion.android.widgets.ButtonWidget.CALL_SERVICE"
        internal const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.ButtonWidget.RECEIVE_DATA"

        internal const val EXTRA_DOMAIN = "EXTRA_DOMAIN"
        internal const val EXTRA_SERVICE = "EXTRA_SERVICE"
        internal const val EXTRA_SERVICE_DATA = "EXTRA_SERVICE_DATA"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val EXTRA_ICON = "EXTRA_ICON"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    lateinit var buttonWidgetDao: ButtonWidgetDao

    private var iconPack: IconPack? = null

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        buttonWidgetDao = AppDatabase.getInstance(context).buttonWidgetDao()
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            mainScope.launch {
                val views = getWidgetRemoteViews(context, appWidgetId)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        buttonWidgetDao = AppDatabase.getInstance(context).buttonWidgetDao()
        // When the user deletes the widget, delete the preference associated with it.
        for (appWidgetId in appWidgetIds) {
            buttonWidgetDao.delete(appWidgetId)
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
            TAG, "Broadcast received: " + System.lineSeparator() +
                    "Broadcast action: " + action + System.lineSeparator() +
                    "AppWidgetId: " + appWidgetId
        )

        ensureInjected(context)

        buttonWidgetDao = AppDatabase.getInstance(context).buttonWidgetDao()

        super.onReceive(context, intent)
        when (action) {
            CALL_SERVICE -> callConfiguredService(context, appWidgetId)
            RECEIVE_DATA -> saveServiceCallConfiguration(context, intent.extras, appWidgetId)
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
        return RemoteViews(context.packageName, R.layout.widget_button).apply {
            val iconId = widget?.iconId ?: 988171 // Lightning bolt

            val iconDrawable = iconPack?.icons?.get(iconId)?.drawable
            if (iconDrawable != null) {
                val icon = DrawableCompat.wrap(iconDrawable)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    DrawableCompat.setTint(icon, context.resources.getColor(R.color.colorIcon, context.theme))
                }
                setImageViewBitmap(R.id.widgetImageButton, icon.toBitmap())
            }

            setOnClickPendingIntent(
                R.id.widgetImageButtonLayout,
                PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            setTextViewText(
                R.id.widgetLabel,
                widget?.label ?: ""
            )
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
            // Change color of background image for feedback
            var views = getWidgetRemoteViews(context, appWidgetId)

            // Set default feedback as negative
            var feedbackColor = R.drawable.widget_button_background_red
            var feedbackIcon = R.drawable.ic_clear_black

            // Load the service call data from Shared Preferences
            val domain = widget?.domain
            val service = widget?.service
            val serviceDataJson = widget?.serviceData

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

                    integrationUseCase.callService(domain, service, serviceDataMap)

                    // If service call does not throw an exception, send positive feedback
                    feedbackColor = R.drawable.widget_button_background_green
                    feedbackIcon = R.drawable.ic_check_black_24dp
                } catch (e: Exception) {
                    Log.e(TAG, "Could not send service call.", e)
                }
            }

            // Update widget and set visibilities for feedback
            views.setInt(R.id.widgetLayout, "setBackgroundResource", feedbackColor)
            views.setImageViewResource(R.id.widgetImageButton, feedbackIcon)
            views.setViewVisibility(R.id.widgetProgressBar, View.INVISIBLE)
            views.setViewVisibility(R.id.widgetLabelLayout, View.GONE)
            views.setViewVisibility(R.id.widgetImageButtonLayout, View.VISIBLE)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Reload default views in the coroutine to pass to the post handler
            views = getWidgetRemoteViews(context, appWidgetId)

            // Set a timer to change it back after 1 second
            Handler().postDelayed({
                views.setViewVisibility(R.id.widgetLabelLayout, View.VISIBLE)
                views.setInt(
                    R.id.widgetLayout,
                    "setBackgroundResource",
                    R.drawable.widget_button_background
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }, 1000)
        }
    }

    private fun saveServiceCallConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val domain: String? = extras.getString(EXTRA_DOMAIN)
        val service: String? = extras.getString(EXTRA_SERVICE)
        val serviceData: String? = extras.getString(EXTRA_SERVICE_DATA)
        val label: String? = extras.getString(EXTRA_LABEL)
        val icon: Int = extras.getInt(EXTRA_ICON)

        if (domain == null || service == null || serviceData == null) {
            Log.e(TAG, "Did not receive complete service call data")
            return
        }

        mainScope.launch {
            Log.d(
                TAG, "Saving service call config data:" + System.lineSeparator() +
                        "domain: " + domain + System.lineSeparator() +
                        "service: " + service + System.lineSeparator() +
                        "service_data: " + serviceData + System.lineSeparator() +
                        "label: " + label
            )

            val widget = ButtonWidgetEntity(appWidgetId, icon, domain, service, serviceData, label)
            buttonWidgetDao.add(widget)

            // It is the responsibility of the configuration activity to update the app widget
            // This method is only called during the initial setup of the widget,
            // so rather than duplicating code in the ButtonWidgetConfigurationActivity,
            // it is just calling onUpdate manually here.
            onUpdate(context, AppWidgetManager.getInstance(context), intArrayOf(appWidgetId))
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
