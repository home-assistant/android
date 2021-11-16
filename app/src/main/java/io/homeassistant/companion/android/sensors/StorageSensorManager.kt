package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.sensors.SensorManager
import java.io.File
import kotlin.math.roundToInt

class StorageSensorManager : SensorManager {
    companion object {

        private const val TAG = "StorageSensor"
        private val storageSensor = SensorManager.BasicSensor(
            "storage_sensor",
            "sensor",
            R.string.basic_sensor_name_internal_storage,
            R.string.sensor_description_internal_storage,
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        private val externalStorage = SensorManager.BasicSensor(
            "external_storage",
            "sensor",
            R.string.basic_sensor_name_external_storage,
            R.string.sensor_description_external_storage,
            unitOfMeasurement = "%",
            stateClass = SensorManager.STATE_CLASS_MEASUREMENT,
            entityCategory = SensorManager.ENTITY_CATEGORY_DIAGNOSTIC
        )
        val path: File = Environment.getDataDirectory()
        private val stat = StatFs(path.path)
        var availableBlocks = stat.availableBlocksLong
        var blockSize = stat.blockSizeLong
        var totalBlocks = stat.blockCountLong
        var blockSizeSD = 0L
        var availableBlocksSD = 0L
        var totalBlocksSD = 0L
        private var externalPath: File? = null

        private fun externalMemoryAvailable(context: Context): File? {
            val pathsSD = context.getExternalFilesDirs(null)
            var removable: Boolean
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
        get() = R.string.sensor_name_storage
    override fun getAvailableSensors(context: Context): List<SensorManager.BasicSensor> {
        return listOf(storageSensor, externalStorage)
    }

    override fun requiredPermissions(sensorId: String): Array<String> {
        return emptyArray()
    }

    override fun requestSensorUpdate(
        context: Context
    ) {
        updateStorageSensor(context)
        updateExternalStorageSensor(context)
    }

    private fun updateStorageSensor(context: Context) {
        if (!isEnabled(context, storageSensor.id))
            return

        val totalInternalStorage = getTotalInternalMemorySize()
        val freeInternalStorage = getAvailableInternalMemorySize()
        val percentageFreeInternalStorage = getPercentageInternal()

        val icon = "mdi:harddisk"

        onSensorUpdated(
            context,
            storageSensor,
            percentageFreeInternalStorage,
            icon,
            mapOf(
                "Free internal storage" to freeInternalStorage,
                "Total internal storage" to totalInternalStorage
            )
        )
    }

    private fun updateExternalStorageSensor(context: Context) {
        if (!isEnabled(context, externalStorage.id))
            return

        externalPath = externalMemoryAvailable(context)
        var totalExternalStorage = "No SD Card"
        var freeExternalStorage = "No SD Card"
        var percentFreeExternal = 0

        if (externalPath != null) {
            val statSD = StatFs(externalPath.toString())
            blockSizeSD = statSD.blockSizeLong
            availableBlocksSD = statSD.availableBlocksLong
            totalBlocksSD = statSD.blockCountLong
            totalExternalStorage = getTotalExternalMemorySize()
            freeExternalStorage = getAvailableExternalMemorySize()
            percentFreeExternal = ((availableBlocksSD.toDouble() / totalBlocksSD.toDouble()) * 100).roundToInt()
        }

        val icon = "mdi:micro-sd"

        onSensorUpdated(
            context,
            externalStorage,
            percentFreeExternal,
            icon,
            mapOf(
                "free_external_storage" to freeExternalStorage,
                "total_external_storage" to totalExternalStorage
            )
        )
    }

    private fun formatSize(size: Long): String {
        var suffix = ""

        var sizeLong = size
        if (size >= 1024) {
            suffix = "KB"
            sizeLong /= 1024
            if (size >= 1024) {
                suffix = "MB"
                sizeLong /= 1024
                if (size >= 1024) {
                    suffix = "GB"
                    sizeLong /= 1024
                }
            }
        }

        val resultBuffer = StringBuilder(sizeLong.toString())

        var commaOffset = resultBuffer.length - 3
        while (commaOffset > 0) {
            resultBuffer.insert(commaOffset, ',')
            commaOffset -= 3
        }

        resultBuffer.append(suffix)
        return resultBuffer.toString()
    }

    private fun getTotalInternalMemorySize(): String {
        return formatSize(totalBlocks * blockSize)
    }

    private fun getAvailableInternalMemorySize(): String {
        return formatSize(availableBlocks * blockSize)
    }

    private fun getPercentageInternal(): Int {
        return ((availableBlocks.toDouble() / totalBlocks.toDouble()) * 100).roundToInt()
    }

    private fun getAvailableExternalMemorySize(): String {
        return formatSize(availableBlocksSD * blockSizeSD)
    }

    private fun getTotalExternalMemorySize(): String {
        return formatSize(totalBlocksSD * blockSizeSD)
    }
}
