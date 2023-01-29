package io.homeassistant.companion.android.vehicle

import android.content.Intent
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
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.sizeDp
import com.mikepenz.iconics.utils.toAndroidIconCompat
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.launch.LaunchActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.O)
class MainVehicleScreen(
    carContext: CarContext,
    val serverManager: ServerManager,
    private val allEntities: Flow<Map<String, Entity<*>>>
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

        private val MAP_DOMAINS = listOf(
            "device_tracker",
            "person",
            "zone",
        )
    }

    private var isLoggedIn: Boolean? = null
    private val domains = mutableSetOf<String>()

    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                isLoggedIn = serverManager.isRegistered() &&
                    serverManager.authenticationRepository().getSessionState() == SessionState.CONNECTED
                invalidate()
                while (isLoggedIn != true) {
                    delay(1000)
                    isLoggedIn = serverManager.isRegistered() &&
                        serverManager.authenticationRepository().getSessionState() == SessionState.CONNECTED
                    invalidate()
                }
                allEntities.collect { entities ->
                    domains.clear()
                    entities.values.forEach {
                        if (it.domain in SUPPORTED_DOMAINS) {
                            domains.add(it.domain)
                        }
                    }
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        if (isLoggedIn == false) {
            return MessageTemplate.Builder(carContext.getString(commonR.string.aa_app_not_logged_in))
                .setTitle(carContext.getString(commonR.string.app_name))
                .setHeaderAction(Action.APP_ICON)
                .addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(commonR.string.login))
                        .setOnClickListener(
                            ParkedOnlyOnClickListener.create {
                                Log.i(TAG, "Starting login activity")
                                carContext.startActivity(
                                    Intent(carContext, LaunchActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            }
                        )
                        .build()
                )
                .build()
        }

        val listBuilder = ItemList.Builder()
        domains.forEach { domain ->
            val friendlyDomain =
                SUPPORTED_DOMAINS_WITH_STRING[domain]?.let { carContext.getString(it) }
                    ?: domain.split("_").joinToString(" ") { word ->
                        word.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        }
                    }
            val icon = Entity(
                "$domain.ha_android_placeholder",
                "",
                mapOf<Any, Any>(),
                Calendar.getInstance(),
                Calendar.getInstance(),
                null
            ).getIcon(carContext)

            listBuilder.addItem(
                Row.Builder().apply {
                    if (icon != null) {
                        setImage(
                            CarIcon.Builder(
                                IconicsDrawable(carContext, icon)
                                    .apply {
                                        sizeDp = 48
                                    }.toAndroidIconCompat()
                            )
                                .setTint(CarColor.DEFAULT)
                                .build()
                        )
                    }
                }
                    .setTitle(friendlyDomain)
                    .setOnClickListener {
                        Log.i(TAG, "Domain:$domain clicked")
                        screenManager.push(
                            EntityGridVehicleScreen(
                                carContext,
                                serverManager.integrationRepository(),
                                friendlyDomain,
                                allEntities.map { it.values.filter { entity -> entity.domain == domain } }
                            )
                        )
                    }
                    .build()
            )
        }

        listBuilder.addItem(
            Row.Builder()
                .setImage(
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
                .setTitle(carContext.getString(commonR.string.aa_navigation))
                .setOnClickListener {
                    Log.i(TAG, "Navigation clicked")
                    screenManager.push(
                        MapVehicleScreen(
                            carContext,
                            serverManager.integrationRepository(),
                            allEntities.map { it.values.filter { entity -> entity.domain in MAP_DOMAINS } }
                        )
                    )
                }
                .build()
        )

        return ListTemplate.Builder().apply {
            setTitle(carContext.getString(commonR.string.app_name))
            setHeaderAction(Action.APP_ICON)
            if (domains.isEmpty()) {
                setLoading(true)
            } else {
                setLoading(false)
                setSingleList(listBuilder.build())
            }
        }.build()
    }
}
