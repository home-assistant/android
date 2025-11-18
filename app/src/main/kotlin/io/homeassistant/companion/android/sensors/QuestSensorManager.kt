package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.util.QuestUtil

class QuestSensorManager : SensorManager {
    companion object {
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

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(
            headsetMounted,
        )
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun hasSensor(context: Context): Boolean {
        return QuestUtil.isQuest
    }

    override suspend fun requestSensorUpdate(context: Context) {
        val intent = ContextCompat.registerReceiver(
            context,
            null,
            IntentFilter("com.oculus.intent.action.MOUNT_STATE_CHANGED"),
            ContextCompat.RECEIVER_EXPORTED,
        )
        if (intent != null) {
            updateHeadsetMount(context, intent)
        }
    }

    private fun getHeadsetState(intent: Intent): Boolean {
        return intent.getBooleanExtra("state", false)
    }

    private suspend fun updateHeadsetMount(context: Context, intent: Intent) {
        if (!isEnabled(context, headsetMounted)) {
            return
        }

        val state: Boolean = getHeadsetState(intent)

        onSensorUpdated(
            context,
            headsetMounted,
            state,
            headsetMounted.statelessIcon,
            mapOf(),
        )
    }
}
