package io.homeassistant.companion.android.vehicle

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.authentication.AuthenticationRepository
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import okhttp3.internal.toImmutableMap
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
class HaCarAppService : CarAppService() {

    companion object {
        private const val TAG = "HaCarAppService"
    }

    @Inject
    lateinit var integrationRepository: IntegrationRepository

    @Inject
    lateinit var authenticationRepository: AuthenticationRepository

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

            private val allEntities: Flow<Map<String, Entity<*>>> = flow {
                emit(emptyMap())
                val entities: MutableMap<String, Entity<*>>? =
                    integrationRepository.getEntities()
                        ?.associate { it.entityId to it }
                        ?.toMutableMap()
                if (entities != null) {
                    emit(entities.toImmutableMap())
                    integrationRepository.getEntityUpdates()?.collect { entity ->
                        entities[entity.entityId] = entity
                        emit(entities.toImmutableMap())
                    }
                } else {
                    Log.w(TAG, "No entities found?")
                }
            }.shareIn(
                lifecycleScope,
                SharingStarted.WhileSubscribed(10_000),
                1
            )

            override fun onCreateScreen(intent: Intent): Screen {
                return MainVehicleScreen(
                    carContext,
                    integrationRepository,
                    authenticationRepository,
                    allEntities
                )
            }
        }
    }
}
