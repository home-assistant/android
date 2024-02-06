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
import com.mikepenz.iconics.utils.sizeDp
import com.mikepenz.iconics.utils.toAndroidIconCompat
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.EntityExt
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.friendlyState
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.integration.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class MapVehicleScreen(
    carContext: CarContext,
    val integrationRepository: IntegrationRepository,
    private val entitiesFlow: Flow<List<Entity<*>>>
) : Screen(carContext) {

    companion object {
        private const val TAG = "MapVehicleScreen"
    }

    private var loading = true
    var entities: Set<Entity<*>> = setOf()

    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                entitiesFlow.collect {
                    loading = false
                    val newSet = it
                        .filter { entity ->
                            if (entity.domain == "device_tracker" && entity.state == "home") {
                                return@filter false
                            }
                            val attrs = entity.attributes as? Map<*, *>
                            if (attrs != null) {
                                val lat = attrs["latitude"] as? Double
                                val lon = attrs["longitude"] as? Double
                                return@filter lat != null && lon != null
                            }
                            return@filter false
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
            .map { // Null checks handled during collection
                val attrs = it.attributes as Map<*, *>
                val lat = attrs["latitude"] as Double
                val lon = attrs["longitude"] as Double
                Pair(it, listOf(lat, lon))
            }
            .sortedBy { it.first.friendlyName }
            .forEachIndexed { index, pair ->
                if (index >= gridLimit) {
                    Log.i(TAG, "Grid limit ($gridLimit) reached, not adding any more navigation entities (${entities.size})")
                    return@forEachIndexed
                }
                val icon = pair.first.getIcon(carContext)
                gridBuilder.addItem(
                    GridItem.Builder()
                        .setTitle(pair.first.friendlyName)
                        .setText(pair.first.friendlyState(carContext))
                        .setImage(
                            CarIcon.Builder(
                                IconicsDrawable(carContext, icon)
                                    .apply {
                                        sizeDp = 64
                                    }.toAndroidIconCompat()
                            )
                                .setTint(
                                    if (pair.first.isActive() && pair.first.domain in EntityExt.STATE_COLORED_DOMAINS) {
                                        CarColor.createCustom(
                                            carContext.getColor(R.color.colorYellow),
                                            carContext.getColor(R.color.colorYellow)
                                        )
                                    } else {
                                        CarColor.DEFAULT
                                    }
                                )
                                .build()
                        )
                        .setOnClickListener {
                            Log.i(TAG, "${pair.first.entityId} clicked")
                            val intent = Intent(
                                CarContext.ACTION_NAVIGATE,
                                Uri.parse("geo:${pair.second[0]},${pair.second[1]}")
                            )
                            carContext.startCarApp(intent)
                        }
                        .build()
                )
            }

        return GridTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.aa_navigation))
            setHeaderAction(Action.BACK)
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
