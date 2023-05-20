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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.sizeDp
import com.mikepenz.iconics.utils.toAndroidIconCompat
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.friendlyName
import io.homeassistant.companion.android.common.data.integration.friendlyState
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.integration.isExecuting
import io.homeassistant.companion.android.common.data.integration.onPressed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class EntityGridVehicleScreen(
    carContext: CarContext,
    val integrationRepository: IntegrationRepository,
    val title: String,
    val entitiesFlow: Flow<List<Entity<*>>>
) : Screen(carContext) {

    companion object {
        private const val TAG = "EntityGridVehicleScreen"
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
        entities.forEach { entity ->
            val icon = entity.getIcon(carContext) ?: CommunityMaterial.Icon.cmd_cloud_question
            val gridItem =
                GridItem.Builder()
                    .setLoading(false)
                    .setTitle(entity.friendlyName)
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

        return GridTemplate.Builder().apply {
            setTitle(title)
            setHeaderAction(Action.BACK)
            if (loading) {
                setLoading(true)
            } else {
                setLoading(false)
                setSingleList(listBuilder.build())
            }
        }.build()
    }
}
