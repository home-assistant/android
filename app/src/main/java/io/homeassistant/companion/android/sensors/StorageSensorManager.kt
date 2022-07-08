package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import io.homeassistant.companion.android.common.sensors.SensorManager
import java.io.File
import kotlin.math.roundToInt
import io.homeassistant.companion.android.common.R as commonR

class StorageSensorManager : SensorManager {
    companion object {

        private const val TAG = "StorageSensor"
        private val storageSensor = SensorManager.BasicSensor(
            "storage_sensor",
            "sensor",
            commonR.string.basic_sensor_name_internal_storage,
            commonR.string.sensor_description_internal_storage,
            "mdi:harddisk",
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        private val externalStorage = SensorManager.BasicSensor(
            "external_storage",
            "sensor",
            commonR.string.basic_sensor_name_external_storage,
            commonR.string.sensor_description_external_storage,
            "mdi:micro-sd",
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )

        private fun getExternalStoragePathIfAvailable(context: Context): File? {
            val pathsSD = context.getExternalFilesDirs(null)
            var removable: Boolean
            var externalPath: File? = null
            Log.d(TAG, "PATHS SD ${pathsSD.size}")
            for (item in pathsSD) {
                if (item != null) {
                    Log.d(
                        TAG,
                        "PATH $item is mounted ${Environment.getExternalStorageState(item) == Environment.MEDIA_MOUNTED} and removable is ${Environment.isExternalStorageRemovable(
                            item
                        )}"
                    )
                    if (Environment.getExternalStorageState(item) == Environment.MEDIA_MOUNTED) {
                        removable = Environment.isExternalStorageRemovable(item)
                        if (removable) {
                            externalPath = item
                        }
                    }
                }
            }
            return externalPath
        }
    }

    override fun docsLink(): String {
        return "https://companion.home-assistant.io/docs/core/sensors#storage-sensor"
    }
    override val enabledByDefault: Boolean
        get() = false
    override val name: Int
        get() = commonR.string.sensor_name_storage
    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(storageSensor, externalStorage)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateInternalStorageSensor(context)
        updateExternalStorageSensor(context)
    }

    private fun updateInternalStorageSensor(context: Context) {
        if (!isEnabled(context, storageSensor.id))
            return

        val path = Environment.getDataDirectory()
        val internalStorageStats = getStorageStats(path)

        onSensorUpdated(
            context,
            storageSensor,
            internalStorageStats.percentage,
            storageSensor.statelessIcon,
            mapOf(
                "Free internal storage" to internalStorageStats.freeBytes,
                "Total internal storage" to internalStorageStats.totalBytes
            )
        )
    }

    private fun updateExternalStorageSensor(context: Context) {
        if (!isEnabled(context, externalStorage.id))
            return

        val externalStoragePath = getExternalStoragePathIfAvailable(context)
        val externalStorageStats = externalStoragePath?.let {
            getStorageStats(it)
        }

        onSensorUpdated(
            context,
            externalStorage,
            externalStorageStats?.percentage ?: 0,
            externalStorage.statelessIcon,
            mapOf(
                "free_external_storage" to (externalStorageStats?.freeBytes ?: "No SD Card"),
                "total_external_storage" to (externalStorageStats?.totalBytes ?: "No SD Card")
            )
        )
    }

    private fun formatSize(size: Long): String {
        var suffix = ""

        var sizeLong = size
        if (sizeLong >= 1024) {
            suffix = "KB"
            sizeLong /= 1024
            if (sizeLong >= 1024) {
                suffix = "MB"
                sizeLong /= 1024
                if (sizeLong >= 1024) {
                    suffix = "GB"
                    sizeLong /= 1024
                }
            }
        }

        val sizeWithThousandsSeparator = String.format("%,d", sizeLong)
        return "$sizeWithThousandsSeparator$suffix"
    }

    private data class StorageStats(
        val totalBytes: String,
        val freeBytes: String,
        val percentage: Int
    )

    private fun getStorageStats(path: File) = with(StatFs(path.path)) {
        StorageStats(
            totalBytes = formatSize(blockCountLong * blockSizeLong),
            freeBytes = formatSize(availableBlocksLong * blockSizeLong),
            percentage = ((availableBlocksLong.toDouble() / blockCountLong.toDouble()) * 100).roundToInt()
        )
    }
}
