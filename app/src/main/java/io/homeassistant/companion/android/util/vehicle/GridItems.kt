package io.homeassistant.companion.android.util.vehicle

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.ScreenManager
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.ItemList
import androidx.lifecycle.LifecycleCoroutineScope
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.sizeDp
import com.mikepenz.iconics.utils.toAndroidIconCompat
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.data.websocket.impl.entities.EntityRegistryResponse
import io.homeassistant.companion.android.common.util.capitalize
import io.homeassistant.companion.android.util.RegistriesDataHandler
import io.homeassistant.companion.android.vehicle.ChangeServerScreen
import io.homeassistant.companion.android.vehicle.DomainListScreen
import io.homeassistant.companion.android.vehicle.EntityGridVehicleScreen
import io.homeassistant.companion.android.vehicle.MapVehicleScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private const val TAG = "GridItems"

fun getChangeServerGridItem(
    carContext: CarContext,
    screenManager: ScreenManager,
    serverManager: ServerManager,
    serverId: StateFlow<Int>,
    onChangeServer: (Int) -> Unit
): GridItem.Builder {
    return GridItem.Builder().apply {
        setTitle(carContext.getString(R.string.aa_change_server))
        setImage(
            CarIcon.Builder(
                IconicsDrawable(
                    carContext,
                    CommunityMaterial.Icon2.cmd_home_switch
                ).apply {
                    sizeDp = 64
                }.toAndroidIconCompat()
            )
                .setTint(CarColor.DEFAULT)
                .build()
        )
        setOnClickListener {
            Log.i(TAG, "Change server clicked")
            screenManager.pushForResult(
                ChangeServerScreen(
                    carContext,
                    serverManager,
                    serverId
                )
            ) {
                it?.toString()?.toIntOrNull()?.let { serverId ->
                    onChangeServer(serverId)
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun getNavigationGridItem(
    carContext: CarContext,
    screenManager: ScreenManager,
    integrationRepository: IntegrationRepository,
    allEntities: Flow<Map<String, Entity<*>>>
): GridItem.Builder {
    return GridItem.Builder().apply {
        setTitle(carContext.getString(R.string.aa_navigation))
        setImage(
            CarIcon.Builder(
                IconicsDrawable(
                    carContext,
                    CommunityMaterial.Icon3.cmd_map_outline
                ).apply {
                    sizeDp = 64
                }.toAndroidIconCompat()
            )
                .setTint(CarColor.DEFAULT)
                .build()
        )
        setOnClickListener {
            Log.i(TAG, "Navigation clicked")
            screenManager.push(
                MapVehicleScreen(
                    carContext,
                    integrationRepository,
                    allEntities.map { it.values.filter { entity -> entity.domain in MAP_DOMAINS } }
                )
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun getDomainList(
    domains: MutableSet<String>,
    carContext: CarContext,
    screenManager: ScreenManager,
    serverManager: ServerManager,
    serverId: StateFlow<Int>,
    prefsRepository: PrefsRepository,
    allEntities: Flow<Map<String, Entity<*>>>,
    entityRegistry: List<EntityRegistryResponse>?,
    lifecycleScope: LifecycleCoroutineScope
): ItemList.Builder {
    val listBuilder = ItemList.Builder()
    domains.forEach { domain ->
        val friendlyDomain =
            SUPPORTED_DOMAINS_WITH_STRING[domain]?.let { carContext.getString(it) }
                ?: domain.split("_").joinToString(" ") { word ->
                    word.capitalize(Locale.getDefault())
                }
        val icon = Entity(
            "$domain.ha_android_placeholder",
            "",
            mapOf<Any, Any>(),
            Calendar.getInstance(),
            Calendar.getInstance(),
            null
        ).getIcon(carContext)

        val entityList = allEntities.map {
            it.values.filter { entity ->
                entity.domain == domain && RegistriesDataHandler.getHiddenByForEntity(
                    entity.entityId,
                    entityRegistry
                ) == null
            }
        }
        var domainIsEmpty = false
        lifecycleScope.launch {
            entityList.collect {
                domainIsEmpty = it.isEmpty()
            }
        }
        if (!domainIsEmpty) {
            listBuilder.addItem(
                GridItem.Builder().apply {
                    setImage(
                        CarIcon.Builder(
                            IconicsDrawable(carContext, icon)
                                .apply {
                                    sizeDp = 64
                                }.toAndroidIconCompat()
                        )
                            .setTint(CarColor.DEFAULT)
                            .build()
                    )
                }
                    .setTitle(friendlyDomain)
                    .setOnClickListener {
                        Log.i(TAG, "Domain:$domain clicked")
                        screenManager.push(
                            EntityGridVehicleScreen(
                                carContext,
                                serverManager,
                                serverId,
                                prefsRepository,
                                serverManager.integrationRepository(serverId.value),
                                friendlyDomain,
                                entityRegistry,
                                domains,
                                entityList,
                                allEntities
                            ) { }
                        )
                    }
                    .build()
            )
        }
    }
    listBuilder.setNoItemsMessage(carContext.getString(R.string.no_supported_entities))

    return listBuilder
}

@RequiresApi(Build.VERSION_CODES.O)
fun getDomainsGridItem(
    carContext: CarContext,
    screenManager: ScreenManager,
    serverManager: ServerManager,
    integrationRepository: IntegrationRepository,
    serverId: StateFlow<Int>,
    allEntities: Flow<Map<String, Entity<*>>>,
    prefsRepository: PrefsRepository,
    entityRegistry: List<EntityRegistryResponse>?
): GridItem.Builder {
    return GridItem.Builder().apply {
        setTitle(carContext.getString(R.string.all_entities))
        setImage(
            CarIcon.Builder(
                IconicsDrawable(
                    carContext,
                    CommunityMaterial.Icon3.cmd_view_list
                ).apply {
                    sizeDp = 64
                }.toAndroidIconCompat()
            )
                .setTint(CarColor.DEFAULT)
                .build()
        )
        setOnClickListener {
            Log.i(TAG, "Categories clicked")
            screenManager.push(
                DomainListScreen(
                    carContext,
                    serverManager,
                    integrationRepository,
                    serverId,
                    allEntities,
                    prefsRepository,
                    entityRegistry
                )
            )
        }
    }
}
