package io.homeassistant.companion.android.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.homeassistant.companion.android.common.util.isAutomotive
import timber.log.Timber

abstract class BaseVehicleScreen(carContext: CarContext) : Screen(carContext) {
    private var car: Car? = null
    private var carRestrictionManager: CarUxRestrictionsManager? = null
    protected val isDrivingOptimized
        get() = try {
            car?.let {
                (
                    it.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    ).currentCarUxRestrictions.isRequiresDistractionOptimization
            } ?: false
        } catch (e: Exception) {
            Timber.e(e, "Error getting UX Restrictions")
            false
        }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {

            override fun onResume(owner: LifecycleOwner) {
                registerAutomotiveRestrictionListener()
            }

            override fun onPause(owner: LifecycleOwner) {
                carRestrictionManager?.unregisterListener()
                car?.disconnect()
                car = null
            }
        })
    }

    abstract fun onDrivingOptimizedChanged(newState: Boolean)

    private fun registerAutomotiveRestrictionListener() {
        if (carContext.isAutomotive()) {
            Timber.i("Register for Automotive Restrictions")
            car = Car.createCar(carContext)
            carRestrictionManager =
                car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            val listener =
                CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
                    onDrivingOptimizedChanged(restrictions.isRequiresDistractionOptimization)
                }
            carRestrictionManager?.registerListener(listener)
        }
    }
}
