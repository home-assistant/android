package io.homeassistant.companion.android.sensors

import android.content.Context
import android.provider.Settings
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
import io.homeassistant.companion.android.common.util.STATE_UNAVAILABLE
import io.homeassistant.companion.android.common.util.STATE_UNKNOWN
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

@Singleton
class DynamicColorSensorManager @Inject constructor(
    @ApplicationContext override val applicationContext: Context,
    override val sensorRepository: SensorRepository,
    override val serverManager: ServerManager,
) : SensorManager {
    companion object {
        // See https://source.android.com/docs/core/display/dynamic-color#dynamic-13
        private val TONAL_PALETTE_STYLES = listOf(
            "EXPRESSIVE",
            "FRUIT_SALAD",
            "MONOCHROMATIC",
            "RAINBOW",
            "SPRITZ",
            "TONAL_SPOT",
            "VIBRANT",
        )
        private const val THEME_OVERLAY_JSON = "theme_customization_overlay_packages"
        private const val THEME_STYLE = "android.theme.customization.theme_style"

        @ProvidesSensor
        val accentColorSensor = SensorManager.BasicSensor(
            "accent_color",
            "sensor",
            commonR.string.sensor_name_accent_color_sensor,
            commonR.string.sensor_description_accent_color_sensor,
            "mdi:palette",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )

        @ProvidesSensor
        val tonalPaletteSensor = SensorManager.BasicSensor(
            "tonal_palette",
            "sensor",
            commonR.string.sensor_name_tonal_palette_sensor,
            commonR.string.sensor_description_tonal_palette_sensor,
            "mdi:palette",
            deviceClass = "enum",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#dynamic-color-sensor"
    }

    override val name: Int
        get() = commonR.string.sensor_name_dynamic_color

    override suspend fun getAvailableSensors(): List<SensorManager.BasicSensor> {
        return listOf(accentColorSensor, tonalPaletteSensor)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override suspend fun requestSensorUpdate() {
        updateAccentColor(applicationContext)
        updateTonalPalette(applicationContext)
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

    private suspend fun updateTonalPalette(applicationContext: Context) {
        if (!isEnabled(tonalPaletteSensor)) {
            return
        }

        suspend fun updateState(state: String) {
            onSensorUpdated(
                tonalPaletteSensor,
                state,
                tonalPaletteSensor.statelessIcon,
                mapOf("options" to TONAL_PALETTE_STYLES),
            )
        }

        val jsonString = try {
            Settings.Secure.getString(applicationContext.contentResolver, THEME_OVERLAY_JSON)
        } catch (ex: Exception) {
            Timber.w(ex, "Exception reading %s", THEME_OVERLAY_JSON)
            updateState(STATE_UNAVAILABLE)
            return
        }

        if (jsonString == null) {
            Timber.w("No value found for %s", THEME_OVERLAY_JSON)
            updateState(STATE_UNAVAILABLE)
            return
        }

        val jsonObject = try {
            JSONObject(jsonString)
        } catch (ex: JSONException) {
            Timber.w(ex, "Exception parsing JSON for %s", THEME_OVERLAY_JSON)
            updateState(STATE_UNKNOWN)
            return
        }

        val themeStyle = try {
            jsonObject.getString(THEME_STYLE)
        } catch (ex: JSONException) {
            Timber.w(ex, "Missing %s in JSON for %s", THEME_STYLE, THEME_OVERLAY_JSON)
            updateState(STATE_UNKNOWN)
            return
        }

        updateState(themeStyle)
    }
}
