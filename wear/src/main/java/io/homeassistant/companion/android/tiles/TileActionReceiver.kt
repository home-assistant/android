package io.homeassistant.companion.android.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.getSystemService
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
                if (wearPrefsRepository.getWearHapticFeedback()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vibratorManager = context?.getSystemService<VibratorManager>()
                        val vibrator = vibratorManager?.defaultVibrator
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    } else {
                        val vibrator = context?.getSystemService<Vibrator>()
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(200)
                    }
                }

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
