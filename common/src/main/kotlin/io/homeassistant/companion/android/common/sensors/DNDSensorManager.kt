package io.homeassistant.companion.android.common.sensors

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.timoptr.mdiicons.Mdi
import io.github.timoptr.mdiicons.generated.MinusCircle
import io.github.timoptr.mdiicons.generated.MinusCircleOff
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import io.homeassistant.companion.android.common.util.isAutomotive
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@Singleton
class DNDSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
    private val homeAssistantMode: HomeAssistantMode,
) : SensorManager {
    companion object {
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
            // The mode is driven with the command_dnd notification command
            docsLink = "https://companion.home-assistant.io/docs/notifications/notification-commands#do-not-disturb",
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

    /** @return `true` if this device supports the Home Assistant mode. */
    fun isHomeAssistantModeSupported(): Boolean = homeAssistantMode.isSupported()

    /**
     * Turns the Home Assistant mode on or off, adding it first to turn it on. Android reports the change with
     * [NotificationManager.ACTION_AUTOMATIC_ZEN_RULE_STATUS_CHANGED], which updates the mode sensor. Requires Do Not
     * Disturb access.
     */
    suspend fun setHomeAssistantModeActive(active: Boolean): HomeAssistantMode.ChangeResult =
        homeAssistantMode.setActive(active)

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
     * Reports whether the mode is on, adding it while the sensor is enabled. When the user removes the mode from
     * Android, the sensor is disabled instead of adding the mode back.
     */
    private suspend fun updateHomeAssistantMode(): Unit = homeAssistantModeUpdateMutex.withLock {
        val active = homeAssistantMode.update(
            isWanted = { sensorRepository.get(homeAssistantModeSensor.id).any { it.enabled } },
            onRemovedByUser = {
                // The next sync with the servers tells them the sensor is disabled
                Timber.i("The Home Assistant mode was removed from Android, disabling its sensor")
                sensorRepository.setSensorEnabled(
                    sensorId = homeAssistantModeSensor.id,
                    serverIds = serverManager.servers().map { it.id },
                    enabled = false,
                )
            },
        ) ?: return@withLock
        onSensorUpdated(
            homeAssistantModeSensor,
            active,
            if (active) homeAssistantModeSensor.statelessIcon else Mdi.MinusCircleOff,
            mapOf(),
        )
    }
}
