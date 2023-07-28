package io.homeassistant.companion.android.vehicle

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
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
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.friendlyState
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.integration.isExecuting
import io.homeassistant.companion.android.common.data.integration.onPressed
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.util.vehicle.getChangeServerGridItem
import io.homeassistant.companion.android.util.vehicle.getDomainList
import io.homeassistant.companion.android.util.vehicle.getDomainsGridItem
import io.homeassistant.companion.android.util.vehicle.getNavigationGridItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class EntityGridVehicleScreen(
    carContext: CarContext,
    val serverManager: ServerManager,
    val serverId: StateFlow<Int>,
    val prefsRepository: PrefsRepository,
    val integrationRepository: IntegrationRepository,
    val title: String,
    private val domains: MutableSet<String>,
    private val entitiesFlow: Flow<List<Entity<*>>>,
    private val allEntities: Flow<Map<String, Entity<*>>>,
    private val onChangeServer: (Int) -> Unit
) : Screen(carContext) {

    companion object {
        private const val TAG = "EntityGridVehicleScreen"
    }

    private var loading = true
    var entities: List<Entity<*>> = listOf()
    private val isFavorites = title == carContext.getString(R.string.favorites)
    private val shouldSwitchServers = serverManager.defaultServers.size > 1

    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                entitiesFlow.collect {
                    loading = false
                    val hasChanged = entities.size != it.size || entities.toSet() != it.toSet()
                    entities = it
                    if (hasChanged) invalidate()
                }
            }
        }
    }

    fun getEntityGridItems(entities: List<Entity<*>>): ItemList.Builder {
        val listBuilder = if (entities.isNotEmpty()) {
            createEntityGrid(entities)
        } else {
            getDomainList(
                domains,
                carContext,
                screenManager,
                serverManager,
                serverId,
                prefsRepository,
                allEntities
            )
        }
        if (isFavorites) {
            listBuilder.addItem(
                getNavigationGridItem(
                    carContext,
                    screenManager,
                    integrationRepository,
                    allEntities
                ).build()
            )
            listBuilder.addItem(
                getDomainsGridItem(
                    carContext,
                    screenManager,
                    serverManager,
                    integrationRepository,
                    serverId,
                    allEntities,
                    prefsRepository
                ).build()
            )
            if (shouldSwitchServers) {
                listBuilder.addItem(
                    getChangeServerGridItem(
                        carContext,
                        screenManager,
                        serverManager,
                        serverId
                    ) { onChangeServer(it) }.build()
                )
            }
        }
        return listBuilder
    }

    override fun onGetTemplate(): Template {
        val entityGrid = getEntityGridItems(entities)

        return GridTemplate.Builder().apply {
            setTitle(title)
            setHeaderAction(Action.BACK)
            if (loading) {
                setLoading(true)
            } else {
                setLoading(false)
                setSingleList(entityGrid.build())
            }
        }.build()
    }

    private fun createEntityGrid(entities: List<Entity<*>>): ItemList.Builder {
        val listBuilder = ItemList.Builder()
        val manager = carContext.getCarService(ConstraintManager::class.java)
        val gridLimit = manager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID)
        val extraGrid = if (shouldSwitchServers) 3 else 2
        entities.forEachIndexed { index, entity ->
            if (index >= (gridLimit - if (isFavorites) extraGrid else 0)) {
                Log.i(TAG, "Grid limit ($gridLimit) reached, not adding more entities (${entities.size}) for $title ")
                return@forEachIndexed
            }
            val icon = entity.getIcon(carContext) ?: CommunityMaterial.Icon.cmd_cloud_question
            val gridItem =
                GridItem.Builder()
                    .setLoading(false)
                    .setTitle(entity.friendlyName.ifEmpty { entity.entityId })
                    .setText(entity.friendlyState(carContext))

            if (entity.isExecuting()) {
                gridItem.setLoading(entity.isExecuting())
            } else {
                gridItem
                    .setOnClickListener {
                        Log.i(TAG, "${entity.entityId} clicked")
                        if (entity.domain in MainVehicleScreen.MAP_DOMAINS) {
                            val attrs = entity.attributes as? Map<*, *>
                            if (attrs != null) {
                                val lat = attrs["latitude"] as? Double
                                val lon = attrs["longitude"] as? Double
                                if (lat != null && lon != null) {
                                    val intent = Intent(
                                        CarContext.ACTION_NAVIGATE,
                                        Uri.parse("geo:$lat,$lon")
                                    )
                                    carContext.startCarApp(intent)
                                }
                            }
                        } else {
                            lifecycleScope.launch {
                                entity.onPressed(integrationRepository)
                            }
                        }
                    }
                    .setImage(
                        CarIcon.Builder(
                            IconicsDrawable(carContext, icon).apply {
                                sizeDp = 64
                            }.toAndroidIconCompat()
                        )
                            .setTint(CarColor.DEFAULT)
                            .build()
                    )
            }
            listBuilder.addItem(gridItem.build())
        }
        return listBuilder
    }
}
