package io.homeassistant.companion.android.vehicle

import androidx.activity.addCallback
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.sizeDp
import com.mikepenz.iconics.utils.toAndroidIconCompat
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.util.vehicle.getHeaderBuilder

class NoConnectionScreen(context: CarContext) : Screen(context) {

    init {
        carContext.onBackPressedDispatcher.addCallback(this) {}
    }

    override fun onGetTemplate(): Template {
        val icon = CarIcon.Builder(
            IconicsDrawable(carContext, CommunityMaterial.Icon3.cmd_wifi_off).apply {
                sizeDp = 64
            }.toAndroidIconCompat(),
        )
            .setTint(CarColor.DEFAULT)
            .build()

        return MessageTemplate.Builder(carContext.getString(R.string.error_connection_failed_no_network))
            .setHeader(
                carContext.getHeaderBuilder(
                    title = R.string.aa_no_connection_title,
                    action = Action.APP_ICON,
                ).build(),
            )
            .setIcon(icon)
            .build()
    }
}
