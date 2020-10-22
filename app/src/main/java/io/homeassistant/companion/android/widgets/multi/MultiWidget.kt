package io.homeassistant.companion.android.widgets.multi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import com.maltaisn.icondialog.pack.IconPack
import com.maltaisn.icondialog.pack.IconPackLoader
import com.maltaisn.iconpack.mdi.createMaterialDesignIconPack
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import io.homeassistant.companion.android.database.widget.MultiWidgetDao
import io.homeassistant.companion.android.widgets.DaggerProviderComponent
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

        internal const val EXTRA_DOMAIN = "EXTRA_DOMAIN"
        internal const val EXTRA_SERVICE = "EXTRA_SERVICE"
        internal const val EXTRA_SERVICE_DATA = "EXTRA_SERVICE_DATA"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val EXTRA_ICON = "EXTRA_ICON"

        private const val LABEL_PLAINTEXT = 0
        private const val LABEL_TEMPLATE = 1
    }

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    lateinit var multiWidgetDao: MultiWidgetDao

    private var iconPack: IconPack? = null

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

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
        // Every time AppWidgetManager.updateAppWidget(...) is called, the button listener
        // and label need to be re-assigned, or the next time the layout updates
        // (e.g home screen rotation) the widget will fall back on its default layout
        // without any click listener being applied

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
                R.id.widgetLabel,
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
                }

                if (widget.upperButton == 1) {
                    val iconId = widget.upperIconId ?: 988171 // Lightning bolt
                    val iconDrawable = iconPack?.icons?.get(iconId)?.drawable
                    if (iconDrawable != null) {
                        val icon = DrawableCompat.wrap(iconDrawable)
                        setImageViewBitmap(R.id.widgetImageButtonUpper, icon.toBitmap())
                    }
                }

                // Set label/template text
                when (widget.labelType) {
                    LABEL_PLAINTEXT -> {
                        setTextViewText(R.id.widgetTemplateText, widget.label ?: "")
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
                        setTextViewText(
                            R.id.widgetTemplateText,
                            renderedTemplate
                        )
                    }
                }
            }
        }
    }

    private fun saveConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return
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
