package io.homeassistant.companion.android.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.pm.PackageManager
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
import io.homeassistant.companion.android.util.vehicle.nativeModeActionStrip
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
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

        val MAP_DOMAINS = listOf(
            "device_tracker",
            "person",
            "sensor",
            "zone"
        )
    }

    private var favoriteEntities = flowOf<List<Entity<*>>>()
    private var entityList: List<Entity<*>> = listOf()
    private var favoritesList = emptyList<String>()
    private var isLoggedIn: Boolean? = null
    private val domains = mutableSetOf<String>()
    private var car: Car? = null
    private var carRestrictionManager: CarUxRestrictionsManager? = null
    val iDrivingOptimized
        get() = car?.let {
            (
                it.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                ).getCurrentCarUxRestrictions().isRequiresDistractionOptimization()
        } ?: false

    val isAutomotive get() = carContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

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
        lifecycleScope.launch {
            favoriteEntities = allEntities.map {
                it.values.filter { entity -> favoritesList.contains("${serverId.value}-${entity.entityId}") }
                    .sortedBy { entity -> favoritesList.indexOf("${serverId.value}-${entity.entityId}") }
            }
            favoriteEntities.collect {
                val hasChanged = entityList.size != it.size || entityList.toSet() != it.toSet()
                entityList = it
                if (hasChanged) invalidate()
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
        val listBuilder = if (favoritesList.isNotEmpty()) {
            EntityGridVehicleScreen(
                carContext,
                serverManager,
                serverId,
                prefsRepository,
                serverManager.integrationRepository(serverId.value),
                carContext.getString(commonR.string.favorites),
                domains,
                favoriteEntities,
                allEntities
            ) { onChangeServer(it) }.getEntityGridItems(entityList)
        } else {
            var builder = ItemList.Builder()
            if (domains.isNotEmpty()) {
                builder = addDomainList(domains)
            }

            builder.addItem(
                GridItem.Builder()
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
                builder.addItem(
                    GridItem.Builder()
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
            builder
        }

        return GridTemplate.Builder().apply {
            setTitle(carContext.getString(commonR.string.app_name))
            setHeaderAction(Action.APP_ICON)
            if (isAutomotive && !iDrivingOptimized && BuildConfig.FLAVOR != "full") {
                setActionStrip(nativeModeActionStrip(carContext))
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

    fun addDomainList(domains: MutableSet<String>): ItemList.Builder {
        val listBuilder = ItemList.Builder()
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
                GridItem.Builder().apply {
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
                                serverManager,
                                serverId,
                                prefsRepository,
                                serverManager.integrationRepository(serverId.value),
                                friendlyDomain,
                                domains,
                                allEntities.map { it.values.filter { entity -> entity.domain == domain } },
                                allEntities
                            ) { }
                        )
                    }
                    .build()
            )
        }
        return listBuilder
    }
}
