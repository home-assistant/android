package io.homeassistant.companion.android.util.vehicle

import android.os.Build
import androidx.annotation.RequiresApi
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.vehicle.MainVehicleScreen

@RequiresApi(Build.VERSION_CODES.O)
fun isVehicleDomain(entity: Entity<*>): Boolean {
    return entity.domain in MainVehicleScreen.SUPPORTED_DOMAINS ||
        entity.domain in MainVehicleScreen.NOT_ACTIONABLE_DOMAINS ||
        (
            entity.domain in MainVehicleScreen.MAP_DOMAINS &&
                ((entity.attributes as? Map<*, *>)?.get("latitude") as? Double != null) &&
                ((entity.attributes as? Map<*, *>)?.get("longitude") as? Double != null)
            )
}
