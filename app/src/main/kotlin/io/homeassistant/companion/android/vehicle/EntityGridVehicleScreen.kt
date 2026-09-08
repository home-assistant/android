package io.homeassistant.companion.android.vehicle

import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.mikepenz.iconics.utils.toAndroidIconCompat
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplayState
import io.homeassistant.companion.android.common.data.integration.onPressed
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.util.vehicle.MAP_DOMAINS
import io.homeassistant.companion.android.util.vehicle.NOT_ACTIONABLE_DOMAINS
import io.homeassistant.companion.android.util.vehicle.SUPPORTED_DOMAINS
import io.homeassistant.companion.android.util.vehicle.canNavigate
import io.homeassistant.companion.android.util.vehicle.getDomainList
import io.homeassistant.companion.android.util.vehicle.getDomainsGridItem
import io.homeassistant.companion.android.util.vehicle.getHeaderBuilder
import io.homeassistant.companion.android.util.vehicle.getNavigationGridItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.O)
class EntityGridVehicleScreen(
    carContext: CarContext,
    val serverManager: ServerManager,
    val serverId: StateFlow<Int>,
    val prefsRepository: PrefsRepository,
    val integrationRepositoryProvider: suspend () -> IntegrationRepository,
    val title: String,
    private val domains: MutableSet<String>,
    private val entitiesFlow: Flow<List<EntityDisplay>>,
    private val entitiesState: Flow<EntityDisplayState<EntityDisplay>>,
) : Screen(carContext) {

    private var loading = true
    var entities: List<EntityDisplay> = listOf()
    private val isFavorites = title == carContext.getString(R.string.favorites)

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

    /**
     * Get an [ItemList.Builder] for a grid of entities.
     *
     * If this function is called for favorites (outside of this screen's lifecycle), items are added for:
     * - Navigation
     * - All domains
     *
     * @param canSwitchServers If `true` and the function is called for favorites, the item limit is adjusted to keep
     * space for a 'Switch server' item
     */
    fun getEntityGridItems(entities: List<EntityDisplay>, canSwitchServers: Boolean): ItemList.Builder {
        val listBuilder = if (entities.isNotEmpty()) {
            createEntityGrid(entities, canSwitchServers)
        } else {
            getDomainList(
                domains,
                carContext,
                screenManager,
                serverManager,
                serverId,
                prefsRepository,
                entitiesState,
                lifecycleScope,
            )
        }
        if (isFavorites) {
            listBuilder.addItem(
                getNavigationGridItem(
                    carContext,
                    screenManager,
                    integrationRepositoryProvider,
                    entitiesState,
                ).build(),
            )
            if (domains.isNotEmpty()) {
                listBuilder.addItem(
                    getDomainsGridItem(
                        carContext,
                        screenManager,
                        serverManager,
                        serverId,
                        entitiesState,
                        prefsRepository,
                    ).build(),
                )
            }
        }
        return listBuilder
    }

    override fun onGetTemplate(): Template {
        val entityGrid = getEntityGridItems(entities, false)

        return GridTemplate.Builder().apply {
            setHeader(getHeaderBuilder(title).build())
            if (loading) {
                setLoading(true)
            } else {
                setLoading(false)
                setSingleList(entityGrid.build())
            }
        }.build()
    }

    private fun createEntityGrid(entities: List<EntityDisplay>, canSwitchServers: Boolean): ItemList.Builder {
        val listBuilder = ItemList.Builder()
        val manager = carContext.getCarService(ConstraintManager::class.java)
        val gridLimit = manager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID)
        val extraGrid = if (canSwitchServers) 3 else 2
        entities.forEachIndexed { index, displayed ->
            if (index >= (gridLimit - if (isFavorites) extraGrid else 0)) {
                Timber.i("Grid limit ($gridLimit) reached, not adding more entities (${entities.size}) for $title ")
                return@forEachIndexed
            }
            val gridItem =
                GridItem.Builder()
                    .setLoading(false)
                    .setTitle(displayed.name)
                    .setText(displayed.state.resolve(carContext))

            if (displayed.isExecuting) {
                gridItem.setLoading(displayed.isExecuting)
            } else {
                if (displayed.domain !in NOT_ACTIONABLE_DOMAINS ||
                    canNavigate(displayed) ||
                    displayed.alarm?.isActionable == true
                ) {
                    gridItem
                        .setOnClickListener {
                            Timber.i("${displayed.entityId} clicked")
                            when (displayed.domain) {
                                in MAP_DOMAINS -> {
                                    displayed.coordinates?.let { coordinates ->
                                        val intent = Intent(
                                            CarContext.ACTION_NAVIGATE,
                                            "geo:${coordinates.latitude},${coordinates.longitude}".toUri(),
                                        )
                                        carContext.startCarApp(intent)
                                    }
                                }

                                in SUPPORTED_DOMAINS -> {
                                    lifecycleScope.launch {
                                        try {
                                            displayed.onPressed(integrationRepositoryProvider())
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            Timber.e(e, "Failed to handle entity onPressed")
                                        }
                                    }
                                }

                                else -> {
                                    // No op
                                }
                            }
                        }
                }

                gridItem
                    .setImage(
                        CarIcon.Builder(
                            IconicsDrawable(carContext, displayed.icon).apply {
                                sizeDp = 64
                            }.toAndroidIconCompat(),
                        )
                            .setTint(
                                if (displayed.isActive && displayed.domain in EntityExt.STATE_COLORED_DOMAINS) {
                                    CarColor.createCustom(
                                        carContext.getColor(R.color.colorYellow),
                                        carContext.getColor(R.color.colorYellow),
                                    )
                                } else {
                                    CarColor.DEFAULT
                                },
                            )
                            .build(),
                    )
            }
            listBuilder.addItem(gridItem.build())
        }
        return listBuilder
    }
}
