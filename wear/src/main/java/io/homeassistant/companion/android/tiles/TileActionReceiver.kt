package io.homeassistant.companion.android.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.home.HomePresenterImpl
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class TileActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override fun onReceive(context: Context?, intent: Intent?) {
        val entityId: String? = intent?.getStringExtra("entity_id")

        if (entityId != null) {
            runBlocking {
                val domain = entityId.split(".")[0]
                val serviceName = when (domain) {
                    in HomePresenterImpl.toggleDomains -> "toggle"
                    "lock" -> {
                        val lockEntity = integrationUseCase.getEntity(entityId)
                        if (lockEntity.state == "locked")
                            "unlock"
                        else
                            "lock"
                    }
                    else -> "turn_on"
                }
                integrationUseCase.callService(
                    domain,
                    serviceName,
                    hashMapOf("entity_id" to entityId)
                )
            }
        }
    }
}
