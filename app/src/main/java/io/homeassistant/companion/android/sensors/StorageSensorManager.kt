package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import java.io.File
import kotlin.math.roundToInt

class StorageSensorManager : SensorManager {
    companion object {

        private const val TAG = "StorageSensor"
        private val storageSensor = SensorManager.BasicSensor(
            "storage_sensor",
            "sensor",
            "Storage Sensor",
            unitOfMeasurement = "%"
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

    override val name: String
        get() = "Storage Sensors"
    override val availableSensors: List<SensorManager.BasicSensor>
        get() = listOf(storageSensor)

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun getSensorData(
        context: Context,
        sensorId: String
    ): SensorRegistration<Any> {
        return when (sensorId) {
            storageSensor.id -> getStorageSensor(context)
            else -> throw IllegalArgumentException("Unknown sensorId: $sensorId")
        }
    }

    private fun getStorageSensor(context: Context): SensorRegistration<Any> {

        var totalInternalStorage = getTotalInternalMemorySize()
        var freeInternalStorage = getAvailableInternalMemorySize()
        val percentageFreeInternalStorage = getPercentageInternal()
        externalPath = externalMemoryAvailable(context)
        var totalExternalStorage = "No SD Card"
        var freeExternalStorage = "No SD Card"

        if (externalPath != null) {
            val statSD = StatFs(externalPath.toString())
            blockSizeSD = statSD.blockSizeLong
            availableBlocksSD = statSD.availableBlocksLong
            totalBlocksSD = statSD.blockCountLong
            totalExternalStorage = getTotalExternalMemorySize()
            freeExternalStorage = getAvailableExternalMemorySize()
        }

        val icon = "mdi:harddisk"

        return storageSensor.toSensorRegistration(
            percentageFreeInternalStorage,
            icon,
            mapOf(
                "Free internal storage" to freeInternalStorage,
                "Total internal storage" to totalInternalStorage,
                "Free external storage" to freeExternalStorage,
                "Total external storage" to totalExternalStorage
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

        if (suffix != null) resultBuffer.append(suffix)
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
