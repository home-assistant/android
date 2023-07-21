package io.homeassistant.companion.android.vehicle

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class DomainListScreen(
    carContext: CarContext,
    val serverManager: ServerManager,
    val integrationRepository: IntegrationRepository,
    private val serverId: StateFlow<Int>,
    private val allEntities: Flow<Map<String, Entity<*>>>,
    private val prefsRepository: PrefsRepository
) : Screen(carContext) {

    private val domains = mutableSetOf<String>()

    init {
        lifecycleScope.launch {
            allEntities.collect { entities ->
                val newDomains = entities.values
                    .map { it.domain }
                    .distinct()
                    .filter { it in MainVehicleScreen.SUPPORTED_DOMAINS }
                    .toSet()
                if (newDomains.size != domains.size || newDomains != domains) {
                    domains.clear()
                    domains.addAll(newDomains)
                    invalidate()
                }
            }
        }
    }
    override fun onGetTemplate(): Template {
        val screen = MainVehicleScreen(
            carContext,
            serverManager,
            serverId,
            allEntities,
            prefsRepository
        ) { }
        val domainList = screen.addDomainList(domains)

        return GridTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.all_entities))
            setHeaderAction(Action.BACK)
            if (screen.isAutomotive && !screen.iDrivingOptimized && BuildConfig.FLAVOR != "full") {
                setActionStrip(screen.nativeModeActionStrip())
            }
            if (domainList.build().items.isEmpty()) {
                setLoading(true)
            } else {
                setLoading(false)
                setSingleList(domainList.build())
            }
        }.build()
    }
}
