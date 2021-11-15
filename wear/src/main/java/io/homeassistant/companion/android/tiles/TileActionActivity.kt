package io.homeassistant.companion.android.tiles

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.homeassistant.companion.android.common.dagger.GraphComponentAccessor
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.home.HomePresenterImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

class TileActionActivity : Activity() {

    @Inject
    lateinit var integrationUseCase: IntegrationRepository

    companion object {
        private const val TAG = "TileActionActivity"

        fun newInstance(context: Context): Intent {
            return Intent(context, TileActionActivity::class.java)
        }
    }

    private val mainScope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DaggerTilesComponent
            .builder()
            .appComponent((application as GraphComponentAccessor).appComponent)
            .build()
            .inject(this)

        val entityId: String? = intent.getStringExtra("entity_id")

        if (entityId != null) {
            mainScope.launch {
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

                finish()
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        // Cleans up the coroutine
        mainScope.cancel()
    }
}
