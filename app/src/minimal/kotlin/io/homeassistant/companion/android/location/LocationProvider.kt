package io.homeassistant.companion.android.location

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
import io.homeassistant.companion.android.common.util.instant
import io.homeassistant.companion.android.sensors.GeocodeSensorManager.Companion.LOCATION_OUTDATED_THRESHOLD
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import timber.log.Timber

private val provider: String = if (SDK_INT >= Build.VERSION_CODES.S) {
    LocationManager.FUSED_PROVIDER
} else {
    LocationManager.GPS_PROVIDER
}

@OptIn(ExperimentalTime::class)
@RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
suspend fun getLastLocation(context: Context): Location? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    if (locationManager == null) {
        Timber.w("Couldn't get LocationManager from context")
        return null
    }

    val lastKnownLocation = locationManager.getLastKnownLocation(provider)
    /**
     * If the last known location is null or too old, get the current location.
     *
     * Note: In systems where no application or service actively requests location updates,
     * the last known location may never gets updated.
     */
    return if (lastKnownLocation == null ||
        Clock.System.now() - lastKnownLocation.instant() > LOCATION_OUTDATED_THRESHOLD
    ) {
        Timber.d("Last known location is null or too old, getting current location.")
        locationManager.getCurrentLocation(context)
    } else {
        lastKnownLocation
    }
}

private suspend fun LocationManager.getCurrentLocation(context: Context): Location? {
    return suspendCoroutine {
        LocationManagerCompat.getCurrentLocation(
            this,
            provider,
            CancellationSignal(),
            ContextCompat.getMainExecutor(context),
            it::resume,
        )
    }
}
