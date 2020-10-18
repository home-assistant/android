package io.homeassistant.companion.android.widgets.multi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
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

class MultiWidget : AppWidgetProvider() {
    companion object {
        private const val TAG = "MultiWidget"
        private const val CALL_SERVICE =
            "io.homeassistant.companion.android.widgets.multi.MultiWidget.CALL_SERVICE"
        internal const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.multi.MultiWidget.RECEIVE_DATA"

        internal const val EXTRA_DOMAIN = "EXTRA_DOMAIN"
        internal const val EXTRA_SERVICE = "EXTRA_SERVICE"
        internal const val EXTRA_SERVICE_DATA = "EXTRA_SERVICE_DATA"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
        internal const val EXTRA_ICON = "EXTRA_ICON"
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
        for (appWidgetId in appWidgetIds) {
            appWidgetManager.updateAppWidget(
                appWidgetId,
                getWidgetRemoteViews(context, appWidgetId)
            )
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

    private fun getWidgetRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        // Every time AppWidgetManager.updateAppWidget(...) is called, the button listener
        // and label need to be re-assigned, or the next time the layout updates
        // (e.g home screen rotation) the widget will fall back on its default layout
        // without any click listener being applied

        val intent = Intent(context, MultiWidget::class.java).apply {
            action = CALL_SERVICE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

        val widget = multiWidgetDao.get(appWidgetId)

        // Create an icon pack and load all drawables.
        if (iconPack == null) {
            val loader = IconPackLoader(context)
            iconPack = createMaterialDesignIconPack(loader)
            iconPack!!.loadDrawables(loader.drawableLoader)
        }

        return RemoteViews(context.packageName, R.layout.widget_multi).apply {
            val iconId = widget?.iconId ?: 988171 // Lightning bolt

            val iconDrawable = iconPack?.icons?.get(iconId)?.drawable
            if (iconDrawable != null) {
                val icon = DrawableCompat.wrap(iconDrawable)
                setImageViewBitmap(R.id.widgetImageButton, icon.toBitmap())
            }

            setOnClickPendingIntent(
                R.id.widgetImageButtonUpper,
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
