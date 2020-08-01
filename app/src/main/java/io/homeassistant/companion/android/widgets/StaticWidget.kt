package io.homeassistant.companion.android.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import io.homeassistant.companion.android.domain.widgets.WidgetUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StaticWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "StaticWidget"
        internal const val RECEIVE_DATA =
            "io.homeassistant.companion.android.widgets.StaticWidget.RECEIVE_DATA"

        internal const val EXTRA_ENTITY_ID = "EXTRA_ENTITY_ID"
        internal const val EXTRA_ATTRIBUTE_ID = "EXTRA_ATTRIBUTE_ID"
        internal const val EXTRA_LABEL = "EXTRA_LABEL"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    @Inject
    lateinit var widgetStorage: WidgetUseCase

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

    private suspend fun getWidgetRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_static).apply {
            val entityId: String? = widgetStorage.loadEntityId(appWidgetId)
            val attributeId: String? = widgetStorage.loadAttributeId(appWidgetId)
            val label: String? = widgetStorage.loadLabel(appWidgetId)
            setTextViewText(
                R.id.widgetText,
                resolveTextToShow(entityId, attributeId)
            )
            setTextViewText(
                R.id.widgetLabel,
                label ?: entityId
            )
        }

        return views
    }

    private suspend fun resolveTextToShow(
        entityId: String?,
        attributeId: String?
    ): CharSequence? {
        val entity = integrationUseCase.getEntities().find { e -> e.entityId.equals(entityId) }

        if (attributeId == null) return entity?.state ?: "N/A"

        val fetchedAttributes = entity?.attributes as Map<String, String>
        return entity.state.plus(fetchedAttributes.get(attributeId) ?: "")
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

        super.onReceive(context, intent)

        when (action) {
            RECEIVE_DATA -> saveEntityConfiguration(context, intent.extras, appWidgetId)
        }
    }

    private fun saveEntityConfiguration(context: Context, extras: Bundle?, appWidgetId: Int) {
        if (extras == null) return

        val entitySelection: String? = extras.getString(EXTRA_ENTITY_ID)
        val attributeSelection: String? = extras.getString(EXTRA_ATTRIBUTE_ID)
        val labelSelection: String? = extras.getString(EXTRA_LABEL)

        if (entitySelection == null) {
            Log.e(TAG, "Did not receive complete service call data")
            return
        }

        mainScope.launch {
            Log.d(
                TAG, "Saving service call config data:" + System.lineSeparator() +
                "entity id: " + entitySelection + System.lineSeparator() +
                "attribute: " + attributeSelection ?: "N/A"
            )

            widgetStorage.saveStaticEntityData(
                appWidgetId,
                entitySelection,
                attributeSelection
            )
            widgetStorage.saveLabel(appWidgetId, labelSelection)

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
