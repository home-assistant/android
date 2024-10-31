package io.homeassistant.companion.android.util

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class FlashlightHelper @Inject constructor(
    @ApplicationContext context: Context
) {

    private val cameraManager by lazy { context.getSystemService<CameraManager>() }
    private val cameraId: String? by lazy {
        try {
            // Get the first camera with a flashlight (typically the back camera)
            cameraManager?.cameraIdList?.firstOrNull { id ->
                val cameraCharacteristics = cameraManager?.getCameraCharacteristics(id)
                cameraCharacteristics?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera access exception: ${e.message}")
            null
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    fun turnOnFlashlight() {
        try {
            cameraId?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cameraManager?.setTorchMode(it, true)
                }
                Log.i(TAG, "Flashlight turned ON")
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to turn on flashlight: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    fun turnOffFlashlight() {
        try {
            cameraId?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    cameraManager?.setTorchMode(it, false)
                }
                Log.i(TAG, "Flashlight turned OFF")
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to turn off flashlight: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FlashlightHelper"
    }
}
