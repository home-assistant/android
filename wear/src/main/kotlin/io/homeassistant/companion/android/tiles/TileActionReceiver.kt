package io.homeassistant.companion.android.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.onEntityPressedWithoutState
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class TileActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var wearPrefsRepository: WearPrefsRepository

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onReceive(context: Context?, intent: Intent?) {
        val entityId: String? = intent?.getStringExtra("entity_id")

        if (entityId != null) {
            coroutineScope.launch {
                try {
                    // Check haptic feedback setting and trigger haptic feedback
                    if (wearPrefsRepository.getWearHapticFeedback() && context != null) {
                        hapticClick(context)
                    }

                    // Call onEntityPressedWithoutState suspend function asynchronously
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