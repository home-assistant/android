package io.homeassistant.companion.android.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.home.HomePresenterImpl
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class TileActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override fun onReceive(context: Context?, intent: Intent?) {
        val entityId: String? = intent?.getStringExtra("entity_id")

        if (entityId != null) {
            runBlocking {
                if (integrationUseCase.getWearHapticFeedback()) {
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
                        val lockEntity = integrationUseCase.getEntity(entityId)
                        if (lockEntity?.state == "locked")
                            "unlock"
                        else
                            "lock"
                    }
                    in HomePresenterImpl.toggleDomains -> "toggle"
                    else -> "turn_on"
                }

                integrationUseCase.callService(
                    domain,
                    serviceName,
                    hashMapOf("entity_id" to entityId)
                )
            }
        }
    }
}
