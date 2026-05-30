package io.homeassistant.companion.android.tiles.dashboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.util.launchAsync
import io.homeassistant.companion.android.tiles.hapticClick
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

/**
 * Handles Wear Dashboard tile action broadcasts.
 */
@AndroidEntryPoint
class WearDashboardActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var actionExecutor: WearDashboardActionExecutor

    @Inject
    lateinit var actionSerializer: WearDashboardActionSerializer

    @Inject
    lateinit var wearPrefsRepository: io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository

    private val receiverScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != WearDashboardActionSerializer.ACTION_BROADCAST) return

        val payload = intent.getStringExtra(WearDashboardActionSerializer.EXTRA_ACTION_JSON) ?: return
        val tileId = intent.getIntExtra(WearDashboardActionSerializer.EXTRA_TILE_ID, 0).takeIf { it != 0 }

        launchAsync(receiverScope) {
            if (wearPrefsRepository.getWearHapticFeedback()) {
                hapticClick(context)
            }

            try {
                val action = actionSerializer.deserializeAction(payload)
                if (actionExecutor.requiresConfirmation(action)) {
                    Timber.i("Skipping immediate execution for action requiring confirmation")
                    return@launchAsync
                }
                actionExecutor.execute(context, action, tileId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to execute wear dashboard tile action")
            }
        }
    }

    companion object {
        /**
         * Sends a dashboard action broadcast for execution.
         */
        fun sendAction(context: Context, actionPayload: String, tileId: Int) {
            Intent(WearDashboardActionSerializer.ACTION_BROADCAST).apply {
                setPackage(context.packageName)
                putExtra(WearDashboardActionSerializer.EXTRA_ACTION_JSON, actionPayload)
                putExtra(WearDashboardActionSerializer.EXTRA_TILE_ID, tileId)
            }.also { intent ->
                context.sendBroadcast(intent)
            }
        }
    }
}
