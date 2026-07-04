package io.homeassistant.companion.android.sensors

import android.content.Context
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.ProvidesSensor
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.sensors.SensorRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DynamicColorSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        @ProvidesSensor
        val accentColorSensor = SensorManager.BasicSensor(
            "accent_color",
            "sensor",
            commonR.string.sensor_name_accent_color_sensor,
            commonR.string.sensor_description_accent_color_sensor,
            "mdi:palette",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#dynamic-color-sensor"
    }
    override val name: Int
        get() = commonR.string.sensor_name_dynamic_color

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(accentColorSensor)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate() {
        updateAccentColor(applicationContext)
    }

    override fun hasSensor(): Boolean {
        return DynamicColors.isDynamicColorAvailable()
    }

    private suspend fun updateAccentColor(applicationContext: Context) {
        if (!isEnabled(accentColorSensor)) {
            return
        }

        val dynamicColorContext = DynamicColors.wrapContextIfAvailable(applicationContext)
        val attrsToResolve = intArrayOf(
            android.R.attr.colorAccent,
        )
        val test = dynamicColorContext.obtainStyledAttributes(attrsToResolve)
        val accent = test.getColor(0, 0)
        val accentHex = java.lang.String.format("#%06X", 0xFFFFFF and accent)
        test.recycle()

        onSensorUpdated(
            accentColorSensor,
            accentHex,
            accentColorSensor.statelessIcon,
            mapOf(
                "rgb_color" to listOf(accent.red, accent.green, accent.blue),
            ),
        )
    }
}
