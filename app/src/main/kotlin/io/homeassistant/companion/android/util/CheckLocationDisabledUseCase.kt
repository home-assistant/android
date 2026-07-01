package io.homeassistant.companion.android.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.sensors.SensorManager
import io.homeassistant.companion.android.common.util.DisabledLocationHandler
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks whether location is disabled while features that depend on it are active,
 * and shows or removes a system notification accordingly.
 *
 * Features that depend on location include SSID-based home network detection and
 * any enabled sensor that requires location permissions.
 */
class CheckLocationDisabledUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverManager: ServerManager,
    private val managers: Set<@JvmSuppressWildcards SensorManager>,
) {

    /**
     * Evaluates whether location is required by any active feature and manages
     * the location-disabled notification.
     */
    suspend operator fun invoke() = withContext(Dispatchers.Default) {
        if (DisabledLocationHandler.isLocationEnabled(context)) {
            DisabledLocationHandler.removeLocationDisabledWarning(context)
            return@withContext
        }

        val settingsRequiringLocation = mutableListOf<String>()

        if (isSsidUsed()) {
            settingsRequiringLocation.add(context.getString(R.string.pref_connection_homenetwork))
        }

        for (manager in managers) {
            for (sensor in manager.getAvailableSensors()) {
                if (!manager.isEnabled(sensor)) continue

                val permissions = manager.requiredPermissions(sensor.id)
                val needsLocation =
                    DisabledLocationHandler.containsLocationPermission(
                        permissions,
                        fineLocation = true,
                    ) ||
                        DisabledLocationHandler.containsLocationPermission(
                            permissions,
                            fineLocation = false,
                        )

                if (needsLocation) {
                    settingsRequiringLocation.add(context.getString(sensor.name))
                }
            }
        }

        if (settingsRequiringLocation.isNotEmpty()) {
            DisabledLocationHandler.showLocationDisabledNotification(
                context,
                settingsRequiringLocation.toTypedArray(),
            )
        } else {
            DisabledLocationHandler.removeLocationDisabledWarning(context)
        }
    }

    private suspend fun isSsidUsed(): Boolean =
        serverManager.getServer()?.connection?.internalSsids?.isNotEmpty() == true
}
