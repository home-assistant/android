package io.homeassistant.companion.android.vehicle

import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.util.vehicle.getChangeServerGridItem
import io.homeassistant.companion.android.util.vehicle.getDomainList
import io.homeassistant.companion.android.util.vehicle.getNavigationGridItem
import io.homeassistant.companion.android.util.vehicle.nativeModeActionStrip
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.O)
class MainVehicleScreen(
    carContext: CarContext,
    val serverManager: ServerManager,
    private val serverId: StateFlow<Int>,
    private val allEntities: Flow<Map<String, Entity<*>>>,
    private val prefsRepository: PrefsRepository,
    private val onChangeServer: (Int) -> Unit
) : BaseVehicleScreen(carContext) {

    companion object {
        private const val TAG = "MainVehicleScreen"

        val SUPPORTED_DOMAINS_WITH_STRING = mapOf(
            "button" to commonR.string.buttons,
            "cover" to commonR.string.covers,
            "input_boolean" to commonR.string.input_booleans,
            "input_button" to commonR.string.input_buttons,
            "light" to commonR.string.lights,
            "lock" to commonR.string.locks,
            "scene" to commonR.string.scenes,
            "script" to commonR.string.scripts,
            "switch" to commonR.string.switches
        )
        val SUPPORTED_DOMAINS = SUPPORTED_DOMAINS_WITH_STRING.keys

        val MAP_DOMAINS = listOf(
            "device_tracker",
            "person",
            "sensor",
            "zone"
        )
    }

    private var favoritesEntities: List<Entity<*>> = listOf()
    private var favoritesList = emptyList<String>()
    private var isLoggedIn: Boolean? = null
    private val domains = mutableSetOf<String>()

    private val isAutomotive get() = carContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                favoritesList = prefsRepository.getAutoFavorites()
                isLoggedIn = serverManager.isRegistered() &&
                    serverManager.authenticationRepository()
                    .getSessionState() == SessionState.CONNECTED
                invalidate()
                while (isLoggedIn != true) {
                    delay(1000)
                    isLoggedIn = serverManager.isRegistered() &&
                        serverManager.authenticationRepository()
                        .getSessionState() == SessionState.CONNECTED
                    invalidate()
                }
                allEntities.collect { entities ->
                    val newDomains = entities.values
                        .map { it.domain }
                        .distinct()
                        .filter { it in SUPPORTED_DOMAINS }
                        .toSet()
                    var invalidate = newDomains.size != domains.size || newDomains != domains
                    domains.clear()
                    domains.addAll(newDomains)

                    val newFavorites = getFavoritesList(entities)
                    invalidate = invalidate || (newFavorites.size != favoritesEntities.size || newFavorites.toSet() != favoritesEntities.toSet())
                    favoritesEntities = newFavorites

                    if (invalidate) invalidate()
                }
            }
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                SensorReceiver.updateAllSensors(carContext)
            }
        }
    }

    override fun onDrivingOptimizedChanged(newState: Boolean) {
        invalidate()
    }

    override fun onGetTemplate(): Template {
        if (isLoggedIn != true) {
            return GridTemplate.Builder().apply {
                setTitle(carContext.getString(commonR.string.app_name))
                setHeaderAction(Action.APP_ICON)
                setLoading(true)
            }.build()
        }
        val serverHasFavorites = favoritesList.any { it.split("-")[0].toIntOrNull() == serverId.value }
        val listBuilder = if (serverHasFavorites) {
            EntityGridVehicleScreen(
                carContext,
                serverManager,
                serverId,
                prefsRepository,
                serverManager.integrationRepository(serverId.value),
                carContext.getString(commonR.string.favorites),
                domains,
                flowOf(),
                allEntities
            ) { onChangeServer(it) }.getEntityGridItems(favoritesEntities)
        } else {
            var builder = ItemList.Builder()
            if (domains.isNotEmpty()) {
                builder = getDomainList(
                    domains,
                    carContext,
                    screenManager,
                    serverManager,
                    serverId,
                    prefsRepository,
                    allEntities
                )
            }

            builder.addItem(
                getNavigationGridItem(
                    carContext,
                    screenManager,
                    serverManager.integrationRepository(serverId.value),
                    allEntities
                ).build()
            )

            if (serverManager.defaultServers.size > 1) {
                builder.addItem(
                    getChangeServerGridItem(
                        carContext,
                        screenManager,
                        serverManager,
                        serverId
                    ) { onChangeServer(it) }.build()
                )
            }
            builder
        }

        return GridTemplate.Builder().apply {
            setTitle(carContext.getString(commonR.string.app_name))
            setHeaderAction(Action.APP_ICON)
            if (isAutomotive && !isDrivingOptimized && BuildConfig.FLAVOR != "full") {
                setActionStrip(nativeModeActionStrip(carContext))
            }
            if (domains.isEmpty()) {
                setLoading(true)
            } else {
                setLoading(false)
                setSingleList(listBuilder.build())
            }
        }.build()
    }

    private fun getFavoritesList(entities: Map<String, Entity<*>>): List<Entity<*>> {
        return entities.values.filter { entity -> favoritesList.contains("${serverId.value}-${entity.entityId}") }
            .sortedBy { entity -> favoritesList.indexOf("${serverId.value}-${entity.entityId}") }
    }
}
