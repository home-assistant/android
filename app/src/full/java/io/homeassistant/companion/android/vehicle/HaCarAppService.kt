package io.homeassistant.companion.android.vehicle

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
class HaCarAppService : CarAppService() {

    @Inject
    lateinit var integrationRepository: IntegrationRepository

    override fun createHostValidator(): HostValidator {
        return if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(R.array.hosts_allowlist)
                .build()
        }
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return object : Session() {
            override fun onCreateScreen(intent: Intent): Screen {
                return MainVehicleScreen(carContext, integrationRepository)
            }
        }
    }
}
