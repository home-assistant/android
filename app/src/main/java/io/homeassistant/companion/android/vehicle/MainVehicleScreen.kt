package io.homeassistant.companion.android.vehicle

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.toAndroidIconCompat
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.getIcon
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class MainVehicleScreen(
    carContext: CarContext,
    val integrationRepository: IntegrationRepository,
) : Screen(carContext) {

    companion object {
        private const val TAG = "MainVehicleScreen"
        private val SUPPORTED_DOMAINS = listOf(
            "cover",
            "input_boolean",
            "light",
            "lock",
            "script",
            "switch",
        )
    }

    val entities = sortedMapOf<String, Entity<*>>()

    init {
        lifecycleScope.launch {
            integrationRepository.getEntityUpdates()?.collect { entity ->
                if (entities.containsKey(entity.entityId)) {
                    entities[entity.entityId] = entity
                    invalidate()
                }
            }
        }
        lifecycleScope.launch {
            integrationRepository.getEntities()?.forEach { entity ->
                val domain = entity.entityId.split(".")[0]
                if (domain in SUPPORTED_DOMAINS) {
                    entities[entity.entityId] = entity
                } else {
                    Log.d(TAG, "Ignoring entity: ${entity.entityId}")
                }
            }
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        entities.forEach { (entityId, entity) ->
            val friendlyName =
                (entity.attributes as? Map<*, *>)?.get("friendly_name")?.toString() ?: entityId
            val icon = entity.getIcon(carContext) ?: CommunityMaterial.Icon.cmd_cloud_question
            listBuilder.addItem(
                GridItem.Builder()
                    .setLoading(false)
                    .setTitle(friendlyName)
                    .setText(entity.state)
                    .setImage(
                        CarIcon.Builder(IconicsDrawable(carContext, icon).toAndroidIconCompat())
                            .setTint(CarColor.DEFAULT)
                            .build()
                    )
                    .setOnClickListener {
                        Log.i(TAG, "$entityId clicked")
                    }
                    .build()
            )
        }

        return GridTemplate.Builder()
            .setTitle("Entities")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }
}
