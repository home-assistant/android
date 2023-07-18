package io.homeassistant.companion.android.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.sizeDp
import com.mikepenz.iconics.utils.toAndroidIconCompat
import io.homeassistant.companion.android.BuildConfig
import io.homeassistant.companion.android.common.data.authentication.SessionState
import io.homeassistant.companion.android.common.data.integration.Entity
import io.homeassistant.companion.android.common.data.integration.domain
import io.homeassistant.companion.android.common.data.integration.getIcon
import io.homeassistant.companion.android.common.data.prefs.PrefsRepository
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.util.capitalize
import io.homeassistant.companion.android.launch.LaunchActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import io.homeassistant.companion.android.common.R as commonR

@RequiresApi(Build.VERSION_CODES.O)
class MainVehicleScreen(
    carContext: CarContext,
    val serverManager: ServerManager,
    private val serverId: StateFlow<Int>,
    private val allEntities: Flow<Map<String, Entity<*>>>,
    private val prefsRepository: PrefsRepository,
    private val onChangeServer: (Int) -> Unit
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
            "switch" to commonR.string.switches
        )
        val SUPPORTED_DOMAINS = SUPPORTED_DOMAINS_WITH_STRING.keys

        private val MAP_DOMAINS = listOf(
            "device_tracker",
            "person",
            "sensor",
            "zone"
        )
    }

    private var favoritesList = emptyList<String>()
    private var isLoggedIn: Boolean? = null
    private val domains = mutableSetOf<String>()
    private var car: Car? = null
    private var carRestrictionManager: CarUxRestrictionsManager? = null
    private val iDrivingOptimized
        get() = car?.let {
            (
                it.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                ).getCurrentCarUxRestrictions().isRequiresDistractionOptimization()
        } ?: false

    private val isAutomotive get() = carContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    init {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                favoritesList = prefsRepository.getAutoFavorites()
                isLoggedIn = serverManager.isRegistered() &&
                    serverManager.authenticationRepository()
                    .getSessionState() == SessionState.CONNECTED
                invalidate()
                while (isLoggedIn != true) {
                    delay(1000)
                    isLoggedIn = serverManager.isRegistered() &&
                        serverManager.authenticationRepository()
                        .getSessionState() == SessionState.CONNECTED
                    invalidate()
                }
                allEntities.collect { entities ->
                    val newDomains = entities.values
                        .map { it.domain }
                        .distinct()
                        .filter { it in SUPPORTED_DOMAINS }
                        .toSet()
                    if (newDomains.size != domains.size || newDomains != domains) {
                        domains.clear()
                        domains.addAll(newDomains)
                        invalidate()
                    }
                }
            }
        }

        lifecycle.addObserver(object : DefaultLifecycleObserver {

            override fun onResume(owner: LifecycleOwner) {
                registerAutomotiveRestrictionListener()
            }

            override fun onPause(owner: LifecycleOwner) {
                carRestrictionManager?.unregisterListener()
                car?.disconnect()
                car = null
            }
        })
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()
        if (favoritesList.isNotEmpty()) {
            listBuilder.addItem(
                Row.Builder().apply {
                    setImage(
                        CarIcon.Builder(
                            IconicsDrawable(carContext, CommunityMaterial.Icon3.cmd_star).apply {
                                sizeDp = 48
                            }.toAndroidIconCompat()
                        )
                            .setTint(CarColor.DEFAULT)
                            .build()
                    )
                    setTitle("Favorites")
                    setOnClickListener {
                        Log.i(TAG, "Favorites clicked: $favoritesList, current server: ${serverId.value}")
                        screenManager.push(
                            EntityGridVehicleScreen(
                                carContext,
                                serverManager.integrationRepository(serverId.value),
                                "Favorites",
                                allEntities.map { it.values.filter { entity -> favoritesList.contains("${serverId.value}-${entity.entityId}") } }
                            )
                        )
                    }
                }.build()
            )
        }
        domains.forEach { domain ->
            val friendlyDomain =
                SUPPORTED_DOMAINS_WITH_STRING[domain]?.let { carContext.getString(it) }
                    ?: domain.split("_").joinToString(" ") { word ->
                        word.capitalize(Locale.getDefault())
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
                                serverManager.integrationRepository(serverId.value),
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
                            serverManager.integrationRepository(serverId.value),
                            allEntities.map { it.values.filter { entity -> entity.domain in MAP_DOMAINS } }
                        )
                    )
                }
                .build()
        )

        if (serverManager.defaultServers.size > 1) {
            listBuilder.addItem(
                Row.Builder()
                    .setImage(
                        CarIcon.Builder(
                            IconicsDrawable(
                                carContext,
                                CommunityMaterial.Icon2.cmd_home_switch
                            ).apply {
                                sizeDp = 48
                            }.toAndroidIconCompat()
                        )
                            .setTint(CarColor.DEFAULT)
                            .build()
                    )
                    .setTitle(carContext.getString(commonR.string.aa_change_server))
                    .setOnClickListener {
                        Log.i(TAG, "Change server clicked")
                        screenManager.pushForResult(
                            ChangeServerScreen(
                                carContext,
                                serverManager,
                                serverId
                            )
                        ) {
                            it?.toString()?.toIntOrNull()?.let { serverId ->
                                onChangeServer(serverId)
                            }
                        }
                    }
                    .build()
            )
        }

        return ListTemplate.Builder().apply {
            setTitle(carContext.getString(commonR.string.app_name))
            setHeaderAction(Action.APP_ICON)
            if (isAutomotive && !iDrivingOptimized && BuildConfig.FLAVOR != "full") {
                setActionStrip(
                    ActionStrip.Builder().addAction(
                        Action.Builder()
                            .setTitle(carContext.getString(commonR.string.aa_launch_native))
                            .setOnClickListener {
                                startNativeActivity()
                            }.build()
                    ).build()
                )
            }
            if (domains.isEmpty()) {
                setLoading(true)
            } else {
                setLoading(false)
                setSingleList(listBuilder.build())
            }
        }.build()
    }

    private fun registerAutomotiveRestrictionListener() {
        if (carContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Log.i(TAG, "Register for Automotive Restrictions")
            car = Car.createCar(carContext)
            carRestrictionManager =
                car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            val listener =
                CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
                    invalidate()
                }
            carRestrictionManager?.registerListener(listener)
        }
    }

    private fun startNativeActivity() {
        Log.i(TAG, "Starting login activity")
        with(carContext) {
            startActivity(
                Intent(
                    carContext,
                    LaunchActivity::class.java
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
            if (isAutomotive) {
                finishCarApp()
            }
        }
    }
}
