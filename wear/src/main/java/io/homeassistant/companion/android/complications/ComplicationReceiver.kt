package io.homeassistant.companion.android.complications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ComplicationReceiver : BroadcastReceiver() {
    private var entityUpdates: Flow<Entity<*>>? = null

    @Inject
    lateinit var integrationUseCase: IntegrationRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()

        try {
            when (intent.action.toString()) {
                UPDATE_COMPLICATION -> updateComplication(context, intent.getIntExtra(EXTRA_ID, -1))
                Intent.ACTION_SCREEN_ON -> onScreenOn(context)
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
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
                    complicationDataSourceComponent = ComponentName(context, "io.homeassistant.companion.android.complications.EntityStateDataSourceService")
                )
                .requestUpdate(id)
        }
    }

    private fun updateAllComplications(context: Context) {
        ComplicationDataSourceUpdateRequester
            .create(
                context = context,
                complicationDataSourceComponent = ComponentName(context, "io.homeassistant.companion.android.complications.EntityStateDataSourceService")
            )
            .requestUpdateAll()
    }

    private fun onScreenOn(context: Context) {
        mainScope = CoroutineScope(Dispatchers.Main + Job())
        if (entityUpdates == null) {
            mainScope.launch {
                if (!integrationUseCase.isRegistered()) {
                    return@launch
                }
                updateAllComplications(context)
                if (getAllComplicationIds(context).isNotEmpty()) {
                    Log.d(TAG, "Starting entity update listener")
                    entityUpdates = integrationUseCase.getEntityUpdates()
                    entityUpdates?.collect {
                        onEntityStateChanged(context, it)
                    }
                }
            }
        }
    }

    private fun onScreenOff() {
        mainScope.cancel()
        entityUpdates = null
    }

    private suspend fun getAllComplicationIds(context: Context): List<Int> {
        return AppDatabase.getInstance(context).entityStateComplicationsDao().getAll().map { it.id }
    }

    private suspend fun onEntityStateChanged(context: Context, entity: Entity<*>) {
        Log.d(TAG, "Entity state changed for ${entity.entityId}")
        AppDatabase.getInstance(context).entityStateComplicationsDao().getAll().forEach {
            Log.d(TAG, it.entityId)
            if (it.entityId == entity.entityId) {
                updateComplication(context, it.id)
            }
        }
    }

    companion object {
        private const val TAG = "ComplicationReceiver"

        const val UPDATE_COMPLICATION = "update_complication"
        private const val EXTRA_ID = "complication_instance_id"

        /**
         * Returns a pending intent, suitable for use as a tap intent, that causes a complication to be
         * toggled and updated.
         */
        fun getComplicationToggleIntent(
            context: Context,
            complicationInstanceId: Int
        ): PendingIntent {
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
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
