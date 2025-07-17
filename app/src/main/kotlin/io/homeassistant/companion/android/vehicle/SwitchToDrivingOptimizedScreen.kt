package io.homeassistant.companion.android.vehicle

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import io.homeassistant.companion.android.common.R

class SwitchToDrivingOptimizedScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(carContext.getString(R.string.aa_driving_optimized_change))
            .setIcon(CarIcon.APP_ICON)
            .addAction(
                Action.Builder()
                    .setFlags(Action.FLAG_DEFAULT)
                    .setTitle(carContext.getString(R.string.continue_connect))
                    .setOnClickListener {
                        screenManager.pop()
                    }
                    .build(),
            ).build()
    }
}
