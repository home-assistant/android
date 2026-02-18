package io.homeassistant.companion.android.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.onEntityPressedWithoutState
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.launchAsync
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber

@AndroidEntryPoint
class TileActionReceiver : BroadcastReceiver() {

    companion object {
        private val receiverScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    override fun onReceive(context: Context?, intent: Intent?) {
        val entityId: String? = intent?.getStringExtra("entity_id")

        if (entityId != null) {
            launchAsync(receiverScope) {
                if (wearPrefsRepository.getWearHapticFeedback() && context != null) hapticClick(context)

                try {
                    onEntityPressedWithoutState(
                        entityId = entityId,
                        integrationRepository = serverManager.integrationRepository(),
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Cannot call tile service")
                }
            }
        }
    }
}
