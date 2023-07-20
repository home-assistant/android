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
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
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
import io.homeassistant.companion.android.common.data.integration.getIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.O)
class MapVehicleScreen(
    carContext: CarContext,
    val integrationRepository: IntegrationRepository,
    val entitiesFlow: Flow<List<Entity<*>>>
) : Screen(carContext) {

    companion object {
        private const val TAG = "MapVehicleScreen"
    }

    var loading = true
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
        val listLimit = manager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        val listBuilder = ItemList.Builder()
        entities
            .map { // Null checks handled during collection
                val attrs = it.attributes as Map<*, *>
                val lat = attrs["latitude"] as Double
                val lon = attrs["longitude"] as Double
                Pair(it, listOf(lat, lon))
            }
            .sortedBy { it.first.friendlyName }
            .forEachIndexed { index, pair ->
                if (index >= listLimit) {
                    Log.i(TAG, "List limit ($listLimit) reached, not adding any more navigation entities (${entities.size})")
                    return@forEachIndexed
                }
                val icon = pair.first.getIcon(carContext) ?: CommunityMaterial.Icon.cmd_account
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(pair.first.friendlyName)
                        .setImage(
                            CarIcon.Builder(
                                IconicsDrawable(carContext, icon)
                                    .apply {
                                        sizeDp = 48
                                    }.toAndroidIconCompat()
                            )
                                .setTint(CarColor.DEFAULT)
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

        return ListTemplate.Builder().apply {
            setTitle(carContext.getString(R.string.aa_navigation))
            setHeaderAction(Action.BACK)
            if (loading) {
                setLoading(true)
            } else {
                setLoading(false)
                listBuilder.setNoItemsMessage(carContext.getString(commonR.string.aa_no_entities_with_locations))
                setSingleList(listBuilder.build())
            }
        }.build()
    }
}
