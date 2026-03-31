package io.homeassistant.companion.android.location

import android.Manifest
import android.content.Context
import android.location.Location
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import timber.log.Timber

@RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
suspend fun getLastLocation(context: Context): Location? {
    return try {
        LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
    } catch (e: Exception) {
        Timber.e(e, "Failed to get fused location provider client")
        null
    }
}
