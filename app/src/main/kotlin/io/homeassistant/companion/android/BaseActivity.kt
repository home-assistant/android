package io.homeassistant.companion.android

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.util.PermissionRequestMediator
import io.homeassistant.companion.android.util.enableEdgeToEdgeCompat
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

@AndroidEntryPoint
open class BaseActivity : AppCompatActivity() {

    @Inject
    lateinit var permissionRequestMediator: PermissionRequestMediator

    private var car: Car? = null
    private var carRestrictionManager: CarUxRestrictionsManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdgeCompat()
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                permissionRequestMediator.eventFlow.collect { permissionToRequest ->
                    ActivityCompat.requestPermissions(
                        this@BaseActivity,
                        arrayOf(permissionToRequest),
                        permissionToRequest.hashCode().absoluteValue,
                    )
                }
            }
        }
    }

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
        if (isAutomotive()) {
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
                Class.forName("androidx.car.app.activity.CarAppActivity"),
            ).putExtra("TRANSITION_LAUNCH", true).addFlags(FLAG_ACTIVITY_NEW_TASK),
        )
        overridePendingTransition(
            androidx.appcompat.R.anim.abc_slide_in_bottom,
            androidx.appcompat.R.anim.abc_slide_in_bottom,
        )
    }
}
