package io.homeassistant.companion.android.complications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService.Companion.EXTRA_CONFIG_COMPLICATION_ID
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.onPressed
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.conversation.ConversationActivity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ComplicationReceiver : BroadcastReceiver() {
    @Inject
    lateinit var serverManager: ServerManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()

        try {
            when (intent.action) {
                TOGGLE_COMPLICATION -> toggleComplication(
                    context,
                    intent.getIntExtra(EXTRA_COMPLICATION_ID, -1),
                    intent.getStringExtra(EXTRA_ENTITY_ID)
                )
                Intent.ACTION_SCREEN_ON -> onScreenOn(context)
            }
        } finally {
            result.finish()
        }
    }

    private fun toggleComplication(context: Context, complicationId: Int, entityId: String?) {
        scope.launch {
            // Forward tap to entity if entityId is specified.
            if (entityId != null) {
                val entity = try {
                    serverManager.integrationRepository().getEntity(entityId)
                        ?: return@launch
                } catch (t: Throwable) {
                    Log.e(
                        EntityStateDataSourceService.TAG,
                        "Unable to get entity for $entityId: ${t.message}"
                    )
                    return@launch
                }

                // Press!
                entity.onPressed(serverManager.integrationRepository())
            }

            // Request an update for the complication that has just been toggled.
            ComplicationDataSourceUpdateRequester
                .create(
                    context = context,
                    complicationDataSourceComponent = ComponentName(context, EntityStateDataSourceService::class.java)
                )
                .requestUpdate(complicationId)
        }
    }

    private fun updateAllComplications(context: Context) {
        ComplicationDataSourceUpdateRequester
            .create(
                context = context,
                complicationDataSourceComponent = ComponentName(context, EntityStateDataSourceService::class.java)
            )
            .requestUpdateAll()
    }

    private fun onScreenOn(context: Context) {
        if (!serverManager.isRegistered()) return
        scope.launch {
            updateAllComplications(context)
        }
    }

    companion object {
        private const val TAG = "ComplicationReceiver"

        const val TOGGLE_COMPLICATION = "toggle_complication"
        private const val EXTRA_COMPLICATION_ID = "complication_instance_id"
        private const val EXTRA_ENTITY_ID = "entity_id"

        /**
         * Returns a pending intent, suitable for use as a tap intent, that causes a complication to be
         * toggled and updated.
         */
        fun getComplicationToggleIntent(
            context: Context,
            complicationInstanceId: Int,
            entityId: String?
        ): PendingIntent {
            val intent = Intent(context, ComplicationReceiver::class.java).apply {
                action = TOGGLE_COMPLICATION
                putExtra(EXTRA_COMPLICATION_ID, complicationInstanceId)
                putExtra(EXTRA_ENTITY_ID, entityId)
            }

            // Pass complicationId as the requestCode to ensure that different complications get
            // different intents.
            return PendingIntent.getBroadcast(
                context,
                complicationInstanceId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun getComplicationConfigureIntent(
            context: Context,
            complicationInstanceId: Int
        ): PendingIntent {
            return PendingIntent.getActivity(
                context,
                complicationInstanceId,
                Intent(context, ComplicationConfigActivity::class.java).apply {
                    putExtra(EXTRA_CONFIG_COMPLICATION_ID, complicationInstanceId)
                },
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun getAssistIntent(context: Context): PendingIntent {
            return PendingIntent.getActivity(
                context,
                0,
                Intent(context, ConversationActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
