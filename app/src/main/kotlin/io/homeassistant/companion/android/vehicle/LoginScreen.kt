package io.homeassistant.companion.android.vehicle

import android.content.Intent
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
            startActivity(
                Intent(
                    carContext,
                    LaunchActivity::class.java,
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            )
            if (isAutomotive) {
                finishCarApp()
            }
        }
    }
}
