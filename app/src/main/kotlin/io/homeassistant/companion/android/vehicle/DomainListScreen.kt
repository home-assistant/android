package io.homeassistant.companion.android.vehicle

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.util.vehicle.SUPPORTED_DOMAINS
import io.homeassistant.companion.android.util.vehicle.getDomainList
import io.homeassistant.companion.android.util.vehicle.getHeaderBuilder
import io.homeassistant.companion.android.util.vehicle.nativeModeAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class DomainListScreen(
    carContext: CarContext,
    val serverManager: ServerManager,
    private val serverId: StateFlow<Int>,
    private val allEntities: Flow<Map<String, Entity>>,
    private val prefsRepository: PrefsRepository,
    private val entityRegistry: List<EntityRegistryResponse>?,
) : BaseVehicleScreen(carContext) {

    private val domains = mutableSetOf<String>()
    private var domainsAdded = false

    override fun onDrivingOptimizedChanged(newState: Boolean) {
        invalidate()
    }

    init {
        lifecycleScope.launch {
            allEntities.collect { entities ->
                val newDomains = entities.values
                    .map { it.domain }
                    .distinct()
                    .filter { it in SUPPORTED_DOMAINS }
                    .toSet()
                val invalidate = newDomains.size != domains.size || newDomains != domains || !domainsAdded
                domains.clear()
                domains.addAll(newDomains)
                domainsAdded = true
                if (invalidate) invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val isAutomotive = carContext.isAutomotive()
        val domainList = getDomainList(
            domains,
            carContext,
            screenManager,
            serverManager,
            serverId,
            prefsRepository,
            allEntities,
            entityRegistry,
            lifecycleScope,
        )

        return GridTemplate.Builder().apply {
            val headerBuilder = carContext.getHeaderBuilder(R.string.all_entities)
            if (isAutomotive && !isDrivingOptimized && BuildConfig.FLAVOR != "full") {
                headerBuilder.addEndHeaderAction(nativeModeAction(carContext))
            }
            setHeader(headerBuilder.build())
            val domainBuild = domainList.build()
            if (!domainsAdded) {
                setLoading(true)
            } else {
                setLoading(false)
                setSingleList(domainBuild)
            }
        }.build()
    }
}
