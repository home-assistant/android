package io.homeassistant.companion.android.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process.myPid
import android.os.Process.myUid
import io.homeassistant.companion.android.domain.integration.SensorRegistration

interface SensorManager {

    val name: String

    fun requiredPermissions(): Array<String>

    fun checkPermission(context: Context): Boolean {
        return requiredPermissions().all {
            context.checkPermission(it, myPid(), myUid()) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getSensorRegistrations(context: Context): List<SensorRegistration<Any>>
}
