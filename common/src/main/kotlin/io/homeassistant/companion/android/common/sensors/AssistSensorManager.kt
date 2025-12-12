package io.homeassistant.companion.android.common.sensors

import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.common.R
/**
 * Manages the Assist sensor that reports the current state of assist.
 *
 * Assist can have four states:
 * SPEAKING - Assist is speaking
 * LISTENING - Assist is listening
 * IDLE - Assist is opened but not doing anything
 * CLOSED - Assist is blocked
*/
class AssistSensorManager : SensorManager {

    enum class AssistState(val value: String) {
        SPEAKING("speaking"),
        LISTENING("listening"),
        IDLE("idle"),
        CLOSED("closed"),
    }

    companion object {
        const val ASSIST_STATE_CHANGED = "io.homeassistant.companion.android.assist.STATE_UPDATE"
        private const val STATE = "STATE"

        val assistSensor = SensorManager.BasicSensor(
            id = "assist_sensor",
            type = "sensor",
            name = R.string.sensor_name_assist,
            descriptionId = R.string.sensor_description_assist,
            statelessIcon = "mdi:robot",
        )

        fun updateState(context: Context, state: AssistState) {
            val stateString = state.value
            val intent = Intent(ASSIST_STATE_CHANGED)
            intent.putExtra(STATE, stateString)
            context.sendBroadcast(intent)
        }
    }

    override val name: Int
        get() = R.string.sensor_name_assist
    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(assistSensor)
    }

    override fun requiredPermissions(context: Context, sensorId: String): Array<String> {
        return emptyArray()
    }

    private suspend fun updateAssistSensor(context: Context, state: String) {
        if (!isEnabled(context, assistSensor)) {
            return
        }

        onSensorUpdated(
            context = context,
            basicSensor = assistSensor,
            state = state,
            attributes = mapOf(
                "options" to AssistState.entries.map(AssistState::value),
            ),
            mdiIcon = "mdi:robot",
        )
    }

    override suspend fun requestSensorUpdate(context: Context, intent: Intent?) {
        val state = intent?.getStringExtra(STATE) ?: return
        updateAssistSensor(context, state)
    }

    override suspend fun requestSensorUpdate(context: Context) {
        updateAssistSensor(context, AssistState.IDLE.value)
    }
}
