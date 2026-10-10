package io.homeassistant.companion.android.vehicle

import androidx.activity.addCallback
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.vehicle.getHeaderBuilder

/**
 * Tells the user that the server cannot be reached. Back is disabled since the screen is
 * popped by [HaCarAppService] as soon as the server answers again.
 */
class ConnectionErrorScreen(context: CarContext) : Screen(context) {

    init {
        carContext.onBackPressedDispatcher.addCallback(this) {}
    }

    override fun onGetTemplate(): Template {
        val icon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_casita_no_connection),
        ).build()

        return MessageTemplate.Builder(carContext.getString(commonR.string.aa_connection_error_message))
            .setHeader(
                carContext.getHeaderBuilder(
                    title = commonR.string.aa_connection_error_title,
                    action = Action.APP_ICON,
                ).build(),
            )
            .setIcon(icon)
            .build()
    }
}
