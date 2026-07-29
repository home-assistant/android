package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.ProvidesSensor
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import io.homeassistant.companion.android.util.QuestUtil
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        @ProvidesSensor
        val headsetMounted = SensorManager.BasicSensor(
            "in_use",
            "binary_sensor",
            commonR.string.basic_sensor_name_headset_mounted,
            commonR.string.sensor_description_headset_mounted,
            "mdi:virtual-reality",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/oculus-quest/"
    }

    override val name: Int
        get() = commonR.string.sensor_name_quest

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(
            headsetMounted,
        )
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun hasSensor(): Boolean {
        return QuestUtil.isQuest
    }

    override suspend fun requestSensorUpdate() {
        val intent = ContextCompat.registerReceiver(
            applicationContext,
            null,
            IntentFilter("com.oculus.intent.action.MOUNT_STATE_CHANGED"),
            ContextCompat.RECEIVER_EXPORTED,
        )
        if (intent != null) {
            updateHeadsetMount(applicationContext, intent)
        }
    }

    private fun getHeadsetState(intent: Intent): Boolean {
        return intent.getBooleanExtra("state", false)
    }

    private suspend fun updateHeadsetMount(applicationContext: Context, intent: Intent) {
        if (!isEnabled(headsetMounted)) {
            return
        }

        val state: Boolean = getHeadsetState(intent)

        onSensorUpdated(
            headsetMounted,
            state,
            headsetMounted.statelessIcon,
            mapOf(),
        )
    }
}
