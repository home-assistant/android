package io.homeassistant.companion.android.sensors

import android.content.Context
import android.os.Environment
import android.os.StatFs
import io.homeassistant.companion.android.domain.integration.SensorRegistration
import java.io.File
import kotlin.math.roundToInt

class StorageSensorManager : SensorManager {
    companion object {

        private const val TAG = "StorageSensor"
        val path: File = Environment.getDataDirectory()
        private val stat = StatFs(path.path)
        var availableBlocks = stat.availableBlocksLong
        var blockSize = stat.blockSizeLong
        var totalBlocks = stat.blockCountLong
        var blockSizeSD = 0L
        var availableBlocksSD = 0L
        var totalBlocksSD = 0L

        private fun externalMemoryAvailable() : Boolean {
            return if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                Environment.isExternalStorageRemovable()
            } else {
                false
            }
        }
    }

    override val name: String
        get() = "Storage Sensors"

    override fun requiredPermissions(): Array<String> {
        return emptyArray()
    }

    override fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>> {
        return listOf(getStorageSensor(context))
    }

    private fun getStorageSensor(context: Context): SensorRegistration<Any> {

        var totalInternalStorage = getTotalInternalMemorySize()
        var freeInternalStorage = getAvailableInternalMemorySize()
        val percentageFreeInternalStorage = getPercentageInternal()
        val hasExternalStorage = externalMemoryAvailable()
        var totalExternalStorage = "No SD Card"
        var freeExternalStorage = "No SD Card"

        if (hasExternalStorage) {
            val pathSD = context.getExternalFilesDir(null)
            val statSD = StatFs(pathSD?.path)
            blockSizeSD = statSD.blockSizeLong
            availableBlocksSD = statSD.availableBlocksLong
            totalBlocksSD = statSD.blockCountLong
            totalExternalStorage = getTotalExternalMemorySize()
            freeExternalStorage = getAvailableExternalMemorySize()
        }

        val icon = "mdi:harddisk"

        return SensorRegistration(
            "storage_sensor",
            percentageFreeInternalStorage,
            "sensor",
            icon,
            mapOf(
                "Free internal storage" to freeInternalStorage,
                "Total internal storage" to totalInternalStorage,
                "Free external storage" to freeExternalStorage,
                "Total external storage" to totalExternalStorage
            ),
            "Storage Sensor",
            unitOfMeasurement = "%"
        )
    }

    private fun formatSize(size: Long) : String {
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
                    sizeLong /=1024
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

    private fun getTotalInternalMemorySize() : String {
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
