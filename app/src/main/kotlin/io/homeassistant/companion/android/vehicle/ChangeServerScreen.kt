package io.homeassistant.companion.android.vehicle

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.util.vehicle.getHeaderBuilder
import kotlinx.coroutines.flow.StateFlow

class ChangeServerScreen(
    carContext: CarContext,
    private val serverManager: ServerManager,
    private val serverId: StateFlow<Int>,
) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        serverManager.defaultServers.forEach { server ->
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(server.friendlyName)
                    .setEnabled(server.id != serverId.value)
                    .setOnClickListener {
                        setResult(server.id)
                        finish()
                    }
                    .build(),
            )
        }

        return ListTemplate.Builder().apply {
            setHeader(carContext.getHeaderBuilder(commonR.string.aa_change_server).build())
            setLoading(false)
            setSingleList(listBuilder.build())
        }.build()
    }
}
