package io.homeassistant.companion.android.vehicle

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.domain
import kotlinx.coroutines.launch
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
class MainVehicleScreen(
    carContext: CarContext,
    val integrationRepository: IntegrationRepository,
) : Screen(carContext) {

    companion object {
        private const val TAG = "MainVehicleScreen"

        private val SUPPORTED_DOMAINS = listOf(
            "button",
            "cover",
            "input_boolean",
            "light",
            "lock",
            "scene",
            "script",
            "switch",
        )
    }

    private val domains = mutableSetOf<String>()
    private val entities = mutableMapOf<String, Entity<*>>()

    init {
        lifecycleScope.launch {
            integrationRepository.getEntities()?.forEach { entity ->
                val domain = entity.entityId.split(".")[0]
                if (domain in SUPPORTED_DOMAINS) {
                    entities[entity.entityId] = entity
                    domains.add(domain)
                }
            }
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        domains.forEach { domain ->
            val friendlyDomain = domain.split("_").joinToString(" ") { word ->
                word.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
            }
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(friendlyDomain)
                    .setOnClickListener {
                        Log.i(TAG, "$domain clicked")
                        screenManager.push(
                            EntityGridVehicleScreen(
                                carContext,
                                integrationRepository,
                                friendlyDomain,
                                entities.filter { it.value.domain == domain }.toMutableMap()
                            )
                        )
                    }
                    .build()
            )
        }

        // TODO: Add row for zones so we can start navigation?

        return ListTemplate.Builder()
            .setTitle(carContext.getString(io.homeassistant.companion.android.common.R.string.app_name))
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }
}
