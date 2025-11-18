package io.homeassistant.companion.android.vehicle

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.sizeDp
import com.mikepenz.iconics.utils.toAndroidIconCompat
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.prefs.AutoFavorite
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.util.isAutomotive
import io.homeassistant.companion.android.sensors.SensorReceiver
import io.homeassistant.companion.android.util.vehicle.SUPPORTED_DOMAINS
import io.homeassistant.companion.android.util.vehicle.getChangeServerGridItem
import io.homeassistant.companion.android.util.vehicle.getDomainList
import io.homeassistant.companion.android.util.vehicle.getHeaderBuilder
import io.homeassistant.companion.android.util.vehicle.getNavigationGridItem
import io.homeassistant.companion.android.util.vehicle.nativeModeAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class MainVehicleScreen(
    carContext: CarContext,
    val serverManager: ServerManager,
    private val serverId: StateFlow<Int>,
    private val allEntities: Flow<Map<String, Entity>>,
    private val prefsRepository: PrefsRepository,
    private val onChangeServer: (Int) -> Unit,
    private val onRefresh: () -> Unit,
) : BaseVehicleScreen(carContext) {

    private var favoritesEntities: List<Entity> = listOf()
    private var entityRegistry: List<EntityRegistryResponse>? = null
    private var favoritesList = emptyList<AutoFavorite>()
    private var isLoggedIn: Boolean? = null
    private val domains = mutableSetOf<String>()
    private var domainsJob: Job? = null
    private var domainsAdded = false
    private var domainsAddedFor: Int? = null

    private val isAutomotive get() = carContext.isAutomotive()

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
                serverId.collect { server ->
                    if (domainsAddedFor != server) {
                        domainsAdded = false
                        domainsAddedFor = server
                        invalidate() // Show loading state
                        entityRegistry = serverManager.webSocketRepository(server).getEntityRegistry()
                    }

                    if (domainsJob?.isActive == true) domainsJob?.cancel()
                    domainsJob = launch {
                        allEntities.collect { entities ->
                            val newDomains = entities.values
                                .map { it.domain }
                                .distinct()
                                .filter { it in SUPPORTED_DOMAINS }
                                .toSet()
                            var invalidate = newDomains.size != domains.size || newDomains != domains || !domainsAdded
                            domains.clear()
                            domains.addAll(newDomains)
                            domainsAdded = true

                            val newFavorites = getFavoritesList(entities)
                            invalidate =
                                invalidate ||
                                newFavorites.size != favoritesEntities.size ||
                                newFavorites.toSet() != favoritesEntities.toSet()
                            favoritesEntities = newFavorites

                            if (invalidate) invalidate()
                        }
                    }
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
                setHeader(
                    carContext.getHeaderBuilder(
                        title = commonR.string.app_name,
                        action = Action.APP_ICON,
                    ).build(),
                )
                setLoading(true)
            }.build()
        }
        val serverHasFavorites = favoritesList.any { it.serverId == serverId.value }
        val listBuilder = if (serverHasFavorites) {
            EntityGridVehicleScreen(
                carContext,
                serverManager,
                serverId,
                prefsRepository,
                { serverManager.integrationRepository(serverId.value) },
                carContext.getString(commonR.string.favorites),
                entityRegistry,
                domains,
                flowOf(),
                allEntities,
            ) { onChangeServer(it) }.getEntityGridItems(favoritesEntities)
        } else {
            var builder = ItemList.Builder()
            if (domains.isNotEmpty() && domainsAdded) {
                builder = getDomainList(
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
            }

            builder.addItem(
                getNavigationGridItem(
                    carContext,
                    screenManager,
                    { serverManager.integrationRepository(serverId.value) },
                    allEntities,
                    entityRegistry,
                ).build(),
            )

            if (serverManager.defaultServers.size > 1) {
                builder.addItem(
                    getChangeServerGridItem(
                        carContext,
                        screenManager,
                        serverManager,
                        serverId,
                    ) { onChangeServer(it) }.build(),
                )
            }
            builder
        }
        val refreshAction = Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconicsDrawable(carContext, CommunityMaterial.Icon3.cmd_refresh).apply {
                        sizeDp = 64
                    }.toAndroidIconCompat(),
                )
                    .setTint(CarColor.DEFAULT)
                    .build(),
            )
            .setOnClickListener {
                onRefresh()
            }.build()

        val headerBuilder = carContext.getHeaderBuilder(commonR.string.app_name, Action.APP_ICON)
        if (isAutomotive && !isDrivingOptimized && BuildConfig.FLAVOR != "full") {
            headerBuilder.addEndHeaderAction(nativeModeAction(carContext))
        }
        headerBuilder.addEndHeaderAction(refreshAction)

        return GridTemplate.Builder().apply {
            setHeader(headerBuilder.build())
            if (!domainsAdded) {
                setLoading(true)
            } else {
                setLoading(false)
                setSingleList(listBuilder.build())
            }
        }.build()
    }

    private fun getFavoritesList(entities: Map<String, Entity>): List<Entity> {
        return entities.values.filter { entity ->
            favoritesList.contains(AutoFavorite(serverId.value, entity.entityId))
        }
            .sortedBy { entity -> favoritesList.indexOf(AutoFavorite(serverId.value, entity.entityId)) }
    }
}
