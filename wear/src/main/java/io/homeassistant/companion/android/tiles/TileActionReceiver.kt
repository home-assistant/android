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
import io.homeassistant.companion.android.common.data.prefs.WearPrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.home.HomePresenterImpl
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
                        vibrator?.vibrate(200)
                    }
                }

                val domain = entityId.split(".")[0]
                val serviceName = when (domain) {
                    "button", "input_button" -> "press"
                    "lock" -> {
                        val lockEntity = try {
                            serverManager.integrationRepository().getEntity(entityId)
                        } catch (e: Exception) {
                            null
                        }
                        if (lockEntity?.state == "locked")
                            "unlock"
                        else
                            "lock"
                    }
                    in HomePresenterImpl.toggleDomains -> "toggle"
                    else -> "turn_on"
                }

                try {
                    serverManager.integrationRepository().callService(
                        domain,
                        serviceName,
                        hashMapOf("entity_id" to entityId)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot call tile service", e)
                }
            }
        }
    }
}
