package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.sensors.SensorManager
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking

class HealthConnectSensorManager : SensorManager {
    companion object {
        private const val TAG = "HealthConnectSM"

        fun getPermissionResultContract(context: Context): ActivityResultContract<Set<String>, Set<String>>? =
            PermissionController.createRequestPermissionResultContract(context.packageName)

        val activeCaloriesBurned = SensorManager.BasicSensor(
            id = "health_connect_active_calories_burned",
            type = "sensor",
            commonR.string.basic_sensor_name_active_calories_burned,
            commonR.string.sensor_description_active_calories_burned,
            "mdi:fire",
            "energy",
            unitOfMeasurement = "kcal",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val totalCaloriesBurned = SensorManager.BasicSensor(
            id = "health_connect_total_calories_burned",
            type = "sensor",
            commonR.string.basic_sensor_name_total_calories_burned,
            commonR.string.sensor_description_total_calories_burned,
            "mdi:fire",
            "energy",
            unitOfMeasurement = "kcal",
            stateClass = SensorManager.STATE_CLASS_TOTAL_INCREASING,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        val weight = SensorManager.BasicSensor(
            id = "health_connect_weight",
            type = "sensor",
            commonR.string.basic_sensor_name_weight,
            commonR.string.sensor_description_weight,
            "mdi:scale-bathroom",
            unitOfMeasurement = "g",
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC,
            deviceClass = "weight"
        )
    }

    override val name: Int
        get() = commonR.string.sensor_name_health_connect

    override fun requiredPermissions(sensorId: String): Array<String> {
        return when {
            (sensorId == activeCaloriesBurned.id) -> arrayOf(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
            (sensorId == totalCaloriesBurned.id) -> arrayOf(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
            (sensorId == weight.id) -> arrayOf(HealthPermission.getReadPermission(WeightRecord::class))
            else -> arrayOf()
        }
    }

    override fun requestSensorUpdate(context: Context) {
        if (isEnabled(context, weight)) {
            updateWeightSensor(context)
        }
        if (isEnabled(context, activeCaloriesBurned)) {
            updateActiveCaloriesBurnedSensor(context)
        }
        if (isEnabled(context, totalCaloriesBurned)) {
            updateTotalCaloriesBurnedSensor(context)
        }
    }

    private fun updateTotalCaloriesBurnedSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val totalCaloriesBurnedRequest = runBlocking {
            healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT),
                        LocalDateTime.of(LocalDate.now(), LocalTime.now())
                    )
                )
            )
        }
        totalCaloriesBurnedRequest[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.let { energy ->
            onSensorUpdated(
                context,
                totalCaloriesBurned,
                BigDecimal(energy.inKilocalories).setScale(2, RoundingMode.HALF_EVEN),
                totalCaloriesBurned.statelessIcon,
                attributes = mapOf(
                    "endTime" to LocalDateTime.of(LocalDate.now(), LocalTime.now()).toInstant(ZoneOffset.UTC),
                    "sources" to totalCaloriesBurnedRequest.dataOrigins.map { it.packageName }
                )
            )
        }
    }

    private fun updateWeightSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val weightRequest = ReadRecordsRequest(
            recordType = WeightRecord::class,
            timeRangeFilter = TimeRangeFilter.between(
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now()
            ),
            ascendingOrder = false,
            pageSize = 1
        )
        val response = runBlocking { healthConnectClient.readRecords(weightRequest) }
        if (response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            weight,
            BigDecimal(response.records.last().weight.inGrams).setScale(2, RoundingMode.HALF_EVEN),
            weight.statelessIcon,
            attributes = mapOf(
                "date" to response.records.last().time,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    private fun updateActiveCaloriesBurnedSensor(context: Context) {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return
        val activeCaloriesBurnedRequest = ReadRecordsRequest(
            recordType = ActiveCaloriesBurnedRecord::class,
            timeRangeFilter = TimeRangeFilter.between(
                Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now()

            ),
            ascendingOrder = false,
            pageSize = 1
        )
        val response = runBlocking { healthConnectClient.readRecords(activeCaloriesBurnedRequest) }
        if (response.records.isEmpty()) {
            return
        }
        onSensorUpdated(
            context,
            activeCaloriesBurned,
            BigDecimal(response.records.last().energy.inKilocalories).setScale(2, RoundingMode.HALF_EVEN),
            activeCaloriesBurned.statelessIcon,
            attributes = mapOf(
                "endTime" to response.records.last().endTime,
                "source" to response.records.last().metadata.dataOrigin.packageName
            )
        )
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#health-connect-sensors"
    }

    override suspend fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return if (hasSensor(context)) {
            listOf(weight, activeCaloriesBurned, totalCaloriesBurned)
        } else {
            emptyList()
        }
    }

    override fun hasSensor(context: Context): Boolean {
        return SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    override fun checkPermission(context: Context, sensorId: String): Boolean {
        val healthConnectClient = getOrCreateHealthConnectClient(context) ?: return false
        val result = runBlocking {
            healthConnectClient.permissionController.getGrantedPermissions().containsAll(requiredPermissions(sensorId).toSet())
        }
        return result
    }

    private fun getOrCreateHealthConnectClient(context: Context): HealthConnectClient? {
        return try {
            HealthConnectClient.getOrCreate(context.applicationContext)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to create Health Connect client", e)
            null
        }
    }
}
