package io.homeassistant.companion.android.vehicle

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
            createDomainGrid()
        }
        if (isFavorites) {
            listBuilder.addItem(getNavigationGridItem().build())
            listBuilder.addItem(getDomainsGridItem().build())
            if (shouldSwitchServers) {
                listBuilder.addItem(getChangeServerGridItem().build())
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
                        lifecycleScope.launch {
                            entity.onPressed(integrationRepository)
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

    private fun createDomainGrid(): ItemList.Builder {
        return MainVehicleScreen(
            carContext,
            serverManager,
            serverId,
            allEntities,
            prefsRepository
        ) { }.addDomainList(domains)
    }

    private fun getChangeServerGridItem(): GridItem.Builder {
        return GridItem.Builder().apply {
            setTitle(carContext.getString(R.string.aa_change_server))
            setImage(
                CarIcon.Builder(
                    IconicsDrawable(
                        carContext,
                        CommunityMaterial.Icon2.cmd_home_switch
                    ).apply {
                        sizeDp = 48
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

    private fun getDomainsGridItem(): GridItem.Builder {
        return GridItem.Builder().apply {
            setTitle(carContext.getString(R.string.all_entities))
            setImage(
                CarIcon.Builder(
                    IconicsDrawable(
                        carContext,
                        CommunityMaterial.Icon3.cmd_view_list
                    ).apply {
                        sizeDp = 48
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
                        prefsRepository
                    )
                )
            }
        }
    }

    private fun getNavigationGridItem(): GridItem.Builder {
        return GridItem.Builder().apply {
            setTitle(carContext.getString(R.string.aa_navigation))
            setImage(
                CarIcon.Builder(
                    IconicsDrawable(
                        carContext,
                        CommunityMaterial.Icon3.cmd_map_outline
                    ).apply {
                        sizeDp = 48
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
                        allEntities.map { it.values.filter { entity -> entity.domain in MainVehicleScreen.MAP_DOMAINS } }
                    )
                )
            }
        }
    }
}
