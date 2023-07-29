package io.homeassistant.companion.android

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {
    private var car: Car? = null
    private var carRestrictionManager: CarUxRestrictionsManager? = null

    override fun onResume() {
        super.onResume()
        registerListener()
    }

    override fun onStop() {
        super.onStop()
        carRestrictionManager?.unregisterListener()
        car?.disconnect()
        car = null
    }

    private fun registerListener() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            car = Car.createCar(this)
            carRestrictionManager =
                car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            val listener =
                CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
                    if (restrictions.isRequiresDistractionOptimization) {
                        startCarAppActivity()
                    }
                }
            carRestrictionManager?.registerListener(listener)
        }
    }

    private fun startCarAppActivity() {
        startActivity(
            Intent(
                this,
                Class.forName("androidx.car.app.activity.CarAppActivity")
            ).putExtra("TRANSITION_LAUNCH", true).addFlags(FLAG_ACTIVITY_NEW_TASK)
        )
        overridePendingTransition(
            androidx.appcompat.R.anim.abc_slide_in_bottom,
            androidx.appcompat.R.anim.abc_slide_in_bottom
        )
    }
}
