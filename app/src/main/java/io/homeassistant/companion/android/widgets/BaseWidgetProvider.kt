package io.homeassistant.companion.android.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class BaseWidgetProvider : AppWidgetProvider() {

    companion object {
        const val UPDATE_VIEW =
            "io.homeassistant.companion.android.widgets.template.BaseWidgetProvider.UPDATE_VIEW"
        const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.template.TemplateWidget.RECEIVE_DATA"
    }

    private var entityUpdates: Flow<Entity<*>>? = null

    @Inject
    protected lateinit var integrationUseCase: IntegrationRepository
    protected var mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
    protected var lastIntent = ""

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

    override fun onReceive(context: Context, intent: Intent) {
        lastIntent = intent.action.toString()
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        super.onReceive(context, intent)
        when (lastIntent) {
            UPDATE_VIEW -> updateView(context, appWidgetId)
            RECEIVE_DATA -> {
                saveEntityConfiguration(
                    context,
                    intent.extras,
                    appWidgetId
                )
                onScreenOn(context)
            }
            Intent.ACTION_SCREEN_ON -> onScreenOn(context)
            Intent.ACTION_SCREEN_OFF -> onScreenOff()
        }
    }

    private fun onScreenOn(context: Context) {
        mainScope = CoroutineScope(Dispatchers.Main + Job())
        if (entityUpdates == null) {
            mainScope.launch {
                if (!integrationUseCase.isRegistered()) {
                    return@launch
                }
                updateAllWidgets(context)
                entityUpdates = integrationUseCase.getEntityUpdates()
                entityUpdates!!.collect {
                    updateAllWidgets(context)
                }
            }
        }
    }

    private fun onScreenOff() {
        mainScope.cancel()
        entityUpdates = null
    }

    private fun updateAllWidgets(
        context: Context
    ) {
        getAllWidgetIds(context).forEach {
            updateView(context, it)
        }
    }

    private fun updateView(
        context: Context,
        appWidgetId: Int,
        appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
    ) {
        mainScope.launch {
            val views = getWidgetRemoteViews(context, appWidgetId)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    abstract suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int): RemoteViews
    abstract fun getAllWidgetIds(context: Context): List<Int>
    abstract fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int)
}
