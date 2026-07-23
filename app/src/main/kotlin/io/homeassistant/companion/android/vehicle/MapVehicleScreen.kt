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
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.integration.IntegrationDomains.DEVICE_TRACKER_DOMAIN
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.display.EntityDisplay
import io.homeassistant.companion.android.util.vehicle.getHeaderBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.O)
class MapVehicleScreen(
    carContext: CarContext,
    val integrationRepositoryProvider: suspend () -> IntegrationRepository,
    private val entitiesFlow: Flow<List<EntityDisplay>>,
) : Screen(carContext) {

    private var loading = true
    var entities: Set<EntityDisplay> = setOf()

    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                entitiesFlow.collect {
                    loading = false
                    val newSet = it
                        .filter { entity ->
                            if (entity.domain == DEVICE_TRACKER_DOMAIN && entity.rawState == "home") {
                                return@filter false
                            }
                            entity.coordinates != null
                        }
                        .toSet()
                    val hasChanged = entities.size != newSet.size || entities != newSet
                    entities = newSet
                    if (hasChanged) invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val manager = carContext.getCarService(ConstraintManager::class.java)
        val gridLimit = manager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID)
        val gridBuilder = ItemList.Builder()
        entities
            .map {
                // Null check handled during collection
                Pair(it, it.coordinates!!)
            }
            .sortedBy { it.first.name }
            .forEachIndexed { index, pair ->
                if (index >= gridLimit) {
                    Timber.i(
                        "Grid limit ($gridLimit) reached, not adding any more navigation entities (${entities.size})",
                    )
                    return@forEachIndexed
                }
                val icon = pair.first.icon
                gridBuilder.addItem(
                    GridItem.Builder()
                        .setTitle(pair.first.name)
                        .setText(pair.first.state.resolve(carContext))
                        .setImage(
                            CarIcon.Builder(
                                IconicsDrawable(carContext, icon)
                                    .apply {
                                        sizeDp = 64
                                    }.toAndroidIconCompat(),
                            )
                                .setTint(
                                    if (pair.first.isActive && pair.first.domain in EntityExt.STATE_COLORED_DOMAINS) {
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
                        .setOnClickListener {
                            Timber.i("${pair.first.entityId} clicked")
                            lifecycleScope.launch {
                                try {
                                    integrationRepositoryProvider().fireEvent(
                                        "android.navigation_started",
                                        mapOf(
                                            "entity_id" to pair.first.entityId,
                                        ),
                                    )
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Timber.e(e, "Unable to send navigation started event")
                                }
                            }
                            val intent = Intent(
                                CarContext.ACTION_NAVIGATE,
                                "geo:${pair.second.latitude},${pair.second.longitude}".toUri(),
                            )
                            carContext.startCarApp(intent)
                        }
                        .build(),
                )
            }

        return GridTemplate.Builder().apply {
            setHeader(carContext.getHeaderBuilder(commonR.string.aa_navigation).build())
            if (loading) {
                setLoading(true)
            } else {
                setLoading(false)
                gridBuilder.setNoItemsMessage(carContext.getString(commonR.string.aa_no_entities_with_locations))
                setSingleList(gridBuilder.build())
            }
        }.build()
    }
}
