package io.homeassistant.companion.android.widgets.button

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.util.MapAnySerializer
import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import io.homeassistant.companion.android.database.widget.ButtonWidgetDao
import io.homeassistant.companion.android.database.widget.ButtonWidgetEntity
import io.homeassistant.companion.android.widgets.BaseGlanceEntityWidgetReceiver
import io.homeassistant.companion.android.widgets.EntitiesPerServer
import io.homeassistant.companion.android.widgets.common.WidgetAuthenticationActivity
import java.util.regex.Pattern
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class ButtonWidget : BaseGlanceEntityWidgetReceiver<ButtonWidgetEntity, ButtonWidgetDao>() {
    override suspend fun getWidgetEntitiesByServer(context: Context): Map<Int, EntitiesPerServer> {
        return dao.getAll().associate { widget -> widget.id to EntitiesPerServer(widget.serverId, listOf()) }
    }

    override val glanceAppWidget: GlanceAppWidget = ButtonGlanceAppWidget()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        Timber.d("Broadcast received \nBroadcast action: $action \nAppWidgetId: $appWidgetId")
//        Timber.d(
//            "Broadcast received: " + System.lineSeparator() +
//                "Broadcast action: " + action + System.lineSeparator() +
//                "AppWidgetId: " + appWidgetId,
//        )

        super.onReceive(context, intent)
        when (action) {
            CALL_SERVICE_AUTH -> authThenCallConfiguredAction(context, appWidgetId)
            CALL_SERVICE -> widgetScope.launch { callConfiguredAction(context, appWidgetId) }
        }
    }


    private fun authThenCallConfiguredAction(context: Context, appWidgetId: Int) {
        Timber.d("Calling authentication, then configured action")

        val intent = Intent(context, WidgetAuthenticationActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }

    private suspend fun callConfiguredAction(context: Context, appWidgetId: Int) {
        Timber.d("Calling widget action")
        val widget = dao.get(appWidgetId)

        val domain = widget?.domain
        val action = widget?.service
        val actionDataJson = widget?.serviceData

        val manager = GlanceAppWidgetManager(context)
        val glanceId = manager.getGlanceIdBy(appWidgetId)

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
                updateAppWidgetState(context = context, glanceId = glanceId) {
                    it[booleanPreferencesKey(IS_LOADING_KEY)] = false
                    it[booleanPreferencesKey(ACTION_SENT_SUCCESSFUL_KEY)] = true
                }
            } catch (e: CancellationException) {
                updateAppWidgetState(context = context, glanceId = glanceId) {
                    it[booleanPreferencesKey(IS_LOADING_KEY)] = false
                    it[booleanPreferencesKey(ACTION_SENT_SUCCESSFUL_KEY)] = false
                }
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Could not send action call.")
                updateAppWidgetState(context = context, glanceId = glanceId) {
                    it[booleanPreferencesKey(IS_LOADING_KEY)] = false
                    it[booleanPreferencesKey(ACTION_SENT_SUCCESSFUL_KEY)] = false
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, commonR.string.action_failure, Toast.LENGTH_LONG).show()
                }
            }
        }
        delay(1.seconds)
        glanceAppWidget.update(context, glanceId)
    }

    companion object {

        const val IS_LOADING_KEY = "isLoading"
        const val ACTION_SENT_SUCCESSFUL_KEY = "wasSentSuccessfully"
        const val CALL_SERVICE =
            "io.homeassistant.companion.android.widgets.button.ButtonWidget.CALL_SERVICE"
        const val CALL_SERVICE_AUTH =
            "io.homeassistant.companion.android.widgets.button.ButtonWidget.CALL_SERVICE_AUTH"

        // Vector icon rendering resolution fallback (if we can't infer via AppWidgetManager for some reason)
        const val DEFAULT_MAX_ICON_SIZE = 256
        private var widgetScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }


}
