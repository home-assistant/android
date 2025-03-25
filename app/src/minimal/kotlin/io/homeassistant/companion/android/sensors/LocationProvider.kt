package io.homeassistant.companion.android.sensors

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.CancellationSignal
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import timber.log.Timber

@RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
suspend fun getLocation(context: Context): Location? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    if (locationManager == null) {
        Timber.w("Fail to get LocationManager from context")
        return null
    }

    val lastKnownLocation = locationManager.getLastKnownLocation(getProvider())
    val now = System.currentTimeMillis()
    // TODO maybe extract this into a constant since it also used in GeocodeSensorManager
    return if (lastKnownLocation == null || now - lastKnownLocation.time > 300000) {
        Timber.d("Last known location is null or too old, getting current location.")
        locationManager.getCurrentLocation(context)
    } else {
        Timber.d("Last known location is recent, using it.")
        lastKnownLocation
    }
}

private fun getProvider(): String {
    return if (SDK_INT >= Build.VERSION_CODES.S) {
        LocationManager.FUSED_PROVIDER
    } else {
        LocationManager.GPS_PROVIDER
    }
}

private suspend fun LocationManager.getCurrentLocation(context: Context): Location? {
    return suspendCoroutine {
        LocationManagerCompat.getCurrentLocation(this, getProvider(), CancellationSignal(), ContextCompat.getMainExecutor(context), it::resume)
    }
}
