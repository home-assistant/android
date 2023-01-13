package io.homeassistant.companion.android.vehicle

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.Screen
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
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.getIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class MapVehicleScreen(
    carContext: CarContext,
    val integrationRepository: IntegrationRepository,
    val entitiesFlow: Flow<List<Entity<*>>>,
) : Screen(carContext) {

    companion object {
        private const val TAG = "MapVehicleScreen"
    }

    var loading = true
    var entities: List<Entity<*>> = listOf()

    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                entitiesFlow.collect {
                    loading = false
                    entities = it
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        entities
            .mapNotNull {
                val attrs = it.attributes as? Map<*, *>
                if (attrs != null) {
                    val lat = attrs["latitude"] as? Double
                    val lon = attrs["longitude"] as? Double
                    if (lat != null && lon != null) {
                        return@mapNotNull Pair(it, listOf(lat, lon))
                    }
                }
                return@mapNotNull null
            }
            .sortedBy { it.first.friendlyName }
            .forEach { (entity, location) ->
                val icon = entity.getIcon(carContext) ?: CommunityMaterial.Icon.cmd_account
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(entity.friendlyName)
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
                            Log.i(TAG, "${entity.entityId} clicked")
                            val intent = Intent(
                                CarContext.ACTION_NAVIGATE,
                                Uri.parse("geo:${location[0]},${location[1]}")
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
                listBuilder.setNoItemsMessage("No entities with locations found.")
                setSingleList(listBuilder.build())
            }
        }.build()
    }
}
