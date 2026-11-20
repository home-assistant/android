package io.homeassistant.companion.android.common.sensors

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.MdiIcon
import io.github.timoptr.mdiicons.generated.MinusCircle
import io.github.timoptr.mdiicons.generated.MinusCircleOff
import io.github.timoptr.mdiicons.generated.MinusCircleOffOutline
import io.github.timoptr.mdiicons.generated.MinusCircleOutline
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.modes.HomeAssistantMode
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.isAutomotive
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DNDSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
    private val homeAssistantMode: HomeAssistantMode,
) : SensorManager {
    companion object {
        /** Attribute of [homeAssistantModeSensor]: whether the mode is in Android's Modes. */
        internal const val ATTRIBUTE_HOME_ASSISTANT_MODE_ADDED = "added"

        /** Attribute of [homeAssistantModeSensor]: whether the user left the mode enabled in Android. */
        internal const val ATTRIBUTE_HOME_ASSISTANT_MODE_ENABLED = "enabled"

        @ProvidesSensor
        val dndSensor = SensorManager.BasicSensor(
            "dnd_sensor",
            "sensor",
            commonR.string.sensor_name_dnd,
            commonR.string.sensor_description_dnd_sensor,
            Mdi.MinusCircle,
            deviceClass = "enum",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )

        @ProvidesSensor
        val homeAssistantModeSensor = SensorManager.BasicSensor(
            id = "home_assistant_mode",
            type = "binary_sensor",
            name = commonR.string.basic_sensor_name_home_assistant_mode,
            descriptionId = commonR.string.sensor_description_home_assistant_mode,
            statelessIcon = Mdi.MinusCircle,
            docsLink = "https://companion.home-assistant.io/docs/core/sensors#home-assistant-mode-sensor",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            updateType = SensorManager.BasicSensor.UpdateType.INTENT,
        )
    }

    /**
     * Keeps reading the mode and storing what was read together, so an older read cannot overwrite a newer state:
     * workers and broadcasts refresh the mode concurrently.
     */
    private val homeAssistantModeUpdateMutex = Mutex()

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#do-not-disturb-sensor"
    }

    override val name: Int
        get() = commonR.string.sensor_name_dnd

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOfNotNull(dndSensor, homeAssistantModeSensor.takeIf { homeAssistantMode.isSupported() })
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return if (sensorId == homeAssistantModeSensor.id) {
            arrayOf(Manifest.permission.ACCESS_NOTIFICATION_POLICY)
        } else {
            emptyArray()
        }
    }

    override suspend fun checkPermission(sensorId: String): Boolean {
        // The manifest permission is always granted, what matters is the Do Not Disturb access given by the user
        return sensorId != homeAssistantModeSensor.id || homeAssistantMode.hasAccess()
    }

    override suspend fun requestSensorUpdate() {
        updateDNDState()
        updateHomeAssistantMode()
    }

    override fun hasSensor(): Boolean {
        return !applicationContext.isAutomotive()
    }

    private suspend fun updateDNDState() {
        if (!isEnabled(dndSensor)) {
            return
        }

        val notificationManager =
            applicationContext.getSystemService<NotificationManager>()

        val state = when (notificationManager?.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms_only"
            NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "total_silence"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority_only"
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> STATE_UNKNOWN
            else -> STATE_UNKNOWN
        }

        onSensorUpdated(
            dndSensor,
            state,
            if (state != "off") dndSensor.statelessIcon else Mdi.MinusCircleOff,
            mapOf(
                "options" to listOf("alarms_only", "off", "priority_only", "total_silence"),
            ),
        )
    }

    /**
     * Reports whether the Home Assistant mode is on, off while it is not added, with whether it is added and enabled in
     * Android as attributes: they tell why a command to turn it on or off did nothing.
     */
    private suspend fun updateHomeAssistantMode(): Unit = homeAssistantModeUpdateMutex.withLock {
        if (!homeAssistantMode.isSupported() || !isEnabled(homeAssistantModeSensor)) return@withLock
        val state = homeAssistantMode.getState() ?: return@withLock
        val active = state.active ?: return@withLock
        onSensorUpdated(
            homeAssistantModeSensor,
            active,
            homeAssistantModeIcon(state),
            mapOf(
                ATTRIBUTE_HOME_ASSISTANT_MODE_ADDED to state.added,
                ATTRIBUTE_HOME_ASSISTANT_MODE_ENABLED to state.enabled,
            ),
        )
    }

    /**
     * @return the icon of the mode sensor: a different one for on, off, disabled in Android and not added. A sensor is
     * only sent to Home Assistant when its state or icon changed, so the icon is also what gets a change to the
     * attributes alone sent, whatever else is being sent at that moment.
     */
    private fun homeAssistantModeIcon(state: HomeAssistantMode.State): MdiIcon = when {
        !state.added -> Mdi.MinusCircleOutline
        !state.enabled -> Mdi.MinusCircleOffOutline
        state.active == true -> homeAssistantModeSensor.statelessIcon
        else -> Mdi.MinusCircleOff
    }
}
