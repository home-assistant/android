package io.homeassistant.companion.android.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.home.HomePresenterImpl
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class TileActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    override fun onReceive(context: Context?, intent: Intent?) {
        DaggerTilesComponent
            .builder()
            .appComponent((context?.applicationContext as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        val entityId: String? = intent?.getStringExtra("entity_id")

        if (entityId != null) {
            runBlocking {
                if (entityId.split(".")[0] in HomePresenterImpl.toggleDomains) {
                    integrationUseCase.callService(
                        entityId.split(".")[0],
                        "toggle",
                        hashMapOf("entity_id" to entityId)
                    )
                } else {
                    integrationUseCase.callService(
                        entityId.split(".")[0],
                        "turn_on",
                        hashMapOf("entity_id" to entityId)
                    )
                }
            }
        }
    }
}
