package io.homeassistant.companion.android.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.onEntityPressedWithoutState
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class TileActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TileActionReceiver"
    }

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    override fun onReceive(context: Context?, intent: Intent?) {
        val entityId: String? = intent?.getStringExtra("entity_id")

        if (entityId != null) {
            runBlocking {
                if (wearPrefsRepository.getWearHapticFeedback() && context != null) hapticClick(context)

                try {
                    onEntityPressedWithoutState(
                        entityId = entityId,
                        integrationRepository = serverManager.integrationRepository()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot call tile service", e)
                }
            }
        }
    }
}
