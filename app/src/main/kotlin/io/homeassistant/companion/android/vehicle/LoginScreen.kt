package io.homeassistant.companion.android.vehicle

import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.view.Display
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.launch.LaunchActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoginScreen(context: CarContext, val serverManager: ServerManager) : Screen(context) {
    private var isLoggedIn: Boolean? = null

    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                isLoggedIn = serverManager.isRegistered() &&
                    serverManager.authenticationRepository()
                        .getSessionState() == SessionState.CONNECTED
                invalidate()
                while (isLoggedIn != true) {
                    delay(1000)
                    isLoggedIn = serverManager.isRegistered() &&
                        serverManager.authenticationRepository()
                            .getSessionState() == SessionState.CONNECTED
                }
                screenManager.pop()
            }
        }
    }

    override fun onGetTemplate(): Template {
        return MessageTemplate.Builder(carContext.getString(R.string.welcome_hass))
            .setIcon(CarIcon.APP_ICON)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(if (isAutomotive) R.string.login else R.string.login_on_phone))
                    .setOnClickListener(
                        ParkedOnlyOnClickListener.create {
                            startNativeActivity()
                        },
                    )
                    .setFlags(Action.FLAG_PRIMARY)
                    .build(),
            )
            .build()
    }

    private val isAutomotive get() = carContext.isAutomotive()

    private fun startNativeActivity() {
        with(carContext) {
            // The app must indicate the default display to be used to avoid a SecurityException on newer
            // Android versions. See: https://developer.android.com/training/cars/platforms/releases#android-14
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                    .toBundle()
            } else {
                null
            }
            startActivity(
                Intent(
                    carContext,
                    LaunchActivity::class.java,
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                options
            )
            if (isAutomotive) {
                finishCarApp()
            }
        }
    }
}
