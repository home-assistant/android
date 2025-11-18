package io.homeassistant.companion.android.util

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import timber.log.Timber

class FlashlightHelper @Inject constructor(@ApplicationContext context: Context) {

    private val cameraManager by lazy { context.getSystemService<CameraManager>() }
    private val cameraId: String? by lazy {
        try {
            // Get the first camera with a flashlight (typically the back camera)
            cameraManager?.cameraIdList?.firstOrNull { id ->
                val cameraCharacteristics = cameraManager?.getCameraCharacteristics(id)
                cameraCharacteristics?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: CameraAccessException) {
            Timber.e(e, "Camera access exception")
            null
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    fun turnOnFlashlight() {
        try {
            cameraId?.let {
                cameraManager?.setTorchMode(it, true)
                Timber.i("Flashlight turned ON")
            }
        } catch (e: CameraAccessException) {
            Timber.e(e, "Failed to turn on flashlight")
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    fun turnOffFlashlight() {
        try {
            cameraId?.let {
                cameraManager?.setTorchMode(it, false)
                Timber.i("Flashlight turned OFF")
            }
        } catch (e: CameraAccessException) {
            Timber.e(e, "Failed to turn off flashlight")
        }
    }
}
