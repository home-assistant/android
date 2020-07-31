package io.homeassistant.companion.android.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.domain.integration.IntegrationUseCase
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

abstract class LocationBroadcastReceiverBase : BroadcastReceiver() {

    companion object {
        const val MINIMUM_ACCURACY = 200

        const val ACTION_REQUEST_LOCATION_UPDATES =
            "io.homeassistant.companion.android.background.REQUEST_UPDATES"
        const val ACTION_REQUEST_ACCURATE_LOCATION_UPDATE =
            "io.homeassistant.companion.android.background.REQUEST_ACCURATE_UPDATE"
        const val ACTION_PROCESS_LOCATION =
            "io.homeassistant.companion.android.background.PROCESS_UPDATES"
        const val ACTION_PROCESS_GEO =
            "io.homeassistant.companion.android.background.PROCESS_GEOFENCE"

        internal const val TAG = "LocBroadcastReceiver"
    }

    @Inject
    lateinit var integrationUseCase: IntegrationUseCase

    internal val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onReceive(context: Context, intent: Intent) {
        ensureInjected(context)

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_REQUEST_LOCATION_UPDATES -> setupLocationTracking(context)
            ACTION_PROCESS_LOCATION -> handleLocationUpdate(intent)
            ACTION_PROCESS_GEO -> handleGeoUpdate(context, intent)
            ACTION_REQUEST_ACCURATE_LOCATION_UPDATE -> requestSingleAccurateLocation(context)
            else -> Log.w(TAG, "Unknown intent action: ${intent.action}!")
        }
    }

    private fun ensureInjected(context: Context) {
        if (context.applicationContext is GraphComponentAccessor) {
            DaggerReceiverComponent.builder()
                .appComponent((context.applicationContext as GraphComponentAccessor).appComponent)
                .build()
                .inject(this)
        } else {
            throw Exception("Application Context passed is not of our application!")
        }
    }

    internal abstract fun setupLocationTracking(context: Context)
    internal abstract fun handleLocationUpdate(intent: Intent)
    internal abstract fun handleGeoUpdate(context: Context, intent: Intent)
    internal abstract fun requestSingleAccurateLocation(context: Context)
}
