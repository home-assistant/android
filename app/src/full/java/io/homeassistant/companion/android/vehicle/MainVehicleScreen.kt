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
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.IntegrationRepository
import io.homeassistant.companion.android.common.data.integration.domain
import kotlinx.coroutines.launch
import java.util.Locale
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.O)
class MainVehicleScreen(
    carContext: CarContext,
    val integrationRepository: IntegrationRepository,
) : Screen(carContext) {

    companion object {
        private const val TAG = "MainVehicleScreen"

        private val SUPPORTED_DOMAINS_WITH_STRING = mapOf(
            "button" to commonR.string.buttons,
            "cover" to commonR.string.covers,
            "input_boolean" to commonR.string.input_booleans,
            "input_button" to commonR.string.input_buttons,
            "light" to commonR.string.lights,
            "lock" to commonR.string.locks,
            "scene" to commonR.string.scenes,
            "script" to commonR.string.scripts,
            "switch" to commonR.string.switches,
        )
        private val SUPPORTED_DOMAINS = SUPPORTED_DOMAINS_WITH_STRING.keys
    }

    private val domains = mutableSetOf<String>()
    private val entities = mutableMapOf<String, Entity<*>>()

    init {
        lifecycleScope.launch {
            integrationRepository.getEntities()?.forEach { entity ->
                val domain = entity.domain
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
            val friendlyDomain =
                SUPPORTED_DOMAINS_WITH_STRING[domain]?.let { carContext.getString(it) }
                    ?: domain.split("_").joinToString(" ") { word ->
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
