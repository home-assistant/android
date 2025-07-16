package io.homeassistant.companion.android.complications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService.Companion.EXTRA_CONFIG_COMPLICATION_ID
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import dagger.hilt.android.AndroidEntryPoint
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
                UPDATE_COMPLICATION -> updateComplication(context, intent.getIntExtra(EXTRA_ID, -1))
                Intent.ACTION_SCREEN_ON -> onScreenOn(context)
            }
        } finally {
            result.finish()
        }
    }

    private fun updateComplication(context: Context, id: Int) {
        scope.launch {
            // Request an update for the complication that has just been toggled.
            ComplicationDataSourceUpdateRequester
                .create(
                    context = context,
                    complicationDataSourceComponent = ComponentName(context, EntityStateDataSourceService::class.java),
                )
                .requestUpdate(id)
        }
    }

    private fun updateAllComplications(context: Context) {
        ComplicationDataSourceUpdateRequester
            .create(
                context = context,
                complicationDataSourceComponent = ComponentName(context, EntityStateDataSourceService::class.java),
            )
            .requestUpdateAll()
    }

    private fun onScreenOn(context: Context) {
        scope.launch {
            if (!serverManager.isRegistered()) return@launch
            updateAllComplications(context)
        }
    }

    companion object {
        const val UPDATE_COMPLICATION = "update_complication"
        private const val EXTRA_ID = "complication_instance_id"

        /**
         * Returns a pending intent, suitable for use as a tap intent, that causes a complication to be
         * toggled and updated.
         */
        fun getComplicationToggleIntent(context: Context, complicationInstanceId: Int): PendingIntent {
            val intent = Intent(context, ComplicationReceiver::class.java).apply {
                action = UPDATE_COMPLICATION
                putExtra(EXTRA_ID, complicationInstanceId)
            }

            // Pass complicationId as the requestCode to ensure that different complications get
            // different intents.
            return PendingIntent.getBroadcast(
                context,
                complicationInstanceId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun getComplicationConfigureIntent(context: Context, complicationInstanceId: Int): PendingIntent {
            return PendingIntent.getActivity(
                context,
                complicationInstanceId,
                Intent(context, ComplicationConfigActivity::class.java).apply {
                    putExtra(EXTRA_CONFIG_COMPLICATION_ID, complicationInstanceId)
                },
                PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun getAssistIntent(context: Context): PendingIntent {
            return PendingIntent.getActivity(
                context,
                0,
                Intent(context, ConversationActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
